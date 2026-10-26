package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
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
import kotlinx.coroutines.flow.MutableStateFlow
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
        jobs.recognize(documents.photo, Language.ENGLISH, OcrEngines.CLOUD)
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.None)
        assertThat(documents.photo.attempt!!.results).hasSize(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(documents.commits).isEqualTo(1)
        assertThat(documents.photo.attempt).isNull()
        val recognition = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(recognition.tiles).hasSize(2)
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
        jobs.recognize(documents.photo, Language.ENGLISH, OcrEngines.CLOUD)
        runCurrent()
        assertThat(engine.active).isEqualTo(2)
        assertThat(engine.calls).hasSize(2)
        gate.complete(Unit)
        runCurrent()
        assertThat(engine.maximum).isEqualTo(2)
        assertThat(engine.calls).hasSize(PhotoTiles.grid(4000, 3000).size)
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
        jobs.recognize(documents.photo, Language.ARABIC, OcrEngines.BOTH)
        runCurrent()
        val partial = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(partial.failed).hasSize(1)
        assertThat(partial.tiles).hasSize(1)
        failing = false
        jobs.retry(documents.photo)
        runCurrent()
        val retried = (documents.photo.text as PhotoText.Recognized).recognition
        assertThat(engine.calls.map { it.left }).containsExactly(0, 1300, 1300).inOrder()
        assertThat(retried.failed).isEmpty()
        assertThat(retried.tiles).hasSize(2)
        assertThat(retried.language).isEqualTo(Language.ARABIC)
        assertThat(engine.fixed.last()).isEqualTo(partial.settings)
    }

    @Test
    fun allFailedTilesPreserveThePreviousRecognitionAndStoreRetryMetadata() = runTest {
        val old = TestPhotos.recognition(language = Language.ENGLISH)
        val documents = Documents(photo().copy(revision = 4, text = PhotoText.Recognized(old)))
        val engine = Engine().apply { before = { throw IOException("test") } }
        val jobs = PhotoRecognitionJobs(documents, engine, backgroundScope, { 0 }, { "one" })
        jobs.recognize(documents.photo, Language.JAPANESE, OcrEngines.CLOUD)
        runCurrent()
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.text).isEqualTo(PhotoText.Recognized(old))
        assertThat(documents.photo.attempt!!.finished).isTrue()
        assertThat(documents.photo.attempt!!.remaining).hasSize(2)
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
        jobs.recognize(documents.photo, Language.ENGLISH, OcrEngines.CLOUD)
        runCurrent()
        documents.current.value = photo().copy(id = 2)
        runCurrent()
        assertThat(engine.closed).isEqualTo(0)
        gate.complete(Unit)
        runCurrent()
        assertThat(engine.closed).isEqualTo(1)
        assertThat(documents.commits).isEqualTo(0)
        assertThat(documents.photo.id).isEqualTo(2)
        assertThat(documents.photo.text).isEqualTo(PhotoText.None)
    }

    private inner class Engine : PhotoRecognitionEngine {
        val calls = mutableListOf<PhotoTile>()
        val fixed = mutableListOf<PhotoOcrSettings?>()
        var before: suspend (PhotoTile) -> Unit = {}
        var active = 0
        var maximum = 0
        var closed = 0
        override suspend fun open(document: PhotoDocument, language: Language, engines: OcrEngines, fixed: PhotoOcrSettings?): PhotoOcrSession {
            this.fixed += fixed
            return object : PhotoOcrSession {
                override val settings = fixed ?: PhotoOcrSettings(engines, if (engines == OcrEngines.CLOUD) null else "mlkit")
                override suspend fun recognize(tile: PhotoTile): OcrPage {
                    calls += tile
                    active++
                    maximum = maxOf(maximum, active)
                    try { before(tile); return page(tile) } finally { active-- }
                }
                override fun close() { check(active == 0); closed++ }
            }
        }
    }

    private class Documents(initial: PhotoDocument) : PhotoDocuments {
        override val current = MutableStateFlow<PhotoDocument?>(initial)
        val photo get() = current.value!!
        var commits = 0
        override suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean): Boolean {
            if (photo.id != documentId || photo.revision != attempt.basedOn || (!replace && photo.attempt?.id != attempt.id)) return false
            current.value = photo.copy(attempt = attempt)
            return true
        }
        override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? {
            if (photo.id != documentId || photo.revision != basedOn) return null
            commits++
            return photo.copy(revision = basedOn + 1, text = PhotoText.Recognized(recognition), attempt = null).also { current.value = it }
        }
        override suspend fun pinImage(documentId: Long): com.vpr.screenlate.core.photos.PhotoImage? = null
        override suspend fun newCaptureFile(): File = error("Not used")
        override suspend fun replaceCurrent(capture: File): PhotoDocument = error("Not used")
        override suspend fun discard(capture: File): Unit = error("Not used")
        override suspend fun clearCurrent(): Unit = error("Not used")
    }
}
