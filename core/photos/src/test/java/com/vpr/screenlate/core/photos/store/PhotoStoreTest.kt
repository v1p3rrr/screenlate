package com.vpr.screenlate.core.photos.store

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.photos.HistoryLimits
import com.vpr.screenlate.core.photos.InvalidPhotoException
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.TestPhotos
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoTileResult
import com.vpr.screenlate.core.photos.ThumbnailMaker
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.OcrEngines
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val index = FakePhotoIndex()
    private val root by lazy { File(folder.root, "photos") }

    private var now = 42L
    private var thumbnails = ThumbnailMaker { "thumb".toByteArray() }

    private fun store() = PhotoStore(root, index, FakeProbe, { thumbnails.thumbnail(it) }, clock = { now++ }, io = Dispatchers.Unconfined)

    private suspend fun PhotoStore.capture(content: String = "4000x3000:6"): File =
        newCaptureFile().apply { writeText(content) }

    private suspend fun PhotoStore.currentNow(): PhotoDocument? = current.first()

    @Test
    fun pinnedPixelsSurviveReplacementAndClearUntilTheOwnerClosesThem() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        val pinned = store.pinImage(first.id)!!
        val second = store.replaceCurrent(store.capture("1200x900:1"))
        store.clearCurrent()
        assertThat(index.get(first.id)).isNull()
        assertThat(second.image.parentFile!!.exists()).isFalse()
        val output = java.io.ByteArrayOutputStream()
        pinned.copyTo(output)
        assertThat(output.toString("UTF-8")).isEqualTo("4000x3000:6")
        assertThat(pinned.width to pinned.height).isEqualTo(3000 to 4000)
        pinned.close()
        assertThat(first.image.parentFile!!.exists()).isFalse()
        pinned.close()
        assertThat(store.pinImage(first.id)).isNull()
    }

    @Test
    fun aDeletedPhotoWaitsForItsLastPinAndAKeptOneStays() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        val one = store.pinImage(first.id)!!
        val two = store.pinImage(first.id)!!
        one.close()
        assertThat(first.image.isFile).isTrue()
        store.clearCurrent()
        one.close()
        assertThat(first.image.isFile).isTrue()
        two.close()
        assertThat(first.image.parentFile!!.exists()).isFalse()
        val kept = store.replaceCurrent(store.capture())
        store.pinImage(kept.id)!!.close()
        assertThat(kept.image.isFile).isTrue()
    }

    @Test
    fun aPinWhoseCallerIsCancelledMeanwhileKeepsNoDeletedPhoto() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        val caller = launch { store.pinImage(first.id) }
        index.onGet = { caller.cancel() }
        caller.join()
        index.onGet = {}
        store.clearCurrent()
        assertThat(first.image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun aClosedPhotoThatCannotBeDeletedGoesAtTheNextStart() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        index.failDelete = true
        store.clearCurrent()
        assertThat(store.currentNow()).isNull()
        index.failDelete = false
        store().newCaptureFile()
        assertThat(photo.image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun aRecognitionWhoseCommitFailsLeavesNoFile() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        index.failRecognition = true
        assertThat(runCatching { store.commitRecognition(photo.id, 0, TestPhotos.recognition()) }.isFailure).isTrue()
        assertThat(photo.image.parentFile!!.list()!!.toList()).containsExactly("image")
    }

    @Test
    fun writesWhoseCallerIsCancelledMeanwhileStillReplaceTheOldFiles() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val first = store.commitRecognition(photo.id, 0, TestPhotos.recognition())!!
        store.saveAttempt(photo.id, attempt(first), replace = true)
        val directory = photo.image.parentFile!!
        lateinit var caller: Job
        index.onWrite = { caller.cancel() }
        caller = launch { store.saveAttempt(photo.id, attempt(first, "two"), replace = true) }
        caller.join()
        assertThat(directory.list()!!.count { it.startsWith("attempt-") }).isEqualTo(1)
        assertThat(store.currentNow()!!.attempt?.id).isEqualTo("two")
        caller = launch { store.commitRecognition(photo.id, first.revision, TestPhotos.recognition()) }
        caller.join()
        assertThat(directory.list()!!.toList()).containsExactly("image", "recognition-2")
        assertThat(store.currentNow()!!.text).isEqualTo(PhotoText.Recognized(TestPhotos.recognition()))
    }

    @Test
    fun restartDeletesWhatAbandonedPinsHeldWithoutChangingTheCurrentPhoto() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        store.pinImage(first.id)
        val second = store.replaceCurrent(store.capture("1200x900:1"))
        store.pinImage(second.id)
        assertThat(first.image.isFile).isTrue()
        val reopened = store()
        reopened.newCaptureFile()
        assertThat(first.image.parentFile!!.exists()).isFalse()
        assertThat(reopened.currentNow()!!.id).isEqualTo(second.id)
        assertThat(second.image.isFile).isTrue()
    }

    private fun attempt(document: PhotoDocument, id: String = "one") = PhotoAttempt(id, document.revision,
        com.vpr.screenlate.core.common.Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD),
        listOf(PhotoTile(0, 0, 1500, 1500)), startedAt = 45)

    @Test
    fun progressSurvivesReopeningAndTidyingWithoutPublishingPartialText() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val text = TestPhotos.recognition()
        val recognized = store.commitRecognition(photo.id, photo.revision, text)!!
        val empty = attempt(recognized)
        assertThat(store.saveAttempt(photo.id, empty, replace = true)).isTrue()
        val progress = empty.copy(results = listOf(PhotoTileResult(empty.tiles.single(), page = text.page)))
        assertThat(store.saveAttempt(photo.id, progress)).isTrue()
        val reopened = store()
        reopened.newCaptureFile() // Tidying must preserve the file named by Room.
        val restored = reopened.currentNow()!!
        assertThat(restored.text).isEqualTo(PhotoText.Recognized(text))
        assertThat(restored.attempt).isEqualTo(progress)
        assertThat(restored.attemptUnreadable).isFalse()
    }

    @Test
    fun supersededAttemptAndRevisionCannotWriteLateProgress() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val old = attempt(photo)
        val replacement = attempt(photo, "two")
        store.saveAttempt(photo.id, old, true)
        store.saveAttempt(photo.id, replacement, true)
        assertThat(store.saveAttempt(photo.id, old)).isFalse()
        val committed = store.commitRecognition(photo.id, photo.revision, TestPhotos.recognition())!!
        assertThat(committed.attempt).isNull()
        assertThat(store.saveAttempt(photo.id, replacement)).isFalse()
        assertThat(store.currentNow()!!.attempt).isNull()
        assertThat(photo.image.parentFile.listFiles()!!.map { it.name }).containsExactly("image", "recognition-1")
    }

    @Test
    fun damagedProgressKeepsThePhotoAndItsCommittedText() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val recognized = store.commitRecognition(photo.id, 0, TestPhotos.recognition())!!
        store.saveAttempt(photo.id, attempt(recognized), true)
        val row = index.current()!!
        File(photo.image.parentFile, row.attemptFile!!).writeText("damaged")
        val restored = store().currentNow()!!
        assertThat(restored.attemptUnreadable).isTrue()
        assertThat(restored.text).isEqualTo(recognized.text)
        assertThat(restored.image.isFile).isTrue()
        assertThat(store.saveAttempt(photo.id, attempt(recognized, "new"), true)).isTrue()
        assertThat(store.currentNow()!!.attemptUnreadable).isFalse()
    }

    @Test
    fun `a capture becomes the current photo and replaces the previous one`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        assertThat(first.width).isEqualTo(3000)
        assertThat(first.height).isEqualTo(4000)
        assertThat(first.orientation).isEqualTo(PhotoOrientation.ROTATE_90)
        assertThat(first.text).isEqualTo(PhotoText.None)
        assertThat(first.createdAt).isEqualTo(42L)
        assertThat(store.currentNow()).isEqualTo(first)

        val second = store.replaceCurrent(store.capture("1000x800:1"))
        assertThat(store.currentNow()).isEqualTo(second)
        assertThat(first.image.exists()).isFalse()
        assertThat(index.all().map { it.id }).containsExactly(second.id)
        assertThat(photoDirectories()).hasSize(1)
    }

    @Test
    fun `a capture that is no image leaves the current photo`() = runTest {
        val store = store()
        val current = store.replaceCurrent(store.capture())
        val broken = store.capture("broken")
        assertThat(failure { store.replaceCurrent(broken) }).isInstanceOf(InvalidPhotoException::class.java)
        assertThat(broken.exists()).isFalse()
        assertThat(store.currentNow()).isEqualTo(current)
        assertThat(current.image.exists()).isTrue()
    }

    @Test
    fun `a cancelled capture is deleted and the current photo stays`() = runTest {
        val store = store()
        val current = store.replaceCurrent(store.capture())
        val cancelled = store.capture()
        store.discard(cancelled)
        assertThat(cancelled.exists()).isFalse()
        assertThat(store.currentNow()).isEqualTo(current)
    }

    @Test
    fun `a failed database write keeps the previous photo and no files of the new one`() = runTest {
        val store = store()
        val current = store.replaceCurrent(store.capture())
        index.failInsert = true
        val capture = store.capture()
        assertThat(failure { store.replaceCurrent(capture) }).isInstanceOf(IOException::class.java)
        assertThat(store.currentNow()).isEqualTo(current)
        assertThat(photoDirectories()).containsExactly(current.image.parentFile!!.name)
    }

    @Test
    fun `only files written by the camera can be committed`() = runTest {
        val store = store()
        val elsewhere = folder.newFile("photo.jpg").apply { writeText("4000x3000:1") }
        assertThat(failure { store.replaceCurrent(elsewhere) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(elsewhere.exists()).isTrue()
    }

    @Test
    fun `a committed recognition is the photo's text, also after a restart`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val recognition = TestPhotos.recognition()
        val recognized = store.commitRecognition(photo.id, basedOn = 0, recognition)!!
        assertThat(recognized.revision).isEqualTo(1)
        assertThat(recognized.text).isEqualTo(PhotoText.Recognized(recognition))
        assertThat(store.currentNow()).isEqualTo(recognized)

        val restarted = store()
        assertThat(restarted.currentNow()).isEqualTo(recognized)
    }

    @Test
    fun `a newer recognition replaces the older file`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, 0, TestPhotos.recognition())
        val retried = TestPhotos.recognition().copy(recognizedAt = 2L)
        val second = store.commitRecognition(photo.id, 1, retried)!!
        assertThat(second.revision).isEqualTo(2)
        assertThat(photo.image.parentFile!!.list()!!.toList()).containsExactly("image", "recognition-2")
        assertThat((store.currentNow()!!.text as PhotoText.Recognized).recognition).isEqualTo(retried)
    }

    @Test
    fun `a recognition started before another was committed is not stored`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        val committed = store.commitRecognition(photo.id, 0, TestPhotos.recognition())
        assertThat(store.commitRecognition(photo.id, 0, TestPhotos.recognition().copy(recognizedAt = 3L))).isNull()
        assertThat(store.currentNow()).isEqualTo(committed)
        assertThat(photo.image.parentFile!!.list()!!.toList()).containsExactly("image", "recognition-1")
    }

    @Test
    fun `a recognition of a replaced photo is not stored`() = runTest {
        val store = store()
        val old = store.replaceCurrent(store.capture())
        val new = store.replaceCurrent(store.capture())
        assertThat(store.commitRecognition(old.id, 0, TestPhotos.recognition())).isNull()
        assertThat(store.currentNow()).isEqualTo(new)
    }

    @Test
    fun `damaged stored text keeps the photo`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, 0, TestPhotos.recognition())
        File(photo.image.parentFile, "recognition-1").writeText("garbage")
        val reopened = store().currentNow()!!
        assertThat(reopened.text).isEqualTo(PhotoText.Unreadable)
        assertThat(reopened.image.exists()).isTrue()
        // It can be recognized again.
        val again = store().commitRecognition(photo.id, 1, TestPhotos.recognition())
        assertThat(again!!.text).isInstanceOf(PhotoText.Recognized::class.java)
    }

    @Test
    fun `missing stored text keeps the photo`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, 0, TestPhotos.recognition())
        File(photo.image.parentFile, "recognition-1").delete()
        assertThat(store().currentNow()!!.text).isEqualTo(PhotoText.Unreadable)
    }

    @Test
    fun `an interrupted change leaves the committed photo, and its leftovers go at the next change`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, 0, TestPhotos.recognition())
        val committed = store.currentNow()
        // A capture never committed, a photo moved into place without its row, a recognition never named, and a
        // previous photo whose deletion was cut short.
        val capture = store.capture()
        val orphan = File(root, "orphan").apply { mkdirs(); File(this, "image").writeText("4000x3000:1") }
        val unnamed = File(photo.image.parentFile, "recognition-2.tmp").apply { writeText("partial") }
        val previousDirectory = File(root, "previous").apply { mkdirs(); File(this, "image").writeText("1x1:1") }
        index.add(PhotoEntity(directory = "previous", rawWidth = 1, rawHeight = 1, orientation = 1, imageBytes = 3, createdAt = 1))

        val restarted = store()
        assertThat(restarted.currentNow()).isEqualTo(committed)
        restarted.newCaptureFile()
        assertThat(capture.exists()).isFalse()
        assertThat(orphan.exists()).isFalse()
        assertThat(unnamed.exists()).isFalse()
        assertThat(previousDirectory.exists()).isFalse()
        assertThat(index.all().map { it.id }).containsExactly(photo.id)
        assertThat(restarted.currentNow()).isEqualTo(committed)
    }

    @Test
    fun `photos kept by the history survive replacing and clearing`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        index.save(first.id)
        store.replaceCurrent(store.capture())
        assertThat(first.image.exists()).isTrue()
        store.clearCurrent()
        assertThat(store.currentNow()).isNull()
        assertThat(index.all().map { it.id }).containsExactly(first.id)
        store().newCaptureFile()
        assertThat(first.image.exists()).isTrue()
    }

    @Test
    fun `clearing deletes the current photo`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.clearCurrent()
        assertThat(store.currentNow()).isNull()
        assertThat(photo.image.parentFile!!.exists()).isFalse()
        assertThat(index.all()).isEmpty()
    }

    @Test
    fun `a photo whose image is gone is no current photo`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        photo.image.delete()
        val restarted = store()
        assertThat(restarted.currentNow()).isNull()
        restarted.newCaptureFile()
        assertThat(index.all()).isEmpty()
        assertThat(index.current()).isNull()
    }

    @Test
    fun `a saved photo stays after replacement, with its thumbnail, language and size`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, photo.revision, TestPhotos.recognition(Language.KOREAN))
        assertThat(store.save(photo.id, HistoryLimits())).isTrue()
        assertThat(store.currentNow()!!.saved).isTrue()
        store.replaceCurrent(store.capture())
        val entry = store.entries.first().single()
        assertThat(entry.id).isEqualTo(photo.id)
        assertThat(entry.language).isEqualTo(Language.KOREAN)
        assertThat(entry.thumbnail!!.readText()).isEqualTo("thumb")
        assertThat(entry.bytes).isEqualTo(photo.image.parentFile!!.listFiles()!!.sumOf { it.length() })
        assertThat(photo.image.exists()).isTrue()
    }

    @Test
    fun `the history lists the latest saved first and a second save keeps the first place`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        store.save(first.id, HistoryLimits())
        val second = store.replaceCurrent(store.capture())
        store.save(second.id, HistoryLimits())
        store.save(first.id, HistoryLimits())
        assertThat(store.entries.first().map { it.id }).containsExactly(second.id, first.id).inOrder()
    }

    @Test
    fun `a photo is saved without a thumbnail when none can be made`() = runTest {
        thumbnails = ThumbnailMaker { throw IOException("Unreadable") }
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        assertThat(store.save(photo.id, HistoryLimits())).isTrue()
        assertThat(store.entries.first().single().thumbnail).isNull()
    }

    @Test
    fun `saving deletes the oldest photos beyond the count limit`() = runTest {
        val store = store()
        val ids = (1..3).map { store.replaceCurrent(store.capture()).also { store.save(it.id, HistoryLimits(count = 2)) } }
        assertThat(store.entries.first().map { it.id }).containsExactly(ids[2].id, ids[1].id).inOrder()
        assertThat(ids[0].image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun `a limit deletes the current photo too when it is among the oldest`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        store.save(first.id, HistoryLimits())
        val second = store.replaceCurrent(store.capture())
        store.save(second.id, HistoryLimits())
        store.open(first.id)
        store.trim(HistoryLimits(count = 1))
        assertThat(store.entries.first().map { it.id }).containsExactly(second.id)
        assertThat(store.currentNow()).isNull()
        assertThat(first.image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun `excess counts what lower limits would delete and deletes nothing`() = runTest {
        val store = store()
        repeat(3) { store.save(store.replaceCurrent(store.capture()).id, HistoryLimits()) }
        assertThat(store.excess(HistoryLimits(count = 1))).isEqualTo(2)
        assertThat(store.excess(HistoryLimits())).isEqualTo(0)
        assertThat(store.entries.first()).hasSize(3)
    }

    @Test
    fun `opening a history photo makes it current and deletes an unsaved current photo`() = runTest {
        val store = store()
        val saved = store.replaceCurrent(store.capture())
        store.save(saved.id, HistoryLimits())
        val unsaved = store.replaceCurrent(store.capture("1200x900:1"))
        val opened = store.open(saved.id)!!
        assertThat(opened.id).isEqualTo(saved.id)
        assertThat(opened.saved).isTrue()
        assertThat(store.currentNow()!!.id).isEqualTo(saved.id)
        assertThat(unsaved.image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun `a photo outside the history does not open`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        assertThat(store.open(photo.id)).isNull()
        assertThat(store.open(photo.id + 1)).isNull()
        assertThat(store.currentNow()!!.id).isEqualTo(photo.id)
    }

    @Test
    fun `removing a history photo that is current closes and deletes it`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        store.save(first.id, HistoryLimits())
        val second = store.replaceCurrent(store.capture())
        store.save(second.id, HistoryLimits())
        store.remove(setOf(first.id))
        assertThat(first.image.parentFile!!.exists()).isFalse()
        assertThat(store.currentNow()!!.id).isEqualTo(second.id)
        store.remove(setOf(second.id))
        assertThat(store.currentNow()).isNull()
        assertThat(photoDirectories()).isEmpty()
    }

    @Test
    fun `clearing deletes the current photo in the history and keeps an unsaved one`() = runTest {
        val store = store()
        val saved = store.replaceCurrent(store.capture())
        store.save(saved.id, HistoryLimits())
        store.clear()
        assertThat(store.entries.first()).isEmpty()
        assertThat(store.currentNow()).isNull()
        assertThat(saved.image.parentFile!!.exists()).isFalse()
        val older = store.replaceCurrent(store.capture())
        store.save(older.id, HistoryLimits())
        val unsaved = store.replaceCurrent(store.capture())
        store.clear()
        assertThat(older.image.parentFile!!.exists()).isFalse()
        assertThat(store.currentNow()!!.id).isEqualTo(unsaved.id)
        assertThat(unsaved.image.exists()).isTrue()
    }

    @Test
    fun `a restart keeps the thumbnail of a saved photo`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.save(photo.id, HistoryLimits())
        store().newCaptureFile()
        assertThat(File(photo.image.parentFile, "thumbnail").isFile).isTrue()
    }

    @Test
    fun `a recognition committed before the language column names it when saved`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        store.commitRecognition(photo.id, photo.revision, TestPhotos.recognition(Language.KOREAN))
        index.forgetLanguage(photo.id)
        store.save(photo.id, HistoryLimits())
        assertThat(store.entries.first().single().language).isEqualTo(Language.KOREAN)
    }

    @Test
    fun `a clock set back keeps a newly saved photo first and within the limits`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        store.save(first.id, HistoryLimits())
        now = 0
        val second = store.replaceCurrent(store.capture())
        assertThat(store.save(second.id, HistoryLimits(count = 1))).isTrue()
        assertThat(store.entries.first().map { it.id }).containsExactly(second.id)
        assertThat(store.currentNow()!!.id).isEqualTo(second.id)
    }

    @Test
    fun `a replaced photo that cannot be deleted leaves the new one current and goes at the next start`() = runTest {
        val store = store()
        val first = store.replaceCurrent(store.capture())
        index.failDelete = true
        val second = store.replaceCurrent(store.capture("1200x900:1"))
        assertThat(store.currentNow()!!.id).isEqualTo(second.id)
        index.failDelete = false
        store().newCaptureFile()
        assertThat(index.all().map { it.id }).containsExactly(second.id)
        assertThat(first.image.parentFile!!.exists()).isFalse()
    }

    @Test
    fun `a thumbnail that cannot be stored leaves no temporary file`() = runTest {
        val store = store()
        val photo = store.replaceCurrent(store.capture())
        File(photo.image.parentFile, "thumbnail/taken").apply { parentFile!!.mkdirs() }.writeText("in the way")
        assertThat(store.save(photo.id, HistoryLimits())).isTrue()
        assertThat(photo.image.parentFile!!.list()!!.filter { it.endsWith(".tmp") }).isEmpty()
    }

    private fun photoDirectories(): List<String> = root.list()!!.filter { it != ".staging" }

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    /** Reads "<width>x<height>:<exif>" written by the tests; anything else is not an image. */
    private object FakeProbe : ImageProbe {
        override fun probe(file: File): ImageInfo? {
            val match = Regex("""(\d+)x(\d+):(\d)""").matchEntire(file.readText()) ?: return null
            val (width, height, exif) = match.destructured
            return ImageInfo(width.toInt(), height.toInt(), PhotoOrientation.fromExif(exif.toInt()))
        }
    }
}

