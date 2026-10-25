package com.vpr.screenlate.overlay.source

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.core.photos.PhotoViewport
import org.junit.Test

class TextFrameTest {
    /** Two characters at x 100–140 and 140–180 of a 400 × 300 photo. */
    private val layout = TextLayout(
        OcrPage(
            400,
            300,
            listOf(
                OcrParagraph(
                    listOf(OcrLine(listOf(OcrWord("写真", "", Box(100f, 100f, 180f, 140f))), Box(100f, 100f, 180f, 140f), vertical = false)),
                ),
            ),
            OcrEngineType.LENS,
        ),
    )

    /** Twice as large, the photo's left edge 50 px off the screen, under a toolbar 160 px high. */
    private val zoomed = TextFrame.Photo(PhotoViewport(2f, -50f, 160f, 400, 300, Box(0f, 160f, 1080f, 2200f)))

    @Test
    fun theScreenFrameIsTheScreen() {
        assertThat(layout.hitTest(TextFrame.Screen, 150f, 120f, 12f)).isEqualTo(TextPosition(0, 1))
        assertThat(TextFrame.Screen.toScreen(Box(1f, 2f, 3f, 4f))).isEqualTo(Box(1f, 2f, 3f, 4f))
    }

    @Test
    fun theAimFindsTheCharacterUnderItOnTheZoomedPhoto() {
        // The second character, 140–180, is at 230–310 on the screen.
        assertThat(layout.hitTest(zoomed, 250f, 400f, 12f)).isEqualTo(TextPosition(0, 1))
        assertThat(layout.hitTest(zoomed, 170f, 400f, 12f)).isEqualTo(TextPosition(0, 0))
    }

    @Test
    fun theToleranceStaysTheSameOnTheScreen() {
        // 20 screen px right of the text: 10 photo px.
        assertThat(layout.hitTest(zoomed, 330f, 400f, 24f)).isEqualTo(TextPosition(0, 1))
        assertThat(layout.hitTest(zoomed, 330f, 400f, 16f)).isNull()
    }

    @Test
    fun nothingIsAimedAtOutsideThePhoto() {
        // The toolbar above the photo, and the letterbox below it.
        assertThat(zoomed.toLayout(250f, 100f)).isNull()
        assertThat(zoomed.toLayout(250f, 800f)).isNull()
        val fitted = TextFrame.Photo(PhotoViewport.fit(400, 300, Box(0f, 0f, 1080f, 2400f)))
        assertThat(fitted.toLayout(540f, 100f)).isNull()
        assertThat(fitted.toLayout(540f, 1200f)).isNotNull()
    }

    @Test
    fun highlightsAreCutToThePartOfThePhotoThatShows() {
        assertThat(zoomed.toScreen(Box(100f, 100f, 180f, 140f))).isEqualTo(Box(150f, 360f, 310f, 440f))
        // The photo's left 25 px are off the screen.
        assertThat(zoomed.toScreen(Box(0f, 100f, 40f, 140f))).isEqualTo(Box(0f, 360f, 30f, 440f))
        assertThat(zoomed.toScreen(Box(0f, 100f, 20f, 140f))).isNull()
    }
}
