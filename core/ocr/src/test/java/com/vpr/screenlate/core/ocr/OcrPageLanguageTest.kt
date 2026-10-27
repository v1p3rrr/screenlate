package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class OcrPageLanguageTest {
    private fun page(vararg words: String) = lines(words.toList())

    private fun lines(vararg lines: List<String>) = OcrPage(
        100,
        20 * lines.size,
        listOf(
            OcrParagraph(
                lines.mapIndexed { index, words ->
                    val top = 20f * index
                    OcrLine(words.map { OcrWord(it, " ", Box(0f, top, 10f, top + 10f)) }, Box(0f, top, 100f, top + 20f), vertical = false)
                },
            ),
        ),
        OcrEngineType.LENS,
    )

    @Test
    fun `Russian words lose their Latin look-alikes`() {
        // "мoре" with a Latin o; "TOP" has no Cyrillic letter and stays.
        val fixed = page("\u043co\u0440\u0435", "TOP").inLanguage(Language.RUSSIAN)

        assertThat(fixed.text).isEqualTo("\u043c\u043e\u0440\u0435 TOP")
    }

    @Test
    fun `Serbian fixes look-alikes on its Cyrillic lines and keeps its Latin ones`() {
        // "Ja сам o кући" with a Latin J, a and o; "Ja i ti" in Latin letters; a line of "O" alone follows the page,
        // which is mostly Cyrillic.
        val fixed = lines(
            listOf("Ja", "\u0441\u0430\u043c", "o", "\u043a\u0443\u045b\u0438"),
            listOf("Ja", "i", "ti"),
            listOf("O"),
        ).inLanguage(Language.SERBIAN)

        assertThat(fixed.paragraphs.single().lines.map { line -> line.words.joinToString(" ") { it.text } }).containsExactly(
            "\u0408\u0430 \u0441\u0430\u043c \u043e \u043a\u0443\u045b\u0438",
            "Ja i ti",
            "\u041e",
        ).inOrder()
    }

    @Test
    fun `guillemets read as doubled angle brackets take one character box of the two`() {
        val boxes = List(7) { Box(10f * it, 0f, 10f * it + 10f, 10f) }
        val word = OcrWord(">>Pridi", " ", Box(0f, 0f, 70f, 10f), boxes)
        val page = OcrPage(100, 20, listOf(OcrParagraph(listOf(OcrLine(listOf(word), word.box, vertical = false)))), OcrEngineType.LENS)

        val fixed = page.inLanguage(Language.SLOVENIAN).paragraphs.single().lines.single().words.single()

        assertThat(fixed.text).isEqualTo("»Pridi")
        assertThat(fixed.characterBoxes).containsExactly(Box(0f, 0f, 20f, 10f), *boxes.drop(2).toTypedArray()).inOrder()
    }

    @Test
    fun `a page with nothing to change is the same page`() {
        val page = page("\u043co\u0440\u0435", "TOP")

        assertThat(page.inLanguage(Language.ENGLISH)).isSameInstanceAs(page)
    }
}
