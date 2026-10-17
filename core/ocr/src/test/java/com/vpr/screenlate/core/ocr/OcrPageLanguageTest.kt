package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class OcrPageLanguageTest {
    private fun page(vararg words: String) = OcrPage(
        100,
        20,
        listOf(OcrParagraph(listOf(OcrLine(words.map { OcrWord(it, " ", Box(0f, 0f, 10f, 10f)) }, Box(0f, 0f, 100f, 20f), vertical = false)))),
        OcrEngineType.LENS,
    )

    @Test
    fun `Russian words lose their Latin look-alikes`() {
        // "мoре" with a Latin o; "TOP" has no Cyrillic letter and stays.
        val fixed = page("\u043co\u0440\u0435", "TOP").inLanguage(Language.RUSSIAN)

        assertThat(fixed.text).isEqualTo("\u043c\u043e\u0440\u0435 TOP")
    }

    @Test
    fun `a page with nothing to change is the same page`() {
        val page = page("\u043co\u0440\u0435", "TOP")

        assertThat(page.inLanguage(Language.ENGLISH)).isSameInstanceAs(page)
    }
}
