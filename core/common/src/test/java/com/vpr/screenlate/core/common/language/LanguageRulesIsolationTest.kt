package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

/**
 * Each language's special rule fires for that language only: the same text takes the shared rules in every other one
 * (the table in `ai/notes/language-rules.md`).
 */
class LanguageRulesIsolationTest {

    private val all = Language.entries.map { it.support }
    private val spaced = all.filterIsInstance<SpacedLanguageSupport>()

    private fun List<LanguageSupport>.where(predicate: (LanguageSupport) -> Boolean): List<Language> =
        filter(predicate).map { it.language }

    @Test
    fun `an elided word starts a new word only in French, Italian and Portuguese`() {
        assertThat(spaced.where { it.wordStartOffset("de l'ea", "u") == 2 })
            .containsExactly(Language.FRENCH, Language.ITALIAN)
        assertThat(spaced.where { it.wordStartOffset("gota d'águ", "a") == 3 })
            .containsExactly(Language.FRENCH, Language.ITALIAN, Language.PORTUGUESE)
        // Everywhere else the apostrophe joins the word.
        for (support in spaced.filter { it.elisions.isEmpty() }) {
            assertThat(support.wordStartOffset("de l'ea", "u")).isEqualTo(4)
            assertThat(support.wordStartOffset("gota d'águ", "a")).isEqualTo(5)
        }
    }

    @Test
    fun `words with an apostrophe of their own stay whole in every language`() {
        for (support in spaced) {
            assertThat(support.wordStartOffset("don'", "t")).isEqualTo(4)
            assertThat(support.wordStartOffset("o'clo", "ck")).isEqualTo(5)
            assertThat(support.wordStartOffset("aujourd'h", "ui")).isEqualTo(9)
            assertThat(support.wordStartOffset("presqu’î", "le")).isEqualTo(8)
            assertThat(support.wordStartOffset("rock’n’r", "oll")).isEqualTo(8)
            // A Turkish suffix after a name, and the same apostrophe in any other language's text.
            assertThat(support.wordStartOffset("Dün İstanbul'", "a")).isEqualTo(9)
            assertThat(support.wordStartOffset("Paris'", "s")).isEqualTo(6)
        }
    }

    @Test
    fun `stress and tone marks typed apart stay inside their word`() {
        assertThat(RussianSupport.wordStartOffset("ко\u0301ш", "к")).isEqualTo(4)
        assertThat(VietnameseSupport.wordStartOffset("hoa\u0300 bi\u0300", "n")).isEqualTo(3)
        assertThat(RussianSupport.lookupStart("\u0301шка", latinAsNative = false)).isNull()
    }

    @Test
    fun `only Vietnamese starts lookups at the syllables before the aimed one`() {
        assertThat(all.where { it.earlierStartOffset("Tôi là học ") > 0 }).containsExactly(Language.VIETNAMESE)
    }

    @Test
    fun `only Korean starts at the beginning of a spaced word, Latin words start at theirs everywhere`() {
        assertThat(KoreanSupport.wordStartOffset("공부", "하")).isEqualTo(2)
        assertThat(JapaneseSupport.wordStartOffset("べんき", "ょ")).isEqualTo(0)
        assertThat(ChineseSupport.wordStartOffset("图书", "馆")).isEqualTo(0)
        // Latin letters with digits before them: Korean leaves the digits out, as for 3월.
        assertThat(KoreanSupport.wordStartOffset("3", "월")).isEqualTo(0)
        for (support in listOf(JapaneseSupport, ChineseSupport, KoreanSupport)) {
            assertThat(support.wordStartOffset("Ｔシャツ and wa", "lk")).isEqualTo(2)
        }
    }

    @Test
    fun `a period after a short number keeps the sentence going only where ordinals take one`() {
        fun endsAfter(support: LanguageSupport, text: String) = support.endsSentence(text, text.indexOf('.'))
        val ordinals = listOf(Language.GERMAN, Language.POLISH, Language.TURKISH, Language.LATVIAN)
        assertThat(spaced.where { !endsAfter(it, "am 3. Oktober") }).containsExactlyElementsIn(ordinals)
        assertThat(spaced.where { !endsAfter(it, "1918. gada") }).containsExactlyElementsIn(ordinals)
        // A year before a capital letter ends the sentence in every language.
        assertThat(spaced.where { !endsAfter(it, "1918. Dann") }).isEmpty()
        assertThat(KoreanSupport.endsSentence("3. 그리고", 1)).isTrue()
    }

    @Test
    fun `abbreviations of one language do not hold the sentences of another`() {
        fun holds(support: LanguageSupport, text: String) = !support.endsSentence(text, text.indexOf('.'))
        // German "z. B." holds its single letters only beside each other; "Plan B." ends everywhere.
        assertThat(spaced.where { holds(it, "Obst z. B. Äpfel") }).containsExactly(Language.GERMAN)
        assertThat(spaced.where { holds(it, "Plan B. Dann") }).isEmpty()
        assertThat(spaced.filter { it.pairedLetters.isNotEmpty() }.map { it.language }).containsExactly(Language.GERMAN)
        assertThat(spaced.where { holds(it, "Mr. Smith") }).containsExactly(Language.ENGLISH, Language.DUTCH)
        assertThat(spaced.where { holds(it, "т. е. так") }).containsExactly(Language.RUSSIAN)
        assertThat(spaced.where { holds(it, "ở TP. Hồ") }).containsExactly(Language.VIETNAMESE)
        // Every spaced language keeps a period followed by a letter or digit inside the sentence.
        assertThat(spaced.where { holds(it, "pi is 3.14") }).containsExactlyElementsIn(spaced.map { it.language })
    }

