package com.vpr.screenlate.overlay.source

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoViewport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScreenReadsTest {
    private val sources = LookupSources()
    private val reader = FakeReader()
    private val reads = ScreenReads({ sources.source.value }, reader)

    @Test
    fun aShownPhotoReadsNothingFromTheScreen() = runTest {
        val camera = sources.present()
        camera.showPhoto(1, 1, recognition(), viewport())
        assertNothingIsRead()
    }

    @Test
    fun theCameraPageWithoutTextReadsNothingFromTheScreen() = runTest {
        sources.present()
        assertNothingIsRead()
    }

    @Test
    fun theScreenIsReadUnderItsGeneration() = runTest {
        val generation = reads.open()!!
        reads.prepareCloud()
        assertThat(reads.capture(generation)).isEqualTo("shot 1")
        assertThat(reads.readAppText(generation)).isSameInstanceAs(reader.page)
        assertThat(reader.calls).containsExactly("cloud", "capture", "app text").inOrder()
    }

    @Test
    fun aScanFromBeforeThePhotoReadsNothingOnceTheScreenIsBack() = runTest {
        val stale = reads.open()!!
        val camera = sources.present()
        camera.showPhoto(1, 1, recognition(), viewport())
        camera.close()
        assertThat(reads.open()).isNotEqualTo(stale)
        assertThat(failure { reads.capture(stale) }).isInstanceOf(SourceChangedException::class.java)
        assertThat(reads.readAppText(stale)).isNull()
        assertThat(reader.calls).isEmpty()
    }

    @Test
    fun aScreenshotFinishedAfterThePhotoAppearedIsDiscarded() = runTest {
        val generation = reads.open()!!
        reader.whileCapturing = { sources.present().showPhoto(1, 1, recognition(), viewport()) }
        assertThat(failure { reads.capture(generation) }).isInstanceOf(SourceChangedException::class.java)
        assertThat(reader.discarded).containsExactly("shot 1")
    }

    @Test
    fun appTextReadWhileTheCameraPageOpenedIsDropped() = runTest {
        val generation = reads.open()!!
        reader.whileReading = { sources.present() }
        assertThat(reads.readAppText(generation)).isNull()
        assertThat(reader.calls).containsExactly("app text")
    }

    private suspend fun assertNothingIsRead() {
        val generation = sources.source.value.generation
        assertThat(reads.open()).isNull()
        assertThat(reads.isOpen(generation)).isFalse()
        reads.prepareCloud()
        assertThat(failure { reads.capture(generation) }).isInstanceOf(SourceChangedException::class.java)
        assertThat(reads.readAppText(generation)).isNull()
        assertThat(reader.calls).isEmpty()
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    private class FakeReader : ScreenReader<String> {
        val calls = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        val page = OcrPage(100, 100, emptyList(), OcrEngineType.ACCESSIBILITY)
        var whileCapturing: () -> Unit = {}
        var whileReading: () -> Unit = {}
        private var shots = 0

        override fun prepareCloud() {
            calls += "cloud"
        }

        override suspend fun capture(): String {
            calls += "capture"
            whileCapturing()
            return "shot ${++shots}"
        }

        override fun discard(shot: String) {
            discarded += shot
        }

        override suspend fun readAppText(): OcrPage {
            calls += "app text"
            whileReading()
            return page
        }
    }
}

internal fun recognition(language: Language = Language.CHINESE, engines: OcrEngines = OcrEngines.CLOUD) = PhotoRecognition(
    language = language,
    settings = PhotoOcrSettings(engines),
    page = OcrPage(400, 300, emptyList(), OcrEngineType.LENS),
    recognizedAt = 1L,
)

internal fun viewport(scale: Float = 1f, offsetX: Float = 0f) =
    PhotoViewport(scale, offsetX, 0f, 400, 300, Box(0f, 0f, 1080f, 2400f))
