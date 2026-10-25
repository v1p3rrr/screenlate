package com.vpr.screenlate.core.photos.store

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.photos.InvalidPhotoException
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.TestPhotos
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val index = FakePhotoIndex()
    private val root by lazy { File(folder.root, "photos") }

    private fun store() = PhotoStore(root, index, FakeProbe, clock = { 42L }, io = Dispatchers.Unconfined)

    private suspend fun PhotoStore.capture(content: String = "4000x3000:6"): File =
        newCaptureFile().apply { writeText(content) }

    private suspend fun PhotoStore.currentNow(): PhotoDocument? = current.first()

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

    fun add(photo: PhotoEntity): Long {
        val id = nextId++
        rows.value += id to photo.copy(id = id)
        return id
    }

    fun save(id: Long) {
        rows.value += id to rows.value.getValue(id).copy(saved = true)
    }

    override fun observeCurrent(): Flow<PhotoEntity?> = combine(rows, currentId) { rows, id -> id?.let(rows::get) }

    override suspend fun current(): PhotoEntity? = currentId.value?.let(rows.value::get)

    override suspend fun get(id: Long): PhotoEntity? = rows.value[id]

    override suspend fun all(): List<PhotoEntity> = rows.value.values.sortedBy { it.id }

    override suspend fun insertCurrent(photo: PhotoEntity): Long {
        if (failInsert) throw IOException("Disk full")
        return add(photo).also { currentId.value = it }
    }

    override suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int): Boolean {
        val row = rows.value[id]?.takeIf { it.recognitionRevision == expected } ?: return false
        rows.value += id to row.copy(recognitionFile = file, recognitionRevision = revision)
        return true
    }

    override suspend fun clearCurrent() {
        currentId.value = null
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
        if (currentId.value == id) currentId.value = null
    }
}
