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
        // The latest quote open: the inner one.
        assertThat(read(Language.GERMAN, "„Er rief ‚Halt!\" und lief.")).containsExactly("„Er rief ‚Halt!‘ und lief.")
    }

    @Test
    fun `a straight quote with no quote open, after other characters or after a closed quote stays`() {
        assertThat(read(Language.ROMANIAN, "\"Vino aici!\" a strigat.")).containsExactly("\"Vino aici!\" a strigat.")
        assertThat(read(Language.ROMANIAN, "„Vino aici.\" a strigat.")).containsExactly("„Vino aici.\" a strigat.")
        assertThat(read(Language.ROMANIAN, "„Vino” aici!\" a strigat.")).containsExactly("„Vino” aici!\" a strigat.")
        // A bracket is no quote.
        assertThat(read(Language.ENGLISH, "(Stop!\" she said.")).containsExactly("(Stop!\" she said.")
    }
}
