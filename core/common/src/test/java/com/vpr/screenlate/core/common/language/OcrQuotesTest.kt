package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class OcrQuotesTest {

    private fun read(language: Language, vararg lines: String): List<String> =
        language.support.ocrQuotes(lines.map { it.split(' ') }).map { it.joinToString(" ") }

    @Test
    fun `doubled angle brackets at a word's edge are the language's guillemets`() {
        // Cloud recognition read the Slovenian and Danish » « as >> <<.
        assertThat(read(Language.SLOVENIAN, ">>Pridi sem!<< je zaklicala.")).containsExactly("»Pridi sem!« je zaklicala.")
        assertThat(read(Language.DANISH, ">>Kom her!<< råbte hun.")).containsExactly("»Kom her!« råbte hun.")
        assertThat(read(Language.NORWEGIAN, "<<Kom hit!>> ropte hun.")).containsExactly("«Kom hit!» ropte hun.")
        assertThat(read(Language.RUSSIAN, "<<Иди сюда!>> крикнула она.")).containsExactly("«Иди сюда!» крикнула она.")
        // On their own and across a line break too.
        assertThat(read(Language.SLOVENIAN, ">> Pridi", "sem! <<")).containsExactly("» Pridi", "sem! «").inOrder()
    }

    @Test
    fun `angle brackets inside a word, single ones and those of a language without guillemets stay`() {
        assertThat(read(Language.SLOVENIAN, "a>>b x<y >z")).containsExactly("a>>b x<y >z")
        assertThat(read(Language.ENGLISH, ">>Come here!<< she cried.")).containsExactly(">>Come here!<< she cried.")
        assertThat(read(Language.SWEDISH, ">>Kom hit!<<")).containsExactly(">>Kom hit!<<")
    }

    @Test
    fun `a straight quote right after an exclamation or question mark closes the quote open before it`() {
        // Cloud recognition read the Romanian closing ” as ".
        assertThat(read(Language.ROMANIAN, "„Vino aici!\" a strigat ea.")).containsExactly("„Vino aici!” a strigat ea.")
        assertThat(read(Language.GERMAN, "„Wer da?\" fragte er.")).containsExactly("„Wer da?“ fragte er.")
        assertThat(read(Language.ENGLISH, "“Stop!\" she said.")).containsExactly("“Stop!” she said.")
        // The quote opened on the line before.
        assertThat(read(Language.ROMANIAN, "„Vino", "aici!\" a strigat.")).containsExactly("„Vino", "aici!” a strigat.").inOrder()
        // Punctuation as words of its own, as cloud recognition gives it.
        assertThat(Language.ROMANIAN.support.ocrQuotes(listOf(listOf("„", "Vino", "aici", "!", "\"", "a", "strigat", "ea", "."))))
            .containsExactly(listOf("„", "Vino", "aici", "!", "”", "a", "strigat", "ea", "."))
        assertThat(read(Language.ROMANIAN, "„Vino aici! \" a strigat.")).containsExactly("„Vino aici! ” a strigat.")
        // The latest quote open: the inner one.
        assertThat(read(Language.GERMAN, "„Er rief ‚Halt!\" und lief.")).containsExactly("„Er rief ‚Halt!‘ und lief.")
    }

    @Test
    fun `a straight quote right after a full stop closes the quote open before it`() {
        // eo-anki.png: cloud recognition wrote the “ that closes „Jes. as ".
        assertThat(read(Language.ESPERANTO, "Li diris „Jes.\" kaj foriris.")).containsExactly("Li diris „Jes.“ kaj foriris.")
        assertThat(read(Language.ROMANIAN, "„Vino aici.\" a strigat.")).containsExactly("„Vino aici.” a strigat.")
        // Punctuation as words of its own, and across a line break.
        assertThat(Language.ESPERANTO.support.ocrQuotes(listOf(listOf("„", "Jes", "."), listOf("\"", "kaj"))))
            .containsExactly(listOf("„", "Jes", "."), listOf("“", "kaj")).inOrder()
    }

    @Test
    fun `a straight quote at a word's start with no quote open is the language's opening quote`() {
        // eo-anki.png: the “ that opens “Venu, written as " after the quote before it closed.
        assertThat(read(Language.ESPERANTO, "Li diris „Jes.\" kaj foriris. \"Venu, amiko.” Ŝi venis."))
            .containsExactly("Li diris „Jes.“ kaj foriris. “Venu, amiko.” Ŝi venis.")
        // sw-anki.png: cloud recognition and ML Kit wrote “Karibu!” as "Karibu!".
        assertThat(read(Language.SWAHILI, "walirudi. \"Karibu!\" alisema Bi.", "Amina."))
            .containsExactly("walirudi. “Karibu!” alisema Bi.", "Amina.").inOrder()
        // The language's first quote: Romanian „…”, Danish »…«, Finnish ”…”, Russian «…».
        assertThat(read(Language.ROMANIAN, "\"Vino aici!\" a strigat.")).containsExactly("„Vino aici!” a strigat.")
        assertThat(read(Language.DANISH, "\"Kom her!\" råbte hun.")).containsExactly("»Kom her!« råbte hun.")
        assertThat(read(Language.FINNISH, "\"Tule tänne.\" hän sanoi.")).containsExactly("”Tule tänne.” hän sanoi.")
        assertThat(read(Language.RUSSIAN, "\"Иди сюда!\" крикнула она.")).containsExactly("«Иди сюда!» крикнула она.")
        // A quote of its own before the word, as cloud recognition gives it.
        assertThat(Language.SWAHILI.support.ocrQuotes(listOf(listOf("walirudi", ".", "\"", "Karibu", "!", "\""))))
            .containsExactly(listOf("walirudi", ".", "“", "Karibu", "!", "”"))
    }

    @Test
    fun `a straight quote that ends a word closes the quote open before it`() {
        // After a comma or a letter, so the quote opened does not stay open up to the next sentence's full stop.
        assertThat(read(Language.ENGLISH, "\"Hello,\" he said. \"Fine.\" She left."))
            .containsExactly("“Hello,” he said. “Fine.” She left.")
        assertThat(read(Language.ENGLISH, "He said \"yes\" and left. \"Fine.\"")).containsExactly("He said “yes” and left. “Fine.”")
        assertThat(read(Language.ESPERANTO, "Li diris „Jes\" kaj foriris.")).containsExactly("Li diris „Jes“ kaj foriris.")
        // A quote of its own after a comma, as cloud recognition gives it.
        assertThat(Language.ENGLISH.support.ocrQuotes(listOf(listOf("\"", "Hello", ",", "\"", "he", "said"))))
            .containsExactly(listOf("“", "Hello", ",", "”", "he", "said"))
    }

    @Test
    fun `a straight quote inside a word, at its start with a quote open, or after a closed quote stays`() {
        assertThat(read(Language.ROMANIAN, "„Vino” aici!\" a strigat.")).containsExactly("„Vino” aici!\" a strigat.")
        // A bracket is no quote.
        assertThat(read(Language.ENGLISH, "(Stop!\" she said.")).containsExactly("(Stop!\" she said.")
        // Inside a word: Hebrew gershayim written as ", inches.
        assertThat(read(Language.HEBREW, "צה\"ל")).containsExactly("צה\"ל")
        assertThat(read(Language.ENGLISH, "a 5\" screen")).containsExactly("a 5\" screen")
        // With a quote open, a " at a word's start is not a second opening quote; with none open, one at a word's end
        // closes nothing.
        assertThat(read(Language.GERMAN, "„Er rief \"Halt")).containsExactly("„Er rief \"Halt")
        assertThat(read(Language.ENGLISH, "“He said. \"Run")).containsExactly("“He said. \"Run")
        assertThat(read(Language.ENGLISH, "he said, yes\" and left")).containsExactly("he said, yes\" and left")
        // Japanese pairs no quotation marks of that kind.
        assertThat(read(Language.JAPANESE, "\"はい\"")).containsExactly("\"はい\"")
    }
}
