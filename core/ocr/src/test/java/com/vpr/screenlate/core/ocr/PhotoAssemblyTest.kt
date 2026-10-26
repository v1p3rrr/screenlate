package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PhotoAssemblyTest {
    private val left = PhotoTile(0, 0, 1500, 1500)
    private val right = PhotoTile(1300, 0, 2800, 1500)
    private fun word(text: String, x: Float, y: Float = 100f, width: Float = 80f, separator: String = " ") =
        OcrWord(text, separator, Box(x, y, x + width, y + 40))
    private fun line(vararg words: OcrWord, vertical: Boolean = false, rtl: Boolean = false) =
        OcrLine(words.toList(), Box.unionOf(words.map { it.box })!!, vertical, rtl)
    private fun paragraph(vararg lines: OcrLine) = OcrParagraph(lines.toList())
    private fun result(tile: PhotoTile, vararg paragraphs: OcrParagraph, engine: OcrEngineType = OcrEngineType.LENS) =
        PhotoTilePage(tile, OcrPage(tile.width, tile.height, paragraphs.toList(), engine)
            .offset(-tile.left.toFloat(), -tile.top.toFloat()))
    private fun assemble(vararg tiles: PhotoTilePage, language: Language = Language.ENGLISH) =
        PhotoAssembly.assemble(2800, 2800, language, tiles.toList())

    @Test
    fun overlappingHorizontalLinesKeepBothEndsAndDeduplicateOnlyTheSamePhysicalWord() {
        val shared = word("middle", 1390f)
        val page = assemble(
            result(left, paragraph(line(word("before", 1200f), shared))),
            result(right, paragraph(line(shared, word("after", 1600f)))),
        )
        assertThat(page.paragraphs).hasSize(1)
        assertThat(page.paragraphs.single().lines).hasSize(1)
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("before", "middle", "after").inOrder()
        assertThat(page.paragraphs.single().lines.single().words.last().box.left).isEqualTo(1600f)
    }

    @Test
    fun legitimateRepeatedTextAtDifferentLocationsSurvives() {
        val page = assemble(result(left, paragraph(line(word("very", 1310f), word("very", 1420f)))),
            result(right, paragraph(line(word("very", 1310f), word("very", 1420f)))))
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("very", "very")
    }

    @Test
    fun completeBoundaryWordWinsOverTheClippedSuffixAndKeepsCharacterBoxes() {
        val complete = word("boundary", 1270f, width = 160f).copy(characterBoxes =
            List(8) { Box(1270f + it * 20, 100f, 1290f + it * 20, 140f) })
        val clipped = word("undary", 1300f, width = 130f)
        val page = assemble(result(left, paragraph(line(complete))), result(right, paragraph(line(clipped))))
        val kept = page.paragraphs.single().lines.single().words.single()
        assertThat(kept.text).isEqualTo("boundary")
        assertThat(kept.characterBoxes).isEqualTo(complete.characterBoxes)
    }

    @Test
    fun aWordLongerThanTheOverlapCanBeReconstructedFromTwoClippedObservations() {
        val page = assemble(result(left, paragraph(line(word("abcde", 1250f, width = 250f)))),
            result(right, paragraph(line(word("bcdef", 1300f, width = 250f)))))
        assertThat(page.paragraphs.single().lines.single().words.single().text).isEqualTo("abcdef")
    }

    @Test
    fun fourTileCornersProduceOneWord() {
        val center = word("corner", 1390f, 1390f)
        val tiles = listOf(left, right, PhotoTile(0, 1300, 1500, 2800), PhotoTile(1300, 1300, 2800, 2800))
        val page = assemble(*tiles.map { result(it, paragraph(line(center))) }.toTypedArray())
        assertThat(page.paragraphs.flatMap { it.lines }.flatMap { it.words }).hasSize(1)
    }

    @Test
    fun paragraphsAreConnectedThroughSharedLinesWithoutJoiningNeighbours() {
        val shared = line(word("shared", 1390f, 300f))
        val page = assemble(
            result(left, paragraph(line(word("first", 1200f, 100f)), shared), paragraph(line(word("heading", 900f, 500f)))),
            result(right, paragraph(shared, line(word("last", 1600f, 400f)))),
        )
        assertThat(page.paragraphs).hasSize(2)
        assertThat(page.paragraphs.first().lines.map { it.text }).containsExactly("first", "shared", "last").inOrder()
        val layout = TextLayout(page)
        assertThat(layout.paragraphText(TextPosition(0, 0)).first).isEqualTo("first shared last ")
    }

    @Test
    fun adjacentRowsDoNotCollapseIntoOne() {
        val page = assemble(
            result(left, paragraph(line(word("row", 1390f, 100f)), line(word("row", 1390f, 143f)))),
            result(right, paragraph(line(word("row", 1390f, 100f)), line(word("row", 1390f, 143f)))),
        )
        assertThat(page.paragraphs.single().lines).hasSize(2)
    }

    @Test
    fun verticalJapaneseJoinsAlongTheColumnAndPreservesReadingDirection() {
        val top = word("上", 1390f, 1200f, 40f, "")
        val middle = word("中", 1390f, 1390f, 40f, "")
        val bottom = word("下", 1390f, 1600f, 40f, "")
        val page = assemble(result(left, paragraph(line(top, middle, vertical = true))),
            result(PhotoTile(0, 1300, 1500, 2800), paragraph(line(middle, bottom, vertical = true))), language = Language.JAPANESE)
        val output = page.paragraphs.single().lines.single()
        assertThat(output.vertical).isTrue()
        assertThat(output.text).isEqualTo("上中下")
    }

    @Test
    fun rightToLeftFragmentsKeepTheirLogicalWordOrder() {
        val shared = word("كتاب", 1400f)
        val page = assemble(result(left, paragraph(line(shared, word("هنا", 1200f), rtl = true))),
            result(right, paragraph(line(word("هذا", 1600f), shared, rtl = true))), language = Language.ARABIC)
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("هذا", "كتاب", "هنا").inOrder()
    }

    @Test
    fun combiningMarksAndWordLevelEngineSurviveMixedTileResults() {
        val combined = word("cafe" + Char(0x301), 1390f)
        val cloudWord = word("cloud", 1200f)
        val deviceWord = word("device", 1600f)
        val page = assemble(result(left, paragraph(line(cloudWord, combined))),
            result(right, paragraph(line(combined.copy(text = "café"), deviceWord)), engine = OcrEngineType.ML_KIT))
        val words = page.paragraphs.single().lines.single().words
        assertThat(words).hasSize(3)
        assertThat(words.first().engine).isEqualTo(OcrEngineType.LENS)
        assertThat(words.last().engine).isEqualTo(OcrEngineType.ML_KIT)
        assertThat(java.text.Normalizer.normalize(words[1].text, java.text.Normalizer.Form.NFC)).isEqualTo("café")
    }
}