internal class FakePhotoIndex : PhotoIndex {
    private val rows = MutableStateFlow<Map<Long, PhotoEntity>>(emptyMap())
    private val currentId = MutableStateFlow<Long?>(null)
    private var nextId = 1L
    var failInsert = false
    var failDelete = false
    var failRecognition = false

    /** Runs in [setRecognition] and [setAttempt] after the row changed. */
    var onWrite: () -> Unit = {}

    /** Runs in every [get], inside the store's lock. */
    var onGet: () -> Unit = {}

    fun add(photo: PhotoEntity): Long {
        val id = nextId++
        rows.value += id to photo.copy(id = id)
        return id
    }

    fun save(id: Long) {
        rows.value += id to rows.value.getValue(id).copy(saved = true)
    }

    fun forgetLanguage(id: Long) {
        rows.value += id to rows.value.getValue(id).copy(language = null)
    }

    override fun observeCurrent(): Flow<PhotoEntity?> = combine(rows, currentId) { rows, id -> id?.let(rows::get) }

    override suspend fun current(): PhotoEntity? = currentId.value?.let(rows.value::get)

    override suspend fun get(id: Long): PhotoEntity? {
        onGet()
        return rows.value[id]
    }

    override suspend fun all(): List<PhotoEntity> = rows.value.values.sortedBy { it.id }

