package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class PhotoAssemblyTest {
    private val left = PhotoTile(0, 0, 1500, 1500)
    private val right = PhotoTile(1300, 0, 2800, 1500)
    private val lower = PhotoTile(0, 1300, 1500, 2800)
    private fun word(text: String, x: Float, y: Float = 100f, width: Float = 80f, separator: String = " ") =
        OcrWord(text, separator, Box(x, y, x + width, y + 40))
    /** A word of a vertical line, 20 px wide. */
    private fun column(text: String, x: Float, top: Float, bottom: Float) = OcrWord(text, "", Box(x, top, x + 20, bottom))
    private fun line(vararg words: OcrWord, vertical: Boolean = false, rtl: Boolean = false) =
        OcrLine(words.toList(), Box.unionOf(words.map { it.box })!!, vertical, rtl)
    private fun paragraph(vararg lines: OcrLine) = OcrParagraph(lines.toList())
    private fun result(tile: PhotoTile, vararg paragraphs: OcrParagraph, engine: OcrEngineType = OcrEngineType.LENS) =
        PhotoTilePage(tile, OcrPage(tile.width, tile.height, paragraphs.toList(), engine)
            .offset(-tile.left.toFloat(), -tile.top.toFloat()))
    private fun assemble(vararg tiles: PhotoTilePage, language: Language = Language.ENGLISH, overview: PhotoTilePage? = null) =
        PhotoAssembly.assemble(2800, 2800, language, tiles.toList(), overview)
    private fun overview(vararg paragraphs: OcrParagraph) = result(PhotoTile(0, 0, 2800, 2800), *paragraphs)
    /** A word taller than the tiles' overlap. */
    private fun large(text: String, x: Float, top: Float, width: Float = 300f) = OcrWord(text, " ", Box(x, top, x + width, top + 300))

    @Test
    fun largeTextAcrossASeamComesFromTheOverviewInPlaceOfTheTilesPieces() {
        val page = assemble(
            // Both tiles read the parts of the headline they hold, cut by their edges and misread.
            result(left, paragraph(line(word("small", 100f, 1000f))), paragraph(line(large("HEAD", 1100f, 1250f, 400f).copy(
                text = "HFA", box = Box(1100f, 1250f, 1500f, 1500f))))),
            result(lower, paragraph(line(large("LINE", 1100f, 1300f, 400f).copy(text = "IIN", box = Box(1100f, 1300f, 1500f, 1550f)))),
                paragraph(line(word("below", 100f, 2000f)))),
            overview = overview(
                paragraph(line(word("smal", 100f, 1000f))),
                paragraph(line(large("HEADLINE", 1100f, 1250f, 400f))),
                paragraph(line(word("belo", 100f, 2000f))),
            ),
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("small", "HEADLINE", "below").inOrder()
    }

    @Test
    fun largeTextThatATileHoldsWholeComesFromTheTile() {
        val page = assemble(
            result(left, paragraph(line(large("TITLE", 100f, 100f)))),
            result(right, paragraph(line(word("other", 1600f)))),
            overview = overview(paragraph(line(large("TITIE", 100f, 100f)))),
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("TITLE", "other")
    }

    @Test
    fun largeTextInAFailedTileComesFromTheOverview() {
        val page = assemble(
            result(left, paragraph(line(word("kept", 100f)))),
            overview = overview(paragraph(line(large("SIGN", 2000f, 2000f))), paragraph(line(word("lost", 2000f, 2500f)))),
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("kept", "SIGN")
    }

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

    @Test
    fun aReadingInsideTheOverlapKeepsTheLongerSegmentationOnce() {
        val page = assemble(
            result(left, paragraph(line(column("ひら", 100f, 1350f, 1390f), column("が", 100f, 1390f, 1410f), vertical = true))),
            result(lower, paragraph(line(column("ひらがな", 100f, 1350f, 1430f), vertical = true))),
            language = Language.JAPANESE,
        )
        assertThat(page.paragraphs.single().lines.single().text).isEqualTo("ひらがな")
    }

    @Test
    fun aCutWordCoveredByCompleteWordsOfAnotherTileIsDroppedWhateverItReads() {
        val page = assemble(
            result(left, paragraph(line(column("あい", 100f, 1250f, 1290f), column("うえ", 100f, 1290f, 1330f),
                column("お", 100f, 1330f, 1350f), vertical = true))),
            // The lower tile cuts the line at its top edge and misreads the rest; nothing else read the second word.
            result(lower, paragraph(line(column("ちお", 100f, 1297f, 1350f), vertical = true)),
                paragraph(line(column("ぬ", 300f, 1298f, 1318f), vertical = true))),
            language = Language.JAPANESE,
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("あいうえお", "ぬ")
    }

    @Test
    fun aGlyphAnotherTileReadAsALineOfItsOwnIsKeptOnceInTheLongerLine() {
        val page = assemble(
            result(left, paragraph(line(column("かき", 100f, 1380f, 1420f), column("く", 100f, 1420f, 1440f), vertical = true))),
            result(lower, paragraph(line(column("く", 100f, 1420f, 1440f)))),
            language = Language.JAPANESE,
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("かきく")
    }

    @Test
    fun aLongWordJoinsWhenTheEnginesDropTheGlyphsCutByTheEdges() {
        // Glyphs 45 px apart from x = 1020: the left tile reads up to j, the right one from h.
        fun joined(leftBox: Box, rightBox: Box) = assemble(
            result(left, paragraph(line(OcrWord("abcdefghij", " ", leftBox)))),
            result(right, paragraph(line(OcrWord("hijklmno", " ", rightBox)))),
        ).paragraphs.single().lines.single().words.map { it.text }
        // Boxes that reach the tile edges over the cut glyphs, then boxes that end at the last whole glyph.
        assertThat(joined(Box(1020f, 100f, 1500f, 140f), Box(1300f, 100f, 1695f, 140f))).containsExactly("abcdefghijklmno")
        assertThat(joined(Box(1020f, 100f, 1470f, 140f), Box(1335f, 100f, 1695f, 140f))).containsExactly("abcdefghijklmno")
    }

    @Test
    fun aLongWordJoinsWhenTheTilesReadItInDifferentNormalizationForms() {
        val page = assemble(
            result(left, paragraph(line(OcrWord("abcdefgh" + Char(0xE9) + "j", " ", Box(1020f, 100f, 1500f, 140f))))),
            result(right, paragraph(line(OcrWord("he" + Char(0x301) + "jklmno", " ", Box(1300f, 100f, 1695f, 140f))))),
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text })
            .containsExactly("abcdefgh" + Char(0xE9) + "jklmno")
    }

    @Test
    fun aLatinWordInARightToLeftLineJoinsAcrossTheSeamInItsOwnDirection() {
        // The word of the test above inside an Arabic line.
        val page = assemble(
            result(left, paragraph(line(OcrWord("abcdefghij", " ", Box(1020f, 100f, 1500f, 140f)), word("هنا", 900f), rtl = true))),
            result(right, paragraph(line(word("هذا", 1750f), OcrWord("hijklmno", " ", Box(1300f, 100f, 1695f, 140f)), rtl = true))),
            language = Language.ARABIC,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text })
            .containsExactly("هذا", "abcdefghijklmno", "هنا").inOrder()
    }

    @Test
    fun aRightToLeftWordInALeftToRightLineJoinsAcrossTheSeamInItsOwnDirection() {
        // Glyphs 45 px apart, the first one at the right end (x = 1695): the right tile reads eight, the left one ten.
        val hebrew = "אבגדהוזחטיכלמנס"
        val page = assemble(
            result(left, paragraph(line(word("left", 900f), OcrWord(hebrew.substring(5), " ", Box(1020f, 100f, 1470f, 140f))))),
            result(right, paragraph(line(OcrWord(hebrew.take(8), " ", Box(1335f, 100f, 1695f, 140f)), word("right", 1750f)))),
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("left", hebrew, "right").inOrder()
    }

    @Test
    fun anArabicWordInARightToLeftLineJoinsAcrossTheSeam() {
        // The geometry of the test above inside an Arabic line.
        val arabic = "ابتثجحخدذرزسشصض"
        val page = assemble(
            result(left, paragraph(line(OcrWord(arabic.substring(5), " ", Box(1020f, 100f, 1470f, 140f)), word("هنا", 900f), rtl = true))),
            result(right, paragraph(line(word("هذا", 1750f), OcrWord(arabic.take(8), " ", Box(1335f, 100f, 1695f, 140f)), rtl = true))),
            language = Language.ARABIC,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("هذا", arabic, "هنا").inOrder()
    }

    @Test
    fun aNumberInARightToLeftLineJoinsAcrossTheSeamFromItsLeftEnd() {
        val page = assemble(
            result(left, paragraph(line(OcrWord("1234567890", " ", Box(1020f, 100f, 1500f, 140f)), word("هنا", 900f), rtl = true))),
            result(right, paragraph(line(word("هذا", 1750f), OcrWord("89012345", " ", Box(1300f, 100f, 1695f, 140f)), rtl = true))),
            language = Language.ARABIC,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text })
            .containsExactly("هذا", "123456789012345", "هنا").inOrder()
    }

    @Test
    fun aVerticalWordJoinsAcrossTheSeamDownItsColumn() {
        // Glyphs 45 px apart from y = 1020: the upper tile reads ten, the lower one from the eighth.
        val kana = "あいうえおかきくけこさしすせそ"
        val page = assemble(
            result(left, paragraph(line(column(kana.take(10), 700f, 1020f, 1470f), vertical = true))),
            result(lower, paragraph(line(column(kana.substring(7), 700f, 1335f, 1695f), vertical = true))),
            language = Language.JAPANESE,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly(kana)
    }

    @Test
    fun aSlantedLineJoinsAcrossTheSeamThroughItsSharedWord() {
        // Two lines rising 2.5° to the right: the line boxes of the parts the tiles read are offset across the line.
        fun row(prefix: String, base: Float) = (0..19).map { k ->
            val x = 200f + 130f * k
            word("$prefix$k", x, base + (x + 50f) * 0.0437f - 20f, width = 100f)
        }
        val upper = row("a", 300f)
        val lower = row("b", 400f)
        fun part(words: List<OcrWord>, tile: PhotoTile) =
            line(*words.filter { it.box.left >= tile.left && it.box.right <= tile.right }.toTypedArray())
        val page = assemble(
            result(left, paragraph(part(upper, left), part(lower, left))),
            result(right, paragraph(part(upper, right), part(lower, right))),
        )
        assertThat(page.paragraphs).hasSize(1)
        assertThat(page.paragraphs.single().lines.map { it.text })
            .containsExactly(upper.joinToString(" ") { it.text }, lower.joinToString(" ") { it.text }).inOrder()
    }

    @Test
    fun aMisreadWordWhoseEngineDroppedTheGlyphCutByTheEdgeGivesWayToTheCompleteReading() {
        // Glyphs 30 px apart from x = 1420: the left tile misread two of them and dropped the third, cut at 1500.
        val page = assemble(
            result(left, paragraph(line(word("xb", 1420f, width = 60f)))),
            result(right, paragraph(line(word("abcd", 1420f, width = 120f)))),
        )
        assertThat(page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.text }).containsExactly("abcd")
    }

    @Test
    fun aCutWordCoveredByLessThanHalfStays() {
        // The right tile read only a word over a third of the left tile's cut word.
        val page = assemble(
            result(left, paragraph(line(word("xyz", 1440f, width = 60f)))),
            result(right, paragraph(line(word("pq", 1480f)))),
        )
        assertThat(page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.text }).containsExactly("xyz", "pq").inOrder()
    }

    @Test
    fun aThaiWordJoinsWhenTheEnginesDropTheCellsCutByTheEdges() {
        // Cells 45 px apart from x = 1020, each a consonant with a vowel mark: the left tile reads ten, the right one
        // from the eighth; the glyph is a cell, not a code point.
        val thai = "กขคงจฉชซฌญฎฏฐฑฒ".map { "$it" + Char(0x0E34) }.joinToString("")
        val page = assemble(
            result(left, paragraph(line(OcrWord(thai.take(20), "", Box(1020f, 100f, 1470f, 140f))))),
            result(right, paragraph(line(OcrWord(thai.substring(14), "", Box(1335f, 100f, 1695f, 140f))))),
            language = Language.THAI,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly(thai)
    }

    @Test
    fun aMisreadThaiWordWhoseEngineDroppedTheCellCutByTheEdgeGivesWayToTheCompleteReading() {
        // Cells 30 px apart from x = 1420: the left tile misread two of them and dropped the third, cut at 1500.
        val mark = Char(0x0E34)
        val complete = "ก${mark}ข${mark}ค${mark}ง$mark"
        val page = assemble(
            result(left, paragraph(line(word("จ${mark}ฉ$mark", 1420f, width = 60f, separator = "")))),
            result(right, paragraph(line(word(complete, 1420f, width = 120f, separator = "")))),
            language = Language.THAI,
        )
        assertThat(page.paragraphs.flatMap { it.lines }.flatMap { it.words }.map { it.text }).containsExactly(complete)
    }

    @Test
    fun aRightToLeftLineJoinsItsPartThatHasNoRightToLeftLetters() {
        // An Arabic line ending with a long Latin word: the left tile reads only the word, as a left-to-right line.
        val page = assemble(
            result(left, paragraph(line(OcrWord("abcdefghij", " ", Box(1020f, 100f, 1500f, 140f))))),
            result(right, paragraph(line(word("هذا", 1750f), OcrWord("hijklmno", " ", Box(1300f, 100f, 1695f, 140f)), rtl = true))),
            language = Language.ARABIC,
        )
        val output = page.paragraphs.single().lines.single()
        assertThat(output.rightToLeft).isTrue()
        assertThat(output.words.map { it.text }).containsExactly("هذا", "abcdefghijklmno").inOrder()
    }

    @Test
    fun aLeftToRightLineJoinsItsPartThatHasOnlyRightToLeftLetters() {
        // A line ending with a long Hebrew word: the right tile reads only the word, as a right-to-left line.
        val hebrew = "אבגדהוזחטיכלמנס"
        val page = assemble(
            result(left, paragraph(line(word("left", 900f), OcrWord(hebrew.substring(5), " ", Box(1020f, 100f, 1470f, 140f))))),
            result(right, paragraph(line(OcrWord(hebrew.take(8), " ", Box(1335f, 100f, 1695f, 140f)), rtl = true))),
        )
        val output = page.paragraphs.single().lines.single()
        assertThat(output.rightToLeft).isFalse()
        assertThat(output.words.map { it.text }).containsExactly("left", hebrew).inOrder()
    }

    @Test
    fun partsOfOneDirectionKeepItWhateverTheLanguage() {
        // A Hebrew line in a photo set to English.
        val shared = word("ספר", 1400f)
        val page = assemble(result(left, paragraph(line(shared, word("כאן", 1200f), rtl = true))),
            result(right, paragraph(line(word("זה", 1600f), shared, rtl = true))))
        val output = page.paragraphs.single().lines.single()
        assertThat(output.rightToLeft).isTrue()
        assertThat(output.words.map { it.text }).containsExactly("זה", "ספר", "כאן").inOrder()
    }

    @Test
    fun aRubyLineOverItsBaseInAnotherTileStaysALineOfItsOwn() {
        // The left tile read the ruby as a line; the right one read the base with a box that takes the ruby in.
        val page = assemble(
            result(left, paragraph(line(word("かんじ", 1390f, 80f, 80f, "").copy(box = Box(1390f, 80f, 1470f, 95f))))),
            result(right, paragraph(line(OcrWord("漢字", "", Box(1390f, 80f, 1470f, 140f))))),
            language = Language.JAPANESE,
        )
        assertThat(page.paragraphs.flatMap { it.lines }.map { it.text }).containsExactly("かんじ", "漢字")
    }

    @Test
    fun aWordWithoutLettersOrDigitsJoinsAcrossTheSeamInItsLinesDirection() {
        // Signs only, in an Arabic line: the word starts at its right end, as the line does.
        val signs = "!#%&()*+,-./:;?"
        val page = assemble(
            result(left, paragraph(line(OcrWord(signs.substring(5), " ", Box(1020f, 100f, 1470f, 140f)), word("هنا", 900f), rtl = true))),
            result(right, paragraph(line(word("هذا", 1750f), OcrWord(signs.take(8), " ", Box(1335f, 100f, 1695f, 140f)), rtl = true))),
            language = Language.ARABIC,
        )
        assertThat(page.paragraphs.single().lines.single().words.map { it.text }).containsExactly("هذا", signs, "هنا").inOrder()
    }
}
