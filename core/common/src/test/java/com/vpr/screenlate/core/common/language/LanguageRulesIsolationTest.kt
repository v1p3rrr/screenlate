package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

/**
 * Each language's special rule fires for that language only: the same text takes the shared rules in every other one
 * (the table in `ai/notes/language-rules.md`).
 */
class LanguageRulesIsolationTest {

    private val all = Language.entries.map { it.support }
    private val spaced = all.filterIsInstance<SpacedLanguageSupport>()

    /** Spaced languages whose period ends a sentence: all but Armenian, which ends one at ։. */
    private val periodEnding = spaced.filter { it.endsSentence("Run. Then", "Run".length) }

    private fun List<LanguageSupport>.where(predicate: (LanguageSupport) -> Boolean): List<Language> =
        filter(predicate).map { it.language }

    @Test
    fun `an elided word starts a new word only in French, Italian, Portuguese, Greek, Albanian, Irish and Scottish Gaelic`() {
        assertThat(spaced.where { it.wordStartOffset("de l'ea", "u") == 2 })
            .containsExactly(Language.FRENCH, Language.ITALIAN)
        assertThat(spaced.where { it.wordStartOffset("gota d'águ", "a") == 3 })
            .containsExactly(Language.FRENCH, Language.ITALIAN, Language.PORTUGUESE, Language.IRISH, Language.SCOTTISH_GAELIC)
        // Irish and Gaelic m' (mo) and b' (ba, bu): m'athair, b'fhéidir.
        assertThat(spaced.where { it.wordStartOffset("is m'athai", "r") == 5 })
            .containsExactly(Language.FRENCH, Language.ITALIAN, Language.ALBANIAN, Language.IRISH, Language.SCOTTISH_GAELIC)
        assertThat(spaced.where { it.wordStartOffset("b'fhéidi", "r") == 6 })
            .containsExactly(Language.IRISH, Language.SCOTTISH_GAELIC)
        // Gaelic dh' and the th' of tha written onto ann: dh'fhàg, th'ann.
        assertThat(spaced.where { it.wordStartOffset("dh'fh", "àg") == 2 }).containsExactly(Language.SCOTTISH_GAELIC)
        assertThat(spaced.where { it.wordStartOffset("a th'an", "n") == 2 }).containsExactly(Language.SCOTTISH_GAELIC)
        assertThat(spaced.where { it.wordStartOffset("σ'αγα", "π") == 3 }).containsExactly(Language.GREEK)
        // French elides ça as well (ç'a été).
        assertThat(spaced.where { it.wordStartOffset("ç'ësh", "të") == 3 }).containsExactly(Language.FRENCH, Language.ALBANIAN)
        // Everywhere else the apostrophe joins the word.
        for (support in spaced.filter { it.elisions.isEmpty() }) {
            assertThat(support.wordStartOffset("de l'ea", "u")).isEqualTo(4)
            assertThat(support.wordStartOffset("gota d'águ", "a")).isEqualTo(5)
            assertThat(support.wordStartOffset("σ'αγα", "π")).isEqualTo(5)
            assertThat(support.wordStartOffset("ç'ësh", "të")).isEqualTo(5)
        }
    }

    @Test
    fun `an ending written after a colon joins the word before it only in Finnish`() {
        assertThat(spaced.where { it.wordStartOffset("EU:", "n", "") == 3 }).containsExactly(Language.FINNISH)
        assertThat(spaced.where { it.wordStartOffset("STT:l", "l", "e") == 5 }).containsExactly(Language.FINNISH)
        // A time or a ratio stays apart everywhere.
        assertThat(spaced.where { it.wordStartOffset("klo 12:", "3", "0") != 0 }).isEmpty()
    }