    @Test
    fun `a word hyphenated at a line end is tried joined in every spaced language`() {
        fun variants(support: LanguageSupport, text: String) =
            support.spellingVariants(MappedText.identity(text)).map { it.text }
        for (support in spaced) {
            assertThat(variants(support, "impor- tant thing")).containsExactly("important thing", "impor-tant thing")
            // A capital after the break keeps the hyphen only (Max-Planck).
            assertThat(variants(support, "Max- Planck")).containsExactly("Max-Planck")
            // A dash between words and a hyphen inside a word are no breaks.
            assertThat(variants(support, "this - that")).isEmpty()
            assertThat(variants(support, "well-known")).isEmpty()
            // Aimed at the second half, the lookup starts at the first one as well.
            assertThat(support.earlierStartOffset("the impor- ")).isEqualTo("impor- ".length)
            assertThat(support.earlierStartOffset("the mother-in- ")).isEqualTo("mother-in- ".length)
            assertThat(support.earlierStartOffset("this - ")).isEqualTo(0)
        }
        // The joined word maps back over both halves and the break.
        val joined = EnglishSupport.spellingVariants(MappedText.identity("impor- tant"))[0]
        assertThat(joined.sourceLength("important".length)).isEqualTo("impor- tant".length)
        // Scripts without spaces have no line-end hyphens to join.
        assertThat(ChineseSupport.spellingVariants(MappedText.identity("impor- tant"))).isEmpty()
        assertThat(KoreanSupport.spellingVariants(MappedText.identity("impor- tant"))).isEmpty()
        assertThat(all.filterNot { it in spaced }.where { it.earlierStartOffset("impor- ") > 0 }).isEmpty()
    }

    @Test
    fun `only German and Dutch split compounds, German puts capitalized parts first`() {
        assertThat(all.where { it.compoundLinks != null }).containsExactly(Language.GERMAN, Language.DUTCH)
        assertThat(all.where { it.capitalizedNouns }).containsExactly(Language.GERMAN)
    }

    @Test
    fun `form-of tags are dropped or respelled only for their language`() {
        assertThat(all.where { it.ignoredFormTags.isNotEmpty() }).containsExactly(Language.VIETNAMESE)
        assertThat(all.where { it.spellingFormTags.isNotEmpty() }).containsExactly(Language.CHINESE)
    }

    @Test
    fun `only Japanese respells the text and converts romaji`() {
        for (text in listOf("1人", "人々", "ヤッタ", "東京・大阪")) {
            assertThat(all.where { it.spellingVariants(MappedText.identity(text)).isNotEmpty() })
                .containsExactly(Language.JAPANESE)
        }
        assertThat(all.where { it.fromLatin(MappedText.identity("konnichiha")) != null }).containsExactly(Language.JAPANESE)
        assertThat(all.where { it.convertsLatin }).containsExactly(Language.JAPANESE)
    }

    @Test
    fun `only Chinese rewrites readings, searches by them and has two scripts`() {
        assertThat(all.where { it.displayReading("xíng (xing²)") != "xíng (xing²)" }).containsExactly(Language.CHINESE)
        assertThat(all.where { it.normalizeReading("nǐ hǎo") != "nǐ hǎo" }).containsExactly(Language.CHINESE)
        assertThat(all.where { it.readingSearch }).containsExactly(Language.CHINESE)
        assertThat(all.where { it.readingKey("nihao") != null }).containsExactly(Language.CHINESE)
        assertThat(all.where { it.scriptOf("图书馆") != null }).containsExactly(Language.CHINESE)
        assertThat(all.where { it.inScript(MappedText.identity("图书馆"), ChineseScripts.TRADITIONAL).isNotEmpty() })
            .containsExactly(Language.CHINESE)
        // A reading in parentheses that is no numbered pinyin stays.
        assertThat(ChineseSupport.displayReading("ok (formal)")).isEqualTo("ok (formal)")
    }

    @Test
    fun `cyrillic look-alikes are fixed only in Russian`() {
        assertThat(all.where { it.ocrWord("\u043co\u0440e") != "\u043co\u0440e" }).containsExactly(Language.RUSSIAN)
    }

    @Test
    fun `scripts written without spaces shorten the text by letters`() {
        assertThat(all.where { it.searchResolution == SearchResolution.LETTER })
            .containsExactly(Language.JAPANESE, Language.CHINESE, Language.KOREAN)
        assertThat(all.where { it.wordSeparator.isEmpty() }).containsExactly(Language.JAPANESE, Language.CHINESE)
    }
}