    override fun observeHistory(): Flow<List<PhotoEntity>> = rows.map { it.historyOf() }

    override suspend fun history(): List<PhotoEntity> = rows.value.historyOf()

    private fun Map<Long, PhotoEntity>.historyOf() =
        values.filter { it.saved }.sortedWith(compareByDescending<PhotoEntity> { it.savedAt }.thenByDescending { it.id })

    override suspend fun insertCurrent(photo: PhotoEntity): Long {
        if (failInsert) throw IOException("Disk full")
        return add(photo).also { currentId.value = it }
    }

    override suspend fun setCurrent(id: Long) {
        require(id in rows.value)
        currentId.value = id
    }

    override suspend fun save(id: Long, at: Long, language: String?): Boolean {
        val row = rows.value[id]?.takeIf { !it.saved } ?: return false
        rows.value += id to row.copy(saved = true, savedAt = at, language = row.language ?: language)
        return true
    }

    override suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int, language: String): Boolean {
        if (failRecognition) throw IOException("Disk error")
        val row = rows.value[id]?.takeIf { it.recognitionRevision == expected } ?: return false
        rows.value += id to row.copy(recognitionFile = file, recognitionRevision = revision, attemptFile = null, language = language)
        onWrite()
        // A suspending Room call that changed the row still throws when its caller was cancelled meanwhile.
        currentCoroutineContext().ensureActive()
        return true
    }

    override suspend fun setAttempt(id: Long, expected: Int, file: String): Boolean {
        val row = rows.value[id]?.takeIf { it.recognitionRevision == expected && currentId.value == id } ?: return false
        rows.value += id to row.copy(attemptFile = file)
        onWrite()
        // A suspending Room call that changed the row still throws when its caller was cancelled meanwhile.
        currentCoroutineContext().ensureActive()
        return true
    }

    override suspend fun clearCurrent() {
        currentId.value = null
    }

    override suspend fun delete(id: Long) {
        if (failDelete) throw IOException("Disk error")
        rows.value -= id
        if (currentId.value == id) currentId.value = null
    }
}
