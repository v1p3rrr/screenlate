package com.vpr.screenlate.camera

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.photos.LookupSource
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoViewport
import java.io.File
import org.junit.Test

internal fun photo(id: Long = 1, text: PhotoText = recognizedText()) = PhotoDocument(
    id, File("photo-" + id), 4000, 3000, PhotoOrientation.NORMAL, 0, 1, text,
)

private fun recognizedText() = PhotoText.Recognized(
    PhotoRecognition(
        Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD),
        OcrPage(4000, 3000, emptyList(), OcrEngineType.LENS), recognizedAt = 0,
    ),
)

internal fun viewport() = PhotoViewport.fit(4000, 3000, Box(20f, 100f, 1020f, 850f))

class CameraLookupTest {
    @Test
    fun backgroundPageCannotTakeTheScreenSource() {
        val sources = LookupSources()
        val page = CameraLookup(sources)
        page.show(CameraView.Photo(photo(), viewport()))
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
        page.onForeground()
        assertThat((sources.source.value as LookupSource.Photo).recognition.language).isEqualTo(Language.JAPANESE)
        page.onBackground()
        page.show(CameraView.Photo(photo(2), viewport()))
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
    }

    @Test
    fun previewAndUnreadablePhotosNeverFallBackToScreen() {
        val sources = LookupSources()
        val page = CameraLookup(sources)
        page.onForeground()
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        for (text in listOf(PhotoText.None, PhotoText.Unreadable)) {
            page.show(CameraView.Photo(photo(text = text), viewport()))
            assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
        }
        page.show(CameraView.Photo(photo(), viewport()))
        page.show(CameraView.Nothing)
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
    }

    @Test
    fun viewportMovesKeepTheRecognitionGeneration() {
        val sources = LookupSources()
        val page = CameraLookup(sources)
        page.onForeground()
        page.show(CameraView.Photo(photo(), viewport()))
        val generation = sources.source.value.generation
        page.show(CameraView.Photo(photo(), viewport().copy(offsetX = 50f)))
        assertThat(sources.source.value.generation).isEqualTo(generation)
        assertThat((sources.source.value as LookupSource.Photo).viewport.offsetX).isEqualTo(50f)
    }
}
