package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.LensPausedException
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OfflineException
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTiles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoRecognitionJobsTest {
    private fun photo(width: Int = 2800, height: Int = 1500) =
        PhotoDocument(1, File("unused"), width, height, PhotoOrientation.NORMAL, 0, 0, PhotoText.None)
    private fun page(tile: PhotoTile) = OcrPage(tile.width, tile.height, listOf(OcrParagraph(listOf(
        OcrLine(listOf(OcrWord("word", " ", Box(100f, 100f, 200f, 140f))), Box(100f, 100f, 200f, 140f), false)))), OcrEngineType.LENS)

    @Test
    fun resultIsPublishedOnlyAfterTheLastTileTerminates() = runTest {
        val documents = Documents(photo())
        val engine = Engine()
        val gate = CompletableDeferred<Unit>()
        engine.before = { if (it.left > 0) gate.await() }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.None)
        // The overview and the left tile.
        assertThat(documents.photo.attempt!!.results).hasSize(2)
        gate.complete(Unit)
        runCurrent()
        assertThat(documents.commits).isEqualTo(1)
        assertThat(documents.photo.attempt).isNull()
        val recognition = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(recognition.tiles).hasSize(3)
        assertThat(recognition.page.width).isEqualTo(2800)
        assertThat(engine.closed).isEqualTo(1)
    }

    @Test
    fun atMostTwoTileReadersRunAtOnce() = runTest {
        val documents = Documents(photo(4000, 3000))
        val engine = Engine()
        val gate = CompletableDeferred<Unit>()
        engine.before = { gate.await() }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(engine.active).isEqualTo(2)
        assertThat(engine.calls).hasSize(2)
        gate.complete(Unit)
        runCurrent()
        assertThat(engine.maximum).isEqualTo(2)
        assertThat(engine.calls).hasSize(PhotoTiles.grid(4000, 3000).size + 1)
        assertThat(documents.commits).isEqualTo(1)
    }

    @Test
    fun partialResultRetainsSuccessfulWorkAndRetryOnlyRunsFailedTiles() = runTest {
        val documents = Documents(photo())
        val engine = Engine()
        var failing = true
        engine.before = { if (failing && it.left > 0) throw IOException("test") }
        var id = 0
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "attempt" + id++ })
        jobs.recognize(documents.photo, Language.ARABIC, PhotoOcrSettings(OcrEngines.BOTH, "mlkit"))
        runCurrent()
        val partial = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(partial.failed).hasSize(1)
        assertThat(partial.tiles).hasSize(2)
        failing = false
        jobs.retry(documents.photo)
        runCurrent()
        val retried = (documents.photo.text as PhotoText.Recognized).recognition
        // The overview, the left and the right tile, then the right one again.
        assertThat(engine.calls.map { it.left }).containsExactly(0, 0, 1300, 1300).inOrder()
        assertThat(retried.failed).isEmpty()
        assertThat(retried.tiles).hasSize(3)
        assertThat(retried.language).isEqualTo(Language.ARABIC)
        assertThat(engine.fixed.last()).isEqualTo(partial.settings)
    }

    @Test
    fun allFailedTilesPreserveThePreviousRecognitionAndStoreRetryMetadata() = runTest {
        val old = TestPhotos.recognition(language = Language.ENGLISH)
        val documents = Documents(photo().copy(revision = 4, text = PhotoText.Recognized(old)))
        val engine = Engine().apply { before = { throw IOException("test") } }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.Recognized(old))
        assertThat(documents.photo.attempt!!.finished).isTrue()
        assertThat(documents.photo.attempt!!.remaining).hasSize(3)
        assertThat(documents.photo.attempt!!.language).isEqualTo(Language.JAPANESE)
    }

    @Test
    fun restorationNeverRestartsWorkUntilRetryIsExplicit() = runTest {
        val original = photo()
        val grid = PhotoTiles.grid(original.width, original.height)
        val interrupted = PhotoAttempt("interrupted", 0, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD), grid,
            listOf(PhotoTileResult(grid.first(), page = page(grid.first()))), 1)
        val documents = Documents(original.copy(attempt = interrupted))
        val engine = Engine()
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "retry" })
        runCurrent()
        assertThat(engine.calls).isEmpty()
        assertThat(jobs.work.value).isNull()
        jobs.retry(documents.photo)
        runCurrent()
        assertThat(engine.calls).containsExactly(grid.last())
        assertThat(documents.commits).isEqualTo(1)
    }

    @Test
    fun replacementRejectsLateResultsAndRetainsImageOwnershipUntilReadersStop() = runTest {
        val documents = Documents(photo())
        val gate = CompletableDeferred<Unit>()
        val engine = Engine().apply { before = { withContext(NonCancellable) { gate.await() } } }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        documents.state.value = photo().copy(id = 2)
        runCurrent()
        assertThat(engine.closed).isEqualTo(0)
        gate.complete(Unit)
        runCurrent()
        assertThat(engine.closed).isEqualTo(1)
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.id).isEqualTo(2)
        assertThat(documents.photo.text).isEqualTo(PhotoText.None)
    }

    @Test
    fun theRequestedSettingsReachTheEngineAndTheText() = runTest {
        val documents = Documents(photo(1500, 1500))
        val engine = Engine()
        val settings = PhotoOcrSettings(OcrEngines.BOTH, "model")
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, settings)
        runCurrent()
        assertThat(engine.fixed).containsExactly(settings)
        assertThat((documents.photo.text as PhotoText.Recognized).recognition.settings).isEqualTo(settings)
    }

    @Test
    fun aNewRecognitionReplacesTextCommittedAfterItWasRequested() = runTest {
        val documents = Documents(photo())
        val requested = documents.photo
        documents.state.value = requested.copy(revision = 1, text = PhotoText.Recognized(TestPhotos.recognition()))
        val jobs = PhotoRecognitionJobs(documents, Engine(), backgroundScope, { 0 }, { "one" })
        jobs.recognize(requested, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(documents.commits).isEqualTo(1)
        assertThat(documents.photo.revision).isEqualTo(2)
        assertThat((documents.photo.text as PhotoText.Recognized).recognition.language).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun aFailedObservationIsRestartedSoAReplacedPhotoStillStopsItsWork() = runTest {
        val documents = Documents(photo(), failFirstObservation = true)
        val engine = Engine().apply { before = { CompletableDeferred<Unit>().await() } }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        advanceTimeBy(2_000)
        runCurrent()
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(jobs.work.value).isInstanceOf(PhotoWork.Running::class.java)
        documents.state.value = photo().copy(id = 2)
        runCurrent()
        assertThat(jobs.work.value).isNull()
        assertThat(engine.closed).isEqualTo(1)
    }

    @Test
    fun theOverviewRunsFirstOutsideTheProgressAndItsFailureIsNoMissingRegion() = runTest {
        val documents = Documents(photo())
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine()
        val gate = CompletableDeferred<Unit>()
        engine.before = {
            if (it == overview) throw IOException("test")
            if (it.left > 0) gate.await()
        }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(engine.calls.first()).isEqualTo(overview)
        assertThat(jobs.work.value).isEqualTo(PhotoWork.Running(1, "one", 1, 2))
        gate.complete(Unit)
        runCurrent()
        val recognition = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(recognition.failed).isEmpty()
        assertThat(recognition.tiles.map { it.tile }).containsExactlyElementsIn(PhotoTiles.grid(2800, 1500))
    }

    @Test
    fun anOverviewWithoutAnyRegionKeepsThePreviousRecognition() = runTest {
        val old = TestPhotos.recognition(language = Language.ENGLISH)
        val documents = Documents(photo().copy(revision = 4, text = PhotoText.Recognized(old)))
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine().apply {
            before = { if (it != overview) throw IOException("test") }
            pageOf = ::largeText
        }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.Recognized(old))
        assertThat(documents.photo.attempt!!.remaining).containsExactlyElementsIn(PhotoTiles.grid(2800, 1500))
    }

    @Test
    fun anOverviewWithoutAnyRegionGivesItsLargeTextToAPhotoWithoutText() = runTest {
        val documents = Documents(photo())
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine().apply {
            before = { if (it != overview) throw IOException("test") }
            pageOf = ::largeText
        }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        val recognition = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(recognition.page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.text }).containsExactly("BIG")
        assertThat(recognition.failed.map { it.box }).containsExactlyElementsIn(PhotoTiles.grid(2800, 1500).map { it.box })
    }

    @Test
    fun anOverviewWithoutAnyRegionKeepsTextCommittedAfterTheCallersSnapshot() = runTest {
        // The caller's photo has no text yet; the store already holds the text an earlier attempt committed.
        val old = TestPhotos.recognition(language = Language.ENGLISH)
        val documents = Documents(photo().copy(revision = 4, text = PhotoText.Recognized(old)))
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine().apply {
            before = { if (it != overview) throw IOException("test") }
            pageOf = ::largeText
        }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(photo(), Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.Recognized(old))
    }

    @Test
    fun anOverviewWithoutAnyRegionOrLargeTextCommitsNothing() = runTest {
        val documents = Documents(photo())
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine().apply { before = { if (it != overview) throw IOException("test") } }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        // Small text only: the photo is not shown as one without text while its regions failed.
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.None)
        assertThat(documents.photo.attempt!!.remaining).containsExactlyElementsIn(PhotoTiles.grid(2800, 1500))
    }

    /** Small text the tiles read better, and a word taller than the overlap across the seam. */
    private fun largeText(tile: PhotoTile) = OcrPage(tile.width, tile.height, listOf(OcrParagraph(listOf(
        OcrLine(listOf(OcrWord("tiny", " ", Box(100f, 100f, 200f, 140f))), Box(100f, 100f, 200f, 140f), false),
        OcrLine(listOf(OcrWord("BIG", "", Box(1200f, 80f, 1600f, 400f))), Box(1200f, 80f, 1600f, 400f), false),
    ))), OcrEngineType.LENS)

    @Test
    fun theOverviewAddsOnlyLargeTextInPlaceOfTheTilesWordsUnderIt() = runTest {
        val documents = Documents(photo())
        val overview = PhotoTiles.overview(2800, 1500)!!
        val engine = Engine()
        engine.pageOf = { tile ->
            if (tile != overview) page(tile) else OcrPage(tile.width, tile.height, listOf(OcrParagraph(listOf(
                // Small text the tiles read better, and a word taller than the overlap across the seam.
                OcrLine(listOf(OcrWord("tiny", " ", Box(100f, 100f, 200f, 140f))), Box(100f, 100f, 200f, 140f), false),
                OcrLine(listOf(OcrWord("BIG", "", Box(1200f, 80f, 1600f, 400f))), Box(1200f, 80f, 1600f, 400f), false),
            ))), OcrEngineType.LENS)
        }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD))
        runCurrent()
        val page = (documents.photo.text as PhotoText.Recognized).recognition.page
        // The right tile's word lies inside the large word.
        assertThat(page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.text }).containsExactly("word", "BIG")
    }

    @Test
    fun aFailedTileKeepsTheCauseItsWarningNames() = runTest {
        val cases = listOf(
            Triple(OfflineException(), OcrEngines.CLOUD, FailureCause.OFFLINE),
            Triple(LensPausedException(), OcrEngines.CLOUD, FailureCause.REFUSED),
            Triple(LensHttpException(429), OcrEngines.BOTH, FailureCause.REFUSED),
            Triple(LensHttpException(500), OcrEngines.CLOUD, FailureCause.SERVER),
            Triple(IOException("reset"), OcrEngines.CLOUD, FailureCause.NETWORK),
            Triple(NoDeviceOcrException(), OcrEngines.BOTH, FailureCause.DEVICE),
            Triple(IOException("model"), OcrEngines.DEVICE, FailureCause.DEVICE),
            Triple(PhotoReadException(), OcrEngines.DEVICE, FailureCause.OTHER),
            Triple(IllegalStateException(), OcrEngines.CLOUD, FailureCause.OTHER),
        )
        for ((error, engines, cause) in cases) {
            val documents = Documents(photo(1500, 1500))
            val engine = Engine().apply { before = { throw error } }
            val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
            jobs.recognize(documents.photo, Language.ENGLISH, PhotoOcrSettings(engines))
            runCurrent()
            assertThat(documents.photo.attempt!!.results.single().failure).isEqualTo(cause)
        }
    }

    private inner class Engine : PhotoRecognitionEngine {
        val calls = mutableListOf<PhotoTile>()
        val fixed = mutableListOf<PhotoOcrSettings>()
        var before: suspend (PhotoTile) -> Unit = {}
        var pageOf: (PhotoTile) -> OcrPage = { page(it) }
        var active = 0
        var maximum = 0
        var closed = 0
        override suspend fun open(document: PhotoDocument, language: Language, settings: PhotoOcrSettings): PhotoOcrSession {
            fixed += settings
            return object : PhotoOcrSession {
                override val settings = settings
                override suspend fun recognize(tile: PhotoTile): OcrPage {
                    calls += tile
                    active++
                    maximum = maxOf(maximum, active)
                    try { before(tile); return pageOf(tile) } finally { active-- }
                }
                override fun close() { check(active == 0); closed++ }
            }
        }
    }

    /** [failFirstObservation]: the first collection of [current] fails, as a database error would. */
    private class Documents(initial: PhotoDocument, failFirstObservation: Boolean = false) : PhotoDocuments {
        val state = MutableStateFlow<PhotoDocument?>(initial)
        private var failing = failFirstObservation
        override val current: Flow<PhotoDocument?> = flow {
            if (failing) {
                failing = false
                throw IOException("test")
            }
            emitAll(state)
        }
        val photo get() = state.value!!
        var commits = 0
        override suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean): Boolean {
            if (photo.id != documentId || photo.revision != attempt.basedOn || (!replace && photo.attempt?.id != attempt.id)) return false
            state.value = photo.copy(attempt = attempt)
            return true
        }
        override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? {
            if (photo.id != documentId || photo.revision != basedOn) return null
            commits++
            return photo.copy(revision = basedOn + 1, text = PhotoText.Recognized(recognition), attempt = null).also { state.value = it }
        }
        override suspend fun pinImage(documentId: Long): com.vpr.screenlate.core.photos.PhotoImage? = null
        override suspend fun newCaptureFile(): File = error("Not used")
        override suspend fun replaceCurrent(capture: File): PhotoDocument = error("Not used")
        override suspend fun discard(capture: File): Unit = error("Not used")
        override suspend fun clearCurrent(): Unit = error("Not used")
    }
}
