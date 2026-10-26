package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import kotlin.random.Random
import org.junit.Test

class BoxIndexTest {
    @Test
    fun denseIndexedHitTestingMatchesTheOriginalLinearSelection() {
        val random = Random(915)
        val words = List(2000) {
            val x = random.nextFloat() * 4000
            val y = random.nextFloat() * 6000
            OcrWord("a", " ", Box(x, y, x + random.nextFloat() * 60, y + random.nextFloat() * 60))
        }
        val layout = TextLayout(OcrPage(4000, 6000, listOf(OcrParagraph(listOf(
            OcrLine(words, Box(0f, 0f, 4000f, 6000f), false)))), OcrEngineType.LENS))
        val characters = layout.paragraphs.flatten()
        repeat(1000) {
            val x = random.nextFloat() * 4200 - 100
            val y = random.nextFloat() * 6200 - 100
            val tolerance = random.nextFloat() * 35
            var distance = Float.MAX_VALUE
            var expected: TextPosition? = null
            characters.forEach { character ->
                val box = character.box
                val candidate = if (box.contains(x, y)) -1f / (1 +
                    (box.centerX - x) * (box.centerX - x) + (box.centerY - y) * (box.centerY - y)) else box.distanceTo(x, y)
                if (candidate < distance) {
                    distance = candidate
                    expected = TextPosition(character.paragraphIndex, character.offset)
                }
            }
            if (distance > tolerance) expected = null
            assertThat(layout.hitTest(x, y, tolerance)).isEqualTo(expected)
        }
    }

    @Test
    fun touchingZeroAreaBoxesKeepInputOrder() {
        val index = BoxIndex(listOf(Box(3f, 2f, 3f, 5f), Box(3f, 3f, 4f, 5f), Box(10f, 10f, 12f, 12f)))
        assertThat(index.query(Box(3f, 3f, 3f, 3f))).containsExactly(0, 1).inOrder()
    }
}