    @Test
    fun `a pronoun or preposition joined with a hyphen starts a new word only in Romanian`() {
        assertThat(spaced.where { it.wordStartOffset("s-", "a dus") == 0 }).containsExactly(Language.ROMANIAN)
        assertThat(spaced.where { it.wordStartOffset("într-", "o zi") == 0 }).containsExactly(Language.ROMANIAN)
        // A word before a pronoun keeps it in every language, as hyphenated names do.
        for (support in spaced) {
            assertThat(support.wordStartOffset("să-", "l")).isEqualTo(3)
            assertThat(support.wordStartOffset("Baia-", "Mare")).isEqualTo(5)
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
    fun `vowel signs and joiners stay inside their word in every spaced language`() {
        for (support in spaced) {
            // ि is a spacing mark, drawn before its letter.
            assertThat(support.wordStartOffset("आज कि", "ताब")).isEqualTo(2)
            assertThat(support.wordStartOffset("क्\u200C", "ष")).isEqualTo(3)
            assertThat(support.lookupStart("ाना", latinAsNative = false)).isNull()
        }
    }

    @Test
    fun `only Vietnamese starts lookups at the syllables before the aimed one, Thai and Khmer at the words before it`() {
        assertThat(all.where { it.earlierStartOffset("Tôi là học ") > 0 }).containsExactly(Language.VIETNAMESE)
        assertThat(all.where { it.earlierStartOffset("ห้อง", "สมุด") > 0 }).containsExactly(Language.THAI)
        assertThat(all.where { it.earlierStartOffset("ខ្ញុំ", "ទៅ") > 0 }).containsExactly(Language.KHMER)
        // Persian only at a verb prefix or before an ending written apart; Vietnamese at any syllable.
        assertThat(all.where { it.earlierStartOffset("می ", "خواهم") > 0 }).containsExactly(Language.VIETNAMESE, Language.PERSIAN)
        assertThat(all.where { it.earlierStartOffset("کتاب ", "ها") > 0 }).containsExactly(Language.VIETNAMESE, Language.PERSIAN)
        assertThat(all.where { it.earlierStartOffset("کتاب ", "خوب") > 0 }).containsExactly(Language.VIETNAMESE)
    }

    @Test
    fun `only Thai and Khmer find words with a segmenter, keep clusters whole and drop shorter matches inside a word`() {
        // Spaced languages start words after spaces; Thai and Khmer where the segmenter does (one piece without the
        // phone's ICU), and only in their own script.
        assertThat(all.where { it.wordStarts("ห้องสมุด เปิด", "ห้องสมุด เ".length) != listOf(0, "ห้องสมุด ".length) })
            .containsExactly(Language.THAI, Language.KHMER)
        assertThat(all.where { it.shownMatchLengths("การตลาด", setOf(1, 7)) != setOf(1, 7) })
            .containsExactly(Language.THAI, Language.KHMER)
        assertThat(all.where { it.ocrSpaceGap != null }).containsExactly(Language.THAI, Language.KHMER)
        // Only they start lookups inside the aimed word when nothing from its start reaches the aim, in their script.
        assertThat(all.where { it.innerStarts("โรงเรียน", 0, "โรงเรีย".length).isNotEmpty() }).containsExactly(Language.THAI)
        assertThat(all.where { it.innerStarts("ក្រុងភ្នំពេញ", 0, "ក្រុងភ្នំពេ".length).isNotEmpty() })
            .containsExactly(Language.KHMER)
        assertThat(all.where { it.innerStarts("lookup", 0, "look".length).isNotEmpty() }).isEmpty()
    }

    @Test
    fun `only Thai ends a sentence at a space between letters`() {
        assertThat(all.where { it.endsSentence("กิน ข้าว", "กิน".length) }).containsExactly(Language.THAI)
        assertThat(all.where { it.endsSentence("eat rice", "eat".length) }).isEmpty()
    }

    @Test
    fun `only Khmer ends a sentence at khan and the section end`() {
        assertThat(all.where { it.endsSentence("ទៅ។", "ទៅ".length) }).containsExactly(Language.KHMER)
        assertThat(all.where { it.endsSentence("ចប់៕", "ចប់".length) }).containsExactly(Language.KHMER)
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
        val ordinals = listOf(
            Language.GERMAN, Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.CZECH,
            Language.CROATIAN, Language.SERBIAN, Language.NORWEGIAN, Language.DANISH, Language.SLOVAK, Language.SLOVENIAN,
            Language.FINNISH, Language.HUNGARIAN, Language.ESTONIAN,
        )
        assertThat(periodEnding.where { !endsAfter(it, "am 3. Oktober") }).containsExactlyElementsIn(ordinals)
        assertThat(periodEnding.where { !endsAfter(it, "1918. gada") }).containsExactlyElementsIn(ordinals)
        assertThat(periodEnding.where { !endsAfter(it, "Karel IV. byl") }).containsExactlyElementsIn(ordinals)
        // A year before a capital letter ends the sentence in every language.
        assertThat(periodEnding.where { !endsAfter(it, "1918. Dann") }).isEmpty()
        assertThat(KoreanSupport.endsSentence("3. 그리고", 1)).isTrue()
    }

    @Test
    fun `abbreviations of one language do not hold the sentences of another`() {
        fun holds(support: LanguageSupport, text: String) = !support.endsSentence(text, text.indexOf('.'))
        // German "z. B." holds its single letters only beside each other (Albanian z., zoti, holds alone); "Plan B."
        // before a lowercase word ends everywhere but in Azerbaijani and Uzbek, whose "və b." and "va b." (and others)
        // hold before one, and before a capital it is an initial everywhere.
        assertThat(periodEnding.where { holds(it, "Obst z. B. Äpfel") }).containsExactly(Language.GERMAN, Language.ALBANIAN)
        assertThat(periodEnding.where { holds(it, "Plan B. dann") }).containsExactly(Language.AZERBAIJANI, Language.UZBEK)
        assertThat(periodEnding.where { !holds(it, "Plan B. Dann") }).isEmpty()
        assertThat(spaced.filter { it.pairedLetters.isNotEmpty() }.map { it.language })
            .containsExactly(
                Language.GERMAN, Language.CZECH, Language.CROATIAN, Language.SERBIAN, Language.LITHUANIAN, Language.SLOVAK,
                Language.SLOVENIAN, Language.ESPERANTO, Language.BULGARIAN, Language.MACEDONIAN,
            )
        // Czech and Slovak "n. l.": the l takes its period from the n beside it; Italian and Portuguese "n." (number)
        // holds alone. Slovenian l. (leto, year) holds before a lowercase word.
        val era = "v roce 300 n. l. vznikl"
        assertThat(periodEnding.where { !it.endsSentence(era, era.indexOf("l.") + 1) })
            .containsExactly(Language.CZECH, Language.SLOVAK, Language.SLOVENIAN)
        // Only Czech OCR text gets the l of "n. l." back from a capital I; app text with the capital is taken as written.
        assertThat(all.where { it.ocrLines(listOf(listOf("n.", "I.", "Potom"))).single()[1] == "l." })
            .containsExactly(Language.CZECH)
        assertThat(all.where { it.ocrLines(listOf(listOf("n", ".", "I", ".", "Potom"))).single()[2] == "l" })
            .containsExactly(Language.CZECH)
        // Only Czech OCR text parts a word glued to the era's last period.
        assertThat(all.where { it.ocrLines(listOf(listOf("n.l.a"))).single().single() != "n.l.a" }).containsExactly(Language.CZECH)
        val joined = "v roce 300 n.l. vznikl"
        assertThat(periodEnding.where { !it.endsSentence(joined, joined.indexOf("l.") + 1) }).containsExactly(Language.CZECH, Language.SLOVAK)
        // Before a capital the I is an initial in every language.
        val capital = "v roce 300 n. I. Potom"
        assertThat(periodEnding.where { it.endsSentence(capital, capital.indexOf("I.") + 1) }).isEmpty()
        // Croatian and Serbian mr. is magistar, Albanian mr. magjistër.
        assertThat(periodEnding.where { holds(it, "Mr. Smith") })
            .containsExactly(Language.ENGLISH, Language.DUTCH, Language.CROATIAN, Language.SERBIAN, Language.ALBANIAN)
        // Slovak kr. of "pred Kr." holds before a lowercase word too.
        assertThat(periodEnding.where { holds(it, "Kr. godine") }).containsExactly(Language.CROATIAN, Language.SERBIAN, Language.SLOVAK)
        // Ukrainian т. holds for "т. зв." and "у т. ч.", Bulgarian т. is том (volume), Macedonian т. pairs with е and н.
        assertThat(periodEnding.where { holds(it, "т. е. так") })
            .containsExactly(Language.RUSSIAN, Language.UKRAINIAN, Language.BULGARIAN, Language.MACEDONIAN)
        assertThat(periodEnding.where { holds(it, "у 1991 р. він") }).containsExactly(Language.UKRAINIAN)
        // г. is год, гады and година; Croatian keeps the Cyrillic abbreviations Serbian has.
        assertThat(periodEnding.where { holds(it, "у 1991 г. ён") }).containsExactly(
            Language.RUSSIAN, Language.BELARUSIAN, Language.CROATIAN, Language.SERBIAN, Language.BULGARIAN, Language.MACEDONIAN,
        )
        assertThat(periodEnding.where { holds(it, "ở TP. Hồ") }).containsExactly(Language.VIETNAMESE)
        // Kazakh and Kyrgyz ж. (year) holds before a lowercase word only; Kyrgyz writes ж. б. (and so on) spaced.
        assertThat(periodEnding.where { holds(it, "2007 ж. қазанда") }).containsExactly(Language.KAZAKH, Language.KYRGYZ)
        assertThat(periodEnding.where { holds(it, "1991 ж. Кейін") }).isEmpty()
        val andSoOn = "алма ж. б. жемиштер"
        assertThat(periodEnding.where { !it.endsSentence(andSoOn, andSoOn.lastIndexOf(". ")) }).containsExactly(Language.KYRGYZ)
        val example = "π.χ. το"
        assertThat(periodEnding.where { !it.endsSentence(example, example.indexOf(". ")) }).containsExactly(Language.GREEK)
        // Hindi and Marathi डॉ., whose vowel sign is a mark, and Latin letter names joined by periods; Bengali the same
        // way.
        assertThat(periodEnding.where { holds(it, "डॉ. शर्मा") }).containsExactly(Language.HINDI, Language.MARATHI)
        val acronym = "ए.पी.जे. कलाम"
        assertThat(periodEnding.where { !it.endsSentence(acronym, acronym.indexOf(". ")) })
            .containsExactly(Language.HINDI, Language.MARATHI)
        assertThat(periodEnding.where { holds(it, "মো. রহিম") }).containsExactly(Language.BENGALI)
        val bengaliAcronym = "এ.কে. ফজলুল"
        assertThat(periodEnding.where { !it.endsSentence(bengaliAcronym, bengaliAcronym.indexOf(". ")) })
            .containsExactly(Language.BENGALI)
        assertThat(spaced.filter { it.initials.isNotEmpty() }.map { it.language })
            .containsExactly(Language.HINDI, Language.BENGALI, Language.MARATHI, Language.TAMIL)
        // Marathi and Tamil initials are syllables ("पु. ल. देशपांडे", "க. சண்முகம்"), but for their one-syllable words of
        // dialogue.
        assertThat(periodEnding.where { holds(it, "पु. ल. देशपांडे") }).containsExactly(Language.MARATHI)
        assertThat(periodEnding.where { holds(it, "हो. मी") }).isEmpty()
        assertThat(periodEnding.where { holds(it, "க. சண்முகம்") }).containsExactly(Language.TAMIL)
        assertThat(periodEnding.where { holds(it, "வா. நான்") }).isEmpty()
        assertThat(spaced.filter { it.syllableInitialScript != null }.map { it.language })
            .containsExactly(Language.MARATHI, Language.TAMIL)
        // Tamil திரு. and ரூ.; its months hold before a number only.
        assertThat(periodEnding.where { holds(it, "திரு. ராமன்") }).containsExactly(Language.TAMIL)
        assertThat(periodEnding.where { holds(it, "ஜன. 15") }).containsExactly(Language.TAMIL)
        assertThat(periodEnding.where { holds(it, "ஜன. அவர்") }).isEmpty()
        // Georgian ქ. (city) and წ. (year), single letters without a partner.
        assertThat(periodEnding.where { holds(it, "ქ. თბილისი") }).containsExactly(Language.GEORGIAN)
        assertThat(periodEnding.where { holds(it, "1991 წ. დაიწყო") }).containsExactly(Language.GEORGIAN)
        // Swedish t.ex., and ung. (ungefär), which holds only before a lowercase word or a number.
        val forExample = "frukt t.ex. äpplen"
        assertThat(periodEnding.where { !it.endsSentence(forExample, forExample.lastIndexOf('.')) })
            .containsExactly(Language.SWEDISH)
        assertThat(periodEnding.where { holds(it, "ung. tre") }).containsExactly(Language.SWEDISH)
        assertThat(periodEnding.where { holds(it, "ung. Han") }).isEmpty()
        // Norwegian and Danish f.eks. (for eksempel) and osv., which holds only before a lowercase word or a number.
        val eksempel = "byer, f.eks. Oslo"
        assertThat(periodEnding.where { !it.endsSentence(eksempel, eksempel.lastIndexOf('.')) })
            .containsExactly(Language.NORWEGIAN, Language.DANISH)
        assertThat(periodEnding.where { holds(it, "osv. og") })
            .containsExactly(Language.SWEDISH, Language.NORWEGIAN, Language.DANISH)
        assertThat(periodEnding.where { holds(it, "osv. Han") }).isEmpty()
        // Danish hhv. (henholdsvis) and stk. (styk), before a name or a number.
        assertThat(periodEnding.where { holds(it, "hhv. Aars") }).containsExactly(Language.DANISH)
        assertThat(periodEnding.where { holds(it, "stk. Tuborg") }).containsExactly(Language.DANISH)
        // Romanian dl. (domnul), and î.Hr., which holds only before a lowercase word.
        assertThat(periodEnding.where { holds(it, "Dl. Popescu") }).containsExactly(Language.ROMANIAN)
        val beforeChrist = "anul 44 î.Hr. a fost"
        assertThat(periodEnding.where { !it.endsSentence(beforeChrist, beforeChrist.indexOf(". ")) })
            .containsExactly(Language.ROMANIAN)
        val beforeCapital = "anul 44 î.Hr. Apoi"
        assertThat(periodEnding.where { !it.endsSentence(beforeCapital, beforeCapital.indexOf(". ")) }).isEmpty()
        // Latin praenomina (Sex. for Sextus), which hold only capitalized, and a.C.n., only before a lowercase word.
        assertThat(periodEnding.where { holds(it, "Sex. Pompeius") }).containsExactly(Language.LATIN)
        assertThat(periodEnding.where { holds(it, "sex. Deinde") }).isEmpty()
        val latinEra = "anno 44 a.C.n. natus"
        assertThat(periodEnding.where { !it.endsSentence(latinEra, latinEra.lastIndexOf('.')) }).containsExactly(Language.LATIN)
        assertThat(spaced.filter { it.capitalizedAbbreviations.isNotEmpty() }.map { it.language }).containsExactly(Language.LATIN)
        // Lithuanian pvz. (pavyzdžiui), tūkst. (thousand), which Latvian writes too but Lithuanian holds only before a
        // lowercase word or a number, and the y of "t. y.", which takes its period from the t beside it (Uzbek y., yil,
        // year, holds before a lowercase word too).
        assertThat(periodEnding.where { holds(it, "pvz. obuoliai") }).containsExactly(Language.LITHUANIAN)
        assertThat(periodEnding.where { holds(it, "5 tūkst. eurų") }).containsExactly(Language.LATVIAN, Language.LITHUANIAN)
        assertThat(periodEnding.where { holds(it, "5 tūkst. Vėliau") }).containsExactly(Language.LATVIAN)
        val thatIs = "vaisiai, t. y. obuoliai"
        assertThat(periodEnding.where { !it.endsSentence(thatIs, thatIs.lastIndexOf(". ")) })
            .containsExactly(Language.LITHUANIAN, Language.UZBEK)
        // Slovak napr. (napríklad), which Czech writes např.
        assertThat(periodEnding.where { holds(it, "napr. jablká") }).containsExactly(Language.SLOVAK)
        assertThat(periodEnding.where { holds(it, "napr. Jablká") }).containsExactly(Language.SLOVAK)
        // Slovenian npr. (na primer), which Croatian and Serbian write too, and št. (številka), which holds only before a
        // lowercase word or a number.
        assertThat(periodEnding.where { holds(it, "npr. jabolka") }).containsExactly(Language.CROATIAN, Language.SERBIAN, Language.SLOVENIAN)
        assertThat(periodEnding.where { holds(it, "št. 5") }).containsExactly(Language.SLOVENIAN)
        assertThat(periodEnding.where { holds(it, "št. Potem") }).isEmpty()
        val sloveneEra = "leta 400 pr. n. št. so"
        assertThat(periodEnding.where { !it.endsSentence(sloveneEra, sloveneEra.lastIndexOf('.')) }).containsExactly(Language.SLOVENIAN)
        // Albanian zj. (zonja), p.sh. (për shembull) and etj. (e të tjera), which holds only before a lowercase word or a
        // number.
        assertThat(periodEnding.where { holds(it, "Zj. Hoxha") }).containsExactly(Language.ALBANIAN)
        val forInstance = "frutat p.sh. mollët"
        assertThat(periodEnding.where { !it.endsSentence(forInstance, forInstance.lastIndexOf('.')) }).containsExactly(Language.ALBANIAN)
        assertThat(periodEnding.where { holds(it, "etj. dhe") }).containsExactly(Language.ALBANIAN)
        assertThat(periodEnding.where { holds(it, "etj. Pastaj") }).isEmpty()
        // Swahili Bw. (Bwana), k.m. (kwa mfano) and n.k. (na kadhalika), which holds only before a lowercase word or a
        // number.
        assertThat(periodEnding.where { holds(it, "Bw. Juma") }).containsExactly(Language.SWAHILI)
        val swahiliExample = "vyakula k.m. wali"
        assertThat(periodEnding.where { !it.endsSentence(swahiliExample, swahiliExample.lastIndexOf('.')) })
            .containsExactly(Language.SWAHILI)
        val swahiliEtc = "filamu n.k. na"
        assertThat(periodEnding.where { !it.endsSentence(swahiliEtc, swahiliEtc.lastIndexOf('.')) })
            .containsExactly(Language.SWAHILI)
        val swahiliEtcEnd = "filamu n.k. Hata"
        assertThat(periodEnding.where { !it.endsSentence(swahiliEtcEnd, swahiliEtcEnd.lastIndexOf('.')) }).isEmpty()
        // Welsh e.e. (er enghraifft) and Parch. (Parchedig), and Ysw. (Yswain), which holds only before a lowercase word.
        val welshExample = "ffrwythau e.e. afalau"
        assertThat(periodEnding.where { !it.endsSentence(welshExample, welshExample.lastIndexOf('.')) })
            .containsExactly(Language.WELSH)
        assertThat(periodEnding.where { holds(it, "Parch. Jones") }).containsExactly(Language.WELSH)
        assertThat(periodEnding.where { holds(it, "Ysw. a") }).containsExactly(Language.WELSH)
        assertThat(periodEnding.where { holds(it, "Ysw. Mae") }).isEmpty()
        // Irish m.sh. (mar shampla) and lch. (leathanach), and srl. (agus araile), which holds only before a lowercase word.
        val irishExample = "torthaí m.sh. úlla"
        assertThat(periodEnding.where { !it.endsSentence(irishExample, irishExample.lastIndexOf('.')) })
            .containsExactly(Language.IRISH)
        assertThat(periodEnding.where { holds(it, "lch. 5") }).containsExactly(Language.IRISH)
        assertThat(periodEnding.where { holds(it, "srl. agus") }).containsExactly(Language.IRISH)
        assertThat(periodEnding.where { holds(it, "srl. Ansin") }).isEmpty()
        // Gaelic m.e. (mar eisimpleir) and Urr. (Urramach), and msaa. (agus mar sin air adhart), which holds only
        // before a lowercase word.
        val gaelicExample = "measan m.e. ùbhlan"
        assertThat(periodEnding.where { !it.endsSentence(gaelicExample, gaelicExample.lastIndexOf('.')) })
            .containsExactly(Language.SCOTTISH_GAELIC)
        assertThat(periodEnding.where { holds(it, "Urr. Iain") }).containsExactly(Language.SCOTTISH_GAELIC)
        assertThat(periodEnding.where { holds(it, "msaa. agus") }).containsExactly(Language.SCOTTISH_GAELIC)
        assertThat(periodEnding.where { holds(it, "msaa. Tha") }).isEmpty()
        // Finnish esim. (esimerkiksi) and mm. (muun muassa; French MM., messieurs, holds too), and jne. (ja niin
        // edelleen), which holds only before a lowercase word (Estonian jne., ja nii edasi, too).
        assertThat(periodEnding.where { holds(it, "esim. Omenoita") }).containsExactly(Language.FINNISH)
        assertThat(periodEnding.where { holds(it, "mm. Helsinki") }).containsExactly(Language.FRENCH, Language.FINNISH)
        assertThat(periodEnding.where { holds(it, "jne. ja") }).containsExactly(Language.FINNISH, Language.ESTONIAN)
        assertThat(periodEnding.where { holds(it, "jne. Sitten") }).isEmpty()
        // Hungarian pl. (például; Polish pl., plac, holds too) and kb. (körülbelül), and stb. (s a többi), which holds
        // only before a lowercase word.
        assertThat(periodEnding.where { holds(it, "pl. Alma") }).containsExactly(Language.POLISH, Language.HUNGARIAN)
        assertThat(periodEnding.where { holds(it, "kb. 5") }).containsExactly(Language.HUNGARIAN)
        assertThat(periodEnding.where { holds(it, "stb. és") }).containsExactly(Language.HUNGARIAN)
        assertThat(periodEnding.where { holds(it, "stb. Aztán") }).isEmpty()
        // Estonian v.a. (välja arvatud; German v.a., vor allem, holds too) and u. (umbes), and jms. (ja muud
        // sellist), which holds only before a lowercase word.
        val estonianExample = "kõik v.a. Tartu"
        assertThat(periodEnding.where { !it.endsSentence(estonianExample, estonianExample.lastIndexOf('.')) })
            .containsExactly(Language.GERMAN, Language.ESTONIAN)
        assertThat(periodEnding.where { holds(it, "u. 5") }).contains(Language.ESTONIAN)
        assertThat(periodEnding.where { holds(it, "jms. ja") }).containsExactly(Language.ESTONIAN)
        assertThat(periodEnding.where { holds(it, "jms. Siis") }).isEmpty()
        // Azerbaijani məs. (məsələn) and şəh. (şəhəri) in an address; mlrd. holds only before a lowercase word.
        assertThat(periodEnding.where { holds(it, "məs. Bakı") }).containsExactly(Language.AZERBAIJANI)
        assertThat(periodEnding.where { holds(it, "şəh. Nizami") }).containsExactly(Language.AZERBAIJANI)
        assertThat(periodEnding.where { holds(it, "mlrd. manat") }).contains(Language.AZERBAIJANI)
        assertThat(periodEnding.where { holds(it, "mlrd. Sonra") }).doesNotContain(Language.AZERBAIJANI)
        // Uzbek dots. (dotsent) and koʻch. (koʻchasi) in an address, the latter with any apostrophe, and h.k. (hokazo),
        // which holds only before a lowercase word.
        assertThat(periodEnding.where { holds(it, "dots. Aliyev") }).containsExactly(Language.UZBEK)
        assertThat(periodEnding.where { holds(it, "koʻch. Navoiy") }).containsExactly(Language.UZBEK)
        // English ch. (chapter) holds after the apostrophe.
        assertThat(periodEnding.where { holds(it, "ko'ch. Navoiy") }).containsExactly(Language.ENGLISH, Language.UZBEK)
        val uzbekEtc = "daftarlar va h.k. olindi"
        assertThat(periodEnding.where { !it.endsSentence(uzbekEtc, uzbekEtc.lastIndexOf('.')) }).containsExactly(Language.UZBEK)
        val uzbekEtcEnd = "daftarlar va h.k. Keyin"
        assertThat(periodEnding.where { !it.endsSentence(uzbekEtcEnd, uzbekEtcEnd.lastIndexOf('.')) }).isEmpty()
        // Bulgarian and Macedonian пр. н. е. (before the common era), spaced, as Russian and Serbian hold it, or joined;
        // Bulgarian лв. (лева) and хил. (хиляди), Macedonian илј. (илјади) and нпр. (на пример, as Serbian). Bulgarian
        // "и т.н." (and so on) ends a sentence; Macedonian and Russian т.н. is "so-called".
        val bulgarianEra = "през 44 г. пр. н. е. Цезар"
        assertThat(periodEnding.where { !it.endsSentence(bulgarianEra, bulgarianEra.lastIndexOf('.')) }).containsExactly(
            Language.RUSSIAN, Language.CROATIAN, Language.SERBIAN, Language.BULGARIAN, Language.MACEDONIAN,
        )
        val joinedEra = "през 44 г. пр.н.е. Цезар"
        assertThat(periodEnding.where { !it.endsSentence(joinedEra, joinedEra.lastIndexOf('.')) })
            .containsExactly(Language.BULGARIAN, Language.MACEDONIAN)
        assertThat(periodEnding.where { holds(it, "5 лв. за") }).containsExactly(Language.BULGARIAN)
        assertThat(periodEnding.where { holds(it, "5 хил. души") }).containsExactly(Language.BULGARIAN)
        assertThat(periodEnding.where { holds(it, "5 илј. денари") }).containsExactly(Language.MACEDONIAN)
        assertThat(periodEnding.where { holds(it, "нпр. јаболка") })
            .containsExactly(Language.CROATIAN, Language.SERBIAN, Language.MACEDONIAN)
        val bulgarianEtc = "ябълки и т.н. Тогава"
        assertThat(periodEnding.where { !it.endsSentence(bulgarianEtc, bulgarianEtc.lastIndexOf('.')) })
            .containsExactly(Language.RUSSIAN, Language.MACEDONIAN)
        // Every spaced language keeps a period followed by a letter or digit inside the sentence.
        assertThat(periodEnding.where { holds(it, "pi is 3.14") }).containsExactlyElementsIn(periodEnding.map { it.language })
    }

    @Test
    fun `only Greek ends a sentence at a semicolon, and none at the ano teleia`() {
        assertThat(all.where { it.endsSentence("Τι; Ναι", 2) }).containsExactly(Language.GREEK)
        assertThat(all.where { it.endsSentence("Τι· Ναι", 2) }).isEmpty()
    }

    @Test
    fun `only Hindi, Bengali and Marathi end a sentence at the danda`() {
        val danda = listOf(Language.HINDI, Language.BENGALI, Language.MARATHI)
        assertThat(all.where { it.endsSentence("वह आया। फिर", "वह आया".length) }).containsExactlyElementsIn(danda)
        assertThat(all.where { it.endsSentence("সে এল। তারপর", "সে এল".length) }).containsExactlyElementsIn(danda)
        assertThat(all.where { it.endsSentence("राम॥ अब", "राम".length) }).containsExactlyElementsIn(danda)
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
    fun `a soft hyphen inside a word keeps the word whole in every spaced language`() {
        val word = "Kan\u00ADnada"
        for (support in spaced) {
            assertThat(support.wordStartOffset("the Kan\u00ADna", "da")).isEqualTo("Kan\u00ADna".length)
            assertThat(support.spellingVariants(MappedText.identity("$word is")).map { it.text })
                .containsExactly("Kannada is")
            // At a line end the soft hyphen joins the halves as a hyphen does.
            assertThat(support.spellingVariants(MappedText.identity("Kan\u00AD nada")).map { it.text })
                .containsExactly("Kannada")
        }
        val joined = EnglishSupport.spellingVariants(MappedText.identity(word))[0]
        assertThat(joined.sourceLength("Kannada".length)).isEqualTo(word.length)
    }

    @Test
    fun `a zero-width joiner inside an abbreviation keeps it whole in every spaced language`() {
        for (support in spaced) {
            assertThat(support.endsSentence("Mr\u200C. Smith", "Mr\u200C".length))
                .isEqualTo(support.endsSentence("Mr. Smith", "Mr".length))
        }
    }

    @Test
    fun `a later part of a hyphenated word is looked up in every spaced language`() {
        // Kazakh and Kyrgyz start at each hyphen before a part as well, for their particles and endings (сондай-ақ).
        val hyphened = setOf(Language.KAZAKH.support, Language.KYRGYZ.support)
        for (support in spaced) {
            fun starts(vararg parts: String) =
                parts.flatMap { if (support in hyphened) listOf(it.length - 1, it.length) else listOf(it.length) }
            assertThat(support.partStarts("state-owned firms", 0, "state-o".length)).isEqualTo(starts("state-"))
            assertThat(support.partStarts("mother-in-law", 0, "mother-in-l".length))
                .isEqualTo(starts("mother-", "mother-in-"))
            assertThat(support.partStarts("հայ֊ռուսական", 0, "հայ֊ռ".length)).isEqualTo(starts("հայ֊"))
            // Aimed at the first part or at the hyphen, the word is looked up as it is.
            assertThat(support.partStarts("state-owned", 0, "sta".length)).isEmpty()
            assertThat(support.partStarts("state-owned", 0, "state".length)).isEmpty()
            // A dash between words, and the words before the aimed one, are no parts.
            assertThat(support.partStarts("this - that", 0, "this - t".length)).isEmpty()
            assertThat(support.partStarts("a well-known", 2, "a well-k".length)).isEqualTo(starts("a well-"))
            // An apostrophe joins a word without dividing it into parts.
            assertThat(support.partStarts("rock'n'roll", 0, "rock'n'r".length)).isEmpty()
        }
        // An ending or a particle is looked up with its hyphen in Kazakh and Kyrgyz after any part, in Hungarian,
        // Estonian, Azerbaijani and Uzbek after a number or an abbreviation in capitals, in Finnish after a number only;
        // a text starting at the hyphen is looked up in these seven.
        assertThat(spaced.where { it.partStarts("1990-ға", 0, "1990-ғ".length) != listOf("1990-".length) })
            .containsExactly(
                Language.KAZAKH, Language.KYRGYZ, Language.FINNISH, Language.HUNGARIAN, Language.ESTONIAN,
                Language.AZERBAIJANI, Language.UZBEK,
            )
        assertThat(spaced.where { it.partStarts("сондай-ақ", 0, "сондай-а".length) != listOf("сондай-".length) })
            .containsExactly(Language.KAZAKH, Language.KYRGYZ)
        assertThat(spaced.where { it.partStarts("EU-maat", 0, "EU-m".length) != listOf("EU-".length) })
            .containsExactly(
                Language.KAZAKH, Language.KYRGYZ, Language.HUNGARIAN, Language.ESTONIAN, Language.AZERBAIJANI, Language.UZBEK,
            )
        assertThat(all.where { it.lookupStart("-ға", latinAsNative = false) != null })
            .containsExactly(
                Language.KAZAKH, Language.KYRGYZ, Language.FINNISH, Language.HUNGARIAN, Language.ESTONIAN,
                Language.AZERBAIJANI, Language.UZBEK,
            )
        // The maqaf divides Hebrew words only.
        assertThat(spaced.where { it.partStarts("בית\u05BEספר", 0, "בית\u05BEס".length).isNotEmpty() })
            .containsExactly(Language.HEBREW)
        assertThat(all.filterNot { it in spaced }.where { it.partStarts("state-owned", 0, "state-o".length).isNotEmpty() })
            .isEmpty()
    }

    @Test
    fun `only German, Dutch, Swedish, Norwegian, Danish, Finnish, Hungarian and Estonian split compounds, German puts capitalized parts first`() {
        assertThat(all.where { it.compoundLinks != null })
            .containsExactly(
                Language.GERMAN, Language.DUTCH, Language.SWEDISH, Language.NORWEGIAN, Language.DANISH, Language.FINNISH,
                Language.HUNGARIAN, Language.ESTONIAN,
            )
        assertThat(all.where { it.capitalizedNouns }).containsExactly(Language.GERMAN)
    }

    @Test
    fun `form-of tags are dropped or respelled only for their language`() {
        assertThat(all.where { it.ignoredFormTags.isNotEmpty() })
            .containsExactly(
                Language.VIETNAMESE, Language.INDONESIAN, Language.MALAY, Language.FILIPINO, Language.CROATIAN, Language.SERBIAN,
                Language.THAI, Language.KAZAKH, Language.KYRGYZ, Language.WELSH, Language.IRISH, Language.SCOTTISH_GAELIC,
            )
        assertThat(all.where { it.spellingFormTags.isNotEmpty() }).containsExactly(Language.CHINESE)
        // Welsh, Irish and Gaelic name their dictionaries' mutation rows after their own rules.
        assertThat(all.where { it.formTagRules.isNotEmpty() })
            .containsExactly(Language.WELSH, Language.IRISH, Language.SCOTTISH_GAELIC)
        assertThat(all.where { it.respelledOnlyWithoutEntry }).containsExactly(Language.WELSH, Language.SCOTTISH_GAELIC)
        // Romanian leaves out the Moldovan Cyrillic spellings Wiktionary links its words to.
        assertThat(all.where { !it.showsHeadword("есте") }).containsExactly(Language.ROMANIAN)
        assertThat(all.where { !it.showsHeadword("este") }).isEmpty()
        assertThat(all.where { it.inflectionRulesOnlyWithoutEntry })
            .containsExactly(
                Language.ROMANIAN, Language.LATIN, Language.SWAHILI, Language.HUNGARIAN, Language.AZERBAIJANI,
                Language.UZBEK, Language.BULGARIAN, Language.MARATHI, Language.TAMIL,
            )
        assertThat(all.where { it.ruleChainsOnlyWithoutRow }).containsExactly(Language.FINNISH)
    }

    @Test
    fun `only Filipino and Bengali have names of their own, only Filipino and Norwegian another voice tag`() {
        val systemName = { support: LanguageSupport ->
            Locale.forLanguageTag(support.language.code).getDisplayLanguage(Locale.ENGLISH)
        }
        // Java says Bangla; Wiktionary and the translators say Bengali.
        assertThat(all.where { it.nameIn(Locale.ENGLISH) != systemName(it) }).containsExactly(Language.FILIPINO, Language.BENGALI)
        assertThat(all.where { it.speechTag != it.language.code }).containsExactly(Language.FILIPINO, Language.NORWEGIAN)
    }

    @Test
    fun `only Croatian and Serbian share their dictionaries and have other recordings, Norwegian more recording items, these three other locale codes`() {
        assertThat(all.where { it.language.dictionaryCode != it.language.code }).containsExactly(Language.CROATIAN, Language.SERBIAN)
        assertThat(all.where { it.recordings("kuća", "") != listOf(RecordingTitle(it.language.dictionaryCode, "kuća")) })
            .containsExactly(Language.CROATIAN, Language.SERBIAN)
        assertThat(all.where { it.linguaLibreItems != listOf(it.wikidataId) }).containsExactly(Language.CROATIAN, Language.NORWEGIAN)
        assertThat(all.where { it.linguaLibreTitle("кућа") != "кућа" }).containsExactly(Language.SERBIAN)
        assertThat(all.where { it.translationTag("Kuća je velika.") != null }).containsExactly(Language.SERBIAN)
        assertThat(all.where { it.frequencySpellings("knjiga").isNotEmpty() }).containsExactly(Language.CROATIAN, Language.SERBIAN)
        assertThat(all.where { it.frequencySpellings("п'ять").isNotEmpty() }).containsExactly(Language.UKRAINIAN)
        assertThat(all.where { it.frequencyWord("τησ") != "τησ" }).containsExactly(Language.GREEK)
        assertThat(all.where { it.frequencyWord("اين") != "اين" }).containsExactly(Language.PERSIAN)
        assertThat(all.where { it.frequencyWord("צה\"ל") != "צה\"ל" }).containsExactly(Language.HEBREW)
        assertThat(all.where { it.frequencyWord("кiтап") != "кiтап" }).containsExactly(Language.KAZAKH)
        assertThat(all.where { it.frequencyWord("сè") != "сè" }).containsExactly(Language.MACEDONIAN)
        assertThat(all.where { it.frequencyWord("vīta") != "vīta" }).containsExactly(Language.LATIN)
        assertThat(all.where { it.firstTranscription(listOf("Modern-Israeli")) }).containsExactly(Language.HEBREW)
        assertThat(all.where { it.firstTranscription(listOf("🇮🇷", "formal")) }).containsExactly(Language.PERSIAN)
        assertThat(all.where { it.frequencySpellings("آن\u200Cها").isNotEmpty() }).containsExactly(Language.PERSIAN)
        assertThat(all.where { it.recordings("کتاب", "").any { recording -> recording.title == "كتاب" } })
            .containsExactly(Language.PERSIAN)
        assertThat(all.where { it.localeLanguages != setOf(it.language.dictionaryCode) })
            .containsExactly(Language.CROATIAN, Language.SERBIAN, Language.NORWEGIAN)
    }

    @Test
    fun `only Greek, Hindi, Khmer, Arabic, Persian, Kazakh and Kyrgyz key every headword in another spelling, only Arabic two further`() {
        assertThat(all.where { it.headwordMarks?.everyHeadword == true }).containsExactly(
            Language.GREEK, Language.HINDI, Language.KHMER, Language.ARABIC, Language.PERSIAN, Language.KAZAKH, Language.KYRGYZ,
            Language.ROMANIAN, Language.LITHUANIAN, Language.SLOVAK, Language.SLOVENIAN, Language.ESPERANTO, Language.WELSH,
            Language.SCOTTISH_GAELIC, Language.UZBEK,
        )
        assertThat(all.where { it.headwordMarks?.replacements.orEmpty().isNotEmpty() }).containsExactly(
            Language.HINDI, Language.KHMER, Language.ARABIC, Language.PERSIAN, Language.KAZAKH, Language.KYRGYZ, Language.ROMANIAN,
            Language.SLOVENIAN, Language.UZBEK,
        )
        assertThat(all.where { it.headwordMarks?.furtherReplacements.orEmpty().isNotEmpty() }).containsExactly(Language.ARABIC)
        // Arabic lemmas lose their vowel marks and Lithuanian and Slovenian ones their stress although every headword is
        // keyed.
        assertThat(all.where { it.headwordMarks?.let { marks -> marks.lemmasAsWritten != marks.everyHeadword } == true })
            .containsExactly(Language.ARABIC, Language.LITHUANIAN, Language.SLOVENIAN)
        // Lithuanian drops a mark after other letters than the rest (the dot over i, not over e), Slovak its caron,
        // circumflex and diaeresis after their own letters, Esperanto the breve after u; only Lithuanian and Slovenian
        // key their headwords without the marks of their letters on a level of their own.
        assertThat(all.where { it.headwordMarks?.markBases.orEmpty().isNotEmpty() })
            .containsExactly(Language.LITHUANIAN, Language.SLOVAK, Language.ESPERANTO)
        assertThat(all.where { it.headwordMarks?.letterMarks.orEmpty().isNotEmpty() })
            .containsExactly(Language.LITHUANIAN, Language.SLOVENIAN)
        assertThat(all.where { it.frequencySpellings("हूं").isNotEmpty() }).containsExactly(Language.HINDI)
        // Only Kazakh and Kyrgyz look a word up again with their letters keyed when it keeps some of them and loses
        // others, each with its own letters.
        assertThat(all.where { it.keyedSpelling("Қазакстан", 3) != null }).containsExactly(Language.KAZAKH)
        assertThat(all.where { it.keyedSpelling("Үйлорду", 2) != null }).containsExactly(Language.KAZAKH, Language.KYRGYZ)
        assertThat(all.where { !it.keepsWrittenLetters("оң", "он") }).containsExactly(Language.KAZAKH, Language.KYRGYZ)
    }

    @Test
    fun `every Latin- and Cyrillic-script language but Vietnamese folds marks, no other`() {
        assertThat(all.where { it.foldsMarks }).containsExactly(
            Language.ENGLISH, Language.SPANISH, Language.FRENCH, Language.GERMAN, Language.ITALIAN, Language.PORTUGUESE,
            Language.POLISH, Language.TURKISH, Language.LATVIAN, Language.DUTCH, Language.CZECH, Language.INDONESIAN,
            Language.MALAY, Language.FILIPINO, Language.RUSSIAN, Language.CROATIAN, Language.SERBIAN, Language.UKRAINIAN,
            Language.BELARUSIAN, Language.KAZAKH, Language.KYRGYZ, Language.SWEDISH, Language.NORWEGIAN, Language.DANISH,
            Language.ROMANIAN, Language.LATIN, Language.LITHUANIAN, Language.SLOVAK, Language.SLOVENIAN, Language.ESPERANTO,
            Language.ALBANIAN, Language.SWAHILI, Language.WELSH, Language.IRISH, Language.SCOTTISH_GAELIC, Language.FINNISH,
            Language.HUNGARIAN, Language.ESTONIAN, Language.AZERBAIJANI, Language.UZBEK, Language.BULGARIAN,
            Language.MACEDONIAN, Language.MONGOLIAN,
        )
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
    fun `look-alikes are fixed only in Cyrillic and Greek text, the Serbian j and the Latin i only where they have a twin`() {
        assertThat(all.where { it.ocrWord("\u043co\u0440e") != "\u043co\u0440e" })
            .containsExactly(
                Language.RUSSIAN, Language.SERBIAN, Language.UKRAINIAN, Language.BELARUSIAN, Language.KAZAKH, Language.KYRGYZ,
                Language.BULGARIAN, Language.MACEDONIAN, Language.MONGOLIAN,
            )
        assertThat(all.where { it.ocrWord("j\u043e\u0448") != "j\u043e\u0448" })
            .containsExactly(Language.SERBIAN, Language.MACEDONIAN)
        // Macedonian writes Latin S and the letters of other Cyrillic languages it reads for Ѕ, Ѓ and Ѝ as those.
        assertThat(all.where { it.ocrWord("Sвезда") != "Sвезда" }).containsExactly(Language.MACEDONIAN)
        assertThat(all.where { it.ocrWord("ґавол") != "ґавол" }).containsExactly(Language.MACEDONIAN)
        assertThat(all.where { it.ocrWord("й") != "й" }).containsExactly(Language.MACEDONIAN)
        // Only Macedonian tries a word from OCR that finds nothing with an Л read for Љ.
        assertThat(all.where { it.misreadSpellings("лубов").isNotEmpty() }).containsExactly(Language.MACEDONIAN)
        // A word of one letter has no other one to show its script.
        assertThat(all.where { it.ocrWord("y") != "y" })
            .containsExactly(
                Language.RUSSIAN, Language.SERBIAN, Language.UKRAINIAN, Language.BELARUSIAN, Language.GREEK, Language.KAZAKH,
                Language.KYRGYZ, Language.BULGARIAN, Language.MACEDONIAN, Language.MONGOLIAN,
            )
        assertThat(all.where { it.ocrWord("i") != "i" })
            .containsExactly(Language.UKRAINIAN, Language.BELARUSIAN, Language.GREEK, Language.KAZAKH)
        // Kazakh writes Latin h and the schwa as һ and ә.
        assertThat(all.where { it.ocrWord("шаhар") != "шаhар" }).containsExactly(Language.KAZAKH)
        assertThat(all.where { it.ocrWord("\u0259лем") != "\u0259лем" }).containsExactly(Language.KAZAKH)
        // Kyrgyz writes the barred o as ө.
        assertThat(all.where { it.ocrWord("к\u0275л") != "к\u0275л" }).containsExactly(Language.KYRGYZ)
        assertThat(all.where { it.ocrWord("TOP") != "TOP" }).isEmpty()
        // Swedish, Norwegian and Danish write the a with a hook or a circumflex that ML Kit reads for å as å;
        // Norwegian and Danish, which have no ä, its ä too.
        assertThat(all.where { it.ocrWord("pả") != "pả" })
            .containsExactly(Language.SWEDISH, Language.NORWEGIAN, Language.DANISH)
        assertThat(all.where { it.ocrWord("gâ") != "gâ" })
            .containsExactly(Language.SWEDISH, Language.NORWEGIAN, Language.DANISH)
        assertThat(all.where { it.ocrWord("pä") != "pä" }).containsExactly(Language.NORWEGIAN, Language.DANISH)
        // Lithuanian writes the u with a diaeresis, the e with an acute and the i with a dot below that ML Kit reads for
        // ū, ė and į as those.
        assertThat(all.where { it.ocrWord("büti") != "büti" }).containsExactly(Language.LITHUANIAN)
        assertThat(all.where { it.ocrWord("upé") != "upé" }).containsExactly(Language.LITHUANIAN)
        assertThat(all.where { it.ocrWord("ịvykis") != "ịvykis" }).containsExactly(Language.LITHUANIAN)
        // Esperanto, which writes none of š, ǔ and ġ, reads them as the hat letters engines give them for.
        assertThat(all.where { it.ocrWord("šipo") != "šipo" }).containsExactly(Language.ESPERANTO)
        assertThat(all.where { it.ocrWord("ankaǔ") != "ankaǔ" }).containsExactly(Language.ESPERANTO)
        assertThat(all.where { it.ocrWord("ġi") != "ġi" }).containsExactly(Language.ESPERANTO)
        // Albanian, which has no ě, writes it as the ë ML Kit reads it for.
        assertThat(all.where { it.ocrWord("shtěpi") != "shtěpi" }).containsExactly(Language.ALBANIAN)
        // A Latin T before a Greek omicron.
        assertThat(all.where { it.ocrWord("T\u03bf") != "T\u03bf" }).containsExactly(Language.GREEK)
        // Only Serbian, written in both scripts, decides by the page: a lone y on a page of nothing else stays Latin.
        assertThat(all.where { it.ocrLines(listOf(listOf("y"))) != listOf(listOf(it.ocrWord("y"))) }).containsExactly(Language.SERBIAN)
    }

    @Test
    fun `only Georgian, Armenian, Arabic, Persian, Hebrew, Swedish, Danish, Norwegian, Finnish and Tamil consider more characters from the aim than Yomitan's 16 by default`() {
        assertThat(all.where { it.defaultScanLength != 16 }).containsExactly(
            Language.GEORGIAN, Language.ARMENIAN, Language.ARABIC, Language.PERSIAN, Language.HEBREW, Language.SWEDISH,
            Language.DANISH, Language.NORWEGIAN, Language.FINNISH, Language.TAMIL,
        )
    }

    @Test
    fun `ocr quotes take the guillemets only where the language pairs them, a straight quote the language's own`() {
        val guillemets = all.where { support -> support.quotePairs.let { '»' in it.keys + it.values } }
        assertThat(guillemets).containsAtLeast(Language.DANISH, Language.SLOVENIAN, Language.NORWEGIAN, Language.RUSSIAN)
        assertThat(guillemets).containsNoneOf(Language.ENGLISH, Language.SWEDISH, Language.LITHUANIAN, Language.SLOVAK)
        assertThat(all.where { it.ocrQuotes(listOf(listOf(">>a!<<"))).single().single() != ">>a!<<" })
            .containsExactlyElementsIn(guillemets)
        // A straight quote after ! with no quote open stays in every language.
        assertThat(all.where { it.ocrQuotes(listOf(listOf("a!\""))).single().single() != "a!\"" }).isEmpty()
        // After an English quote it closes it wherever “ opens a quote.
        assertThat(all.where { it.ocrQuotes(listOf(listOf("“a!\""))).single().single() == "“a!”" })
            .containsExactlyElementsIn(all.where { it.quotePairs['“'] == '”' })
        // At a word's start with no quote open it is the language's first opening quote wherever it pairs quotation
        // marks: not in Japanese, whose quotes are brackets.
        val opens = all.where { it.ocrQuotes(listOf(listOf("\"a"))).single().single() != "\"a" }
        assertThat(opens).containsExactlyElementsIn(all.where { support -> support.quotePairs.values.any { it in "»«”“’‘" } })
        assertThat(opens).doesNotContain(Language.JAPANESE)
        assertThat(Language.GERMAN.support.ocrQuotes(listOf(listOf("\"a\"")))).containsExactly(listOf("„a“"))
    }

    @Test
    fun `only Arabic, Persian and Hebrew run right to left, Arabic and Persian end a sentence at the arabic question mark`() {
        assertThat(all.where { it.rightToLeft }).containsExactly(Language.ARABIC, Language.PERSIAN, Language.HEBREW)
        assertThat(all.where { it.endsSentence("هل؟ نعم", "هل".length) }).containsExactly(Language.ARABIC, Language.PERSIAN)
        assertThat(all.where { it.endsSentence("كتب، ثم", "كتب".length) }).isEmpty()
        assertThat(all.where { it.ocrWord("کتاب") != "کتاب" }).containsExactly(Language.ARABIC)
        assertThat(all.where { it.ocrWord("كتاب") != "كتاب" }).containsExactly(Language.PERSIAN)
        assertThat(periodEnding.where { !it.endsSentence("قال د. أحمد", "قال د".length) }).containsExactly(Language.ARABIC)
    }

    @Test
    fun `only Arabic, Persian and Hebrew wrap their anki markers right to left, find recordings with marks, drop forms in other scripts`() {
        assertThat(all.where { it.rightToLeftMarkers.isNotEmpty() }).containsExactly(Language.ARABIC, Language.PERSIAN, Language.HEBREW)
        // Persian's reading is a Latin romanization.
        assertThat(all.where { "reading" in it.rightToLeftMarkers }).containsExactly(Language.ARABIC, Language.HEBREW)
        assertThat(all.where { it.recordingMarks.isNotEmpty() }).containsExactly(Language.ARABIC, Language.PERSIAN, Language.HEBREW)
        assertThat(all.where { it.headwordMarks?.formsInScript == true })
            .containsExactly(Language.ARABIC, Language.PERSIAN, Language.HEBREW)
        // Norwegian asks Bokmål's item as well.
        assertThat(all.where { it.linguaLibreItems != listOf(it.wikidataId) && it.language != Language.CROATIAN && it.language != Language.SERBIAN })
            .containsExactly(Language.NORWEGIAN)
    }

    @Test
    fun `only Hebrew joins a word at the maqaf, the gershayim, the geresh and the ascii quote between letters`() {
        assertThat(spaced.where { it.wordStartOffset("בית־", "ספר") != 0 }).containsExactly(Language.HEBREW)
        assertThat(spaced.where { it.wordStartOffset("צה״", "ל") != 0 }).containsExactly(Language.HEBREW)
        assertThat(spaced.where { it.wordStartOffset("ג׳", "ירפה") != 0 }).containsExactly(Language.HEBREW)
        assertThat(spaced.where { it.wordStartOffset("צה\"", "ל") != 0 }).containsExactly(Language.HEBREW)
        assertThat(spaced.where { it.wordStartOffset("say\"", "hi") != 0 }).containsExactly(Language.HEBREW)
        // The apostrophe joins words everywhere.
        assertThat(spaced.where { it.wordStartOffset("ג'", "ירפה") == 0 }).isEmpty()
    }

    @Test
    fun `only Uzbek joins a word at the quote mark, the grave and the acute typed for its apostrophes`() {
        assertThat(spaced.where { it.wordStartOffset("so‘", "z") != 0 }).containsExactly(Language.UZBEK)
        assertThat(spaced.where { it.wordStartOffset("so`", "z") != 0 }).containsExactly(Language.UZBEK)
        assertThat(spaced.where { it.wordStartOffset("ta´", "til") != 0 }).containsExactly(Language.UZBEK)
    }

    @Test
    fun `only Armenian ends a sentence at its full stop and the colon, and goes on at a period`() {
        assertThat(all.where { it.endsSentence("Այո։ Ոչ", "Այո".length) }).containsExactly(Language.ARMENIAN)
        assertThat(all.where { it.endsSentence("Yes: no", "Yes".length) }).containsExactly(Language.ARMENIAN)
        assertThat(all.where { it.endsSentence("at 10:30", "at 10".length) }).isEmpty()
        assertThat(spaced.where { !it.endsSentence("Run. Then", "Run".length) }).containsExactly(Language.ARMENIAN)
    }

    @Test
    fun `only Armenian keeps its marks inside the aimed word and keys headwords without them`() {
        assertThat(spaced.where { it.wordStartOffset("ինչո՞", "ւ") != 0 }).containsExactly(Language.ARMENIAN)
        assertThat(all.where { it.headwordMarks?.marks?.contains('\u055E') == true }).containsExactly(Language.ARMENIAN)
    }

    @Test
    fun `scripts written without spaces shorten the text by letters`() {
        assertThat(all.where { it.searchResolution == SearchResolution.LETTER })
            .containsExactly(Language.JAPANESE, Language.CHINESE, Language.KOREAN, Language.THAI, Language.KHMER)
        assertThat(all.where { it.wordSeparator.isEmpty() })
            .containsExactly(Language.JAPANESE, Language.CHINESE, Language.THAI, Language.KHMER)
    }

    @Test
    fun `a line end recognition gives no separator takes the word separator in every language but Korean`() {
        assertThat(all.where { it.lineEndSeparator != it.wordSeparator }).containsExactly(Language.KOREAN)
        assertThat(Language.KOREAN.support.lineEndSeparator).isEmpty()
    }
}
