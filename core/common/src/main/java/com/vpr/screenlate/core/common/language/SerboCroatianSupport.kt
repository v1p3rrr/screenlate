package com.vpr.screenlate.core.common.language

import com.vpr.screenlate.core.common.Language

/**
 * Croatian and Serbian, two languages on the Serbo-Croatian dictionaries (sh), which Yomitan, Wiktionary and wordfreq
 * treat as one language: Latin and Cyrillic headwords are full entries each, so either language finds words in both
 * scripts. Each has its own recognition model, voice, flag and settings. Wiktionary's recordings are mostly Serbian
 * and titled in Cyrillic (`Sr-кућа.flac`); they name no regions.
 */
abstract class SerboCroatianSupport : SpacedLanguageSupport() {
    override val languageTag get() = language.code
    override val localeLanguages = setOf("sh", "hr", "sr", "bs", "cnr")

    /** Wiktionary has recordings of more words than Lingua Libre. */
    override val defaultAudioSources = listOf("WIKTIONARY", "LINGUA_LIBRE", "TEXT_TO_SPEECH")

    /** wordfreq writes the language in Latin letters only (it transliterates Serbian Cyrillic). */
    override fun frequencySpellings(word: String): List<String> = listOfNotNull(SerboCroatianScripts.toCyrillic(word))

    /** Serbian „…“, Croatian »…« (and „…”, whose ” closes in every language), ‚…‘ inside. */
    override val quotePairs = mapOf('„' to '“', '»' to '«', '‚' to '‘', '(' to ')', '[' to ']')

    /** Ordinals in digits take a period: "5. maja", "1918. godine". */
    override val ordinalPeriod = true

    /**
     * Each word's Cyrillic entry is also listed as a form of its Latin one (ма̏чка → mačka), which adds nothing to the
     * Cyrillic entry itself.
     */
    override val ignoredFormTags = setOf("Cyrillic")

    /** Accents of the readings Wiktionary writes into form headwords (ку̏ћа), on vowels and syllabic r. */
    override val headwordMarks = HeadwordMarks(marks = "̀́̂̄̏̑", bases = "aeiourаеиоур")

    /**
     * "god." and "g." are godina, "st." stoljeće or stoleće, "v." vijek or vek, "br." broj, "str." strana, "pr." prije
     * or pre (pr. Kr.); Cyrillic the same. "itd.", "i sl." and "i dr." (and the like) usually end a sentence and are
     * left out, though dr. is also doktor before a name, which is kept.
     */
    override val abbreviations = setOf(
        "npr", "tj", "tzv", "odn", "god", "g", "st", "v", "str", "br", "dr", "mr", "prof", "doc", "ing", "ul", "sv",
        "pr", "tel",
        "нпр", "тј", "тзв", "одн", "год", "г", "ст", "в", "стр", "бр", "др", "мр", "проф", "доц", "инж", "ул", "св",
        "пр", "тел",
    )

    /** Krista and Христа after pr. and пр. ("pr. Kr. godine"); "Kr." before a capital ends the sentence. */
    override val abbreviationsBeforeLowercase = setOf("kr", "хр")

    /** The letters of "p. n. e." and "n. e." (before and in the Common Era), and of "п. н. е.". */
    override val pairedLetters = setOf("p", "n", "e", "п", "н", "е")
}

/** Croatian, written in Latin letters. */
object CroatianSupport : SerboCroatianSupport() {
    override val language = Language.CROATIAN
    override val wikidataId = "Q6654"
    override val glyph = "Đ"
    override val ocrScript = OcrScript.LATIN
    override val fontSample = "Gojazni đačić s biciklom drži hmelj i finu vatu u džepu nošnje."
    override val translationSample = "Danas je lijepo vrijeme. Hoćemo li u šetnju?"
    override val audioTestWord = "voda"
    override val pronunciationSample = "voda → /ʋǒda/"
    override val flagCountries = listOf("hr")

    /**
     * Croatian recordings (`Hr-kuća.ogg`) are few, so Serbian ones of the word follow (`Sr-кућа.flac`); a word Serbian
     * spells otherwise (mlijeko, Serbian млеко) has none of those.
     */
    override fun recordings(term: String, reading: String): List<RecordingTitle> =
        listOfNotNull(RecordingTitle("hr", term), SerboCroatianScripts.toCyrillic(term)?.let { RecordingTitle("sr", it) })

    /** Croatian has a single Lingua Libre recording; Serbian speakers' follow. */
    override val linguaLibreItems = listOf(wikidataId, SERBIAN_WIKIDATA_ID)

    /** The accents of readings (kȕća) are combining marks after the letters. */
    override val systemFonts = LATIN_SYSTEM_FONTS.copy(unicodeRange = "U+0000-024F, U+0300-036F, U+1E00-1EFF, U+2000-206F")
    override val fontSubsets = LATIN_EXT_FONT_SUBSETS
    override val fontLetters = "čćđšž"
}

/**
 * Serbian, written in Cyrillic and in Latin letters: the dictionaries hold both spellings, the voice reads the word in
 * Cyrillic, and the recognition model is Cyrillic unless a Latin one is chosen.
 */
object SerbianSupport : SerboCroatianSupport() {
    override val language = Language.SERBIAN
    override val wikidataId = SERBIAN_WIKIDATA_ID
    override val glyph = "Ћ"
    override val ocrScript = OcrScript.CYRILLIC
    override val fontSample = "Гојазни ђачић с бициклом држи хмељ и фину вату у џепу ношње."
    override val translationSample = "Данас је лепо време. Хоћемо ли у шетњу?"
    override val audioTestWord = "вода"
    override val pronunciationSample = "вода → /ʋǒda/"
    override val flagCountries = listOf("rs")
    override val systemFonts = CYRILLIC_SYSTEM_FONTS

    /** Grammar lines and Latin text need č, ć, đ. */
    override val fontSubsets = setOf("latin", "latin-ext", "cyrillic")
    override val fontLetters = "čćđšžабвгдђежзијклљмнњопрстћуфхцчџш"

    /** `Sr-кућа.flac`, then the few titled in Latin letters (`Sr-grad.ogg`). */
    override fun recordings(term: String, reading: String): List<RecordingTitle> =
        listOf(SerboCroatianScripts.toCyrillic(term) ?: term, SerboCroatianScripts.toLatin(term) ?: term)
            .distinct()
            .map { RecordingTitle("sr", it) }

    /** Serbian Lingua Libre recordings are titled in Latin letters (`LL-Q9299 (srp)-Speaker-voda.wav`). */
    override fun linguaLibreTitle(term: String): String = SerboCroatianScripts.toLatin(term) ?: term

    /** The voice is given Latin words in Cyrillic, which it reads for certain. */
    override fun speechText(term: String, reading: String): String {
        val text = reading.ifEmpty { term }
        return SerboCroatianScripts.toCyrillic(text) ?: text
    }

    /** Text in Latin letters goes to the services as Serbian in Latin letters, where they tell the scripts apart. */
    override fun translationTag(text: String): String? = "sr-Latn".takeIf { scriptBalance(text) < 0 }

    /** Serbian ј looks like the Latin j, which the Cyrillic models also know. */
    override fun ocrWord(text: String): String = CyrillicLookAlikes.fix(text, SERBIAN_LOOK_ALIKES)

    /**
     * Latin look-alikes become Cyrillic only on a line whose letters are mostly Cyrillic, or, on a line of look-alikes
     * alone, when the page's are: a line in Latin letters keeps its one-letter words (i, a, u, o). On a Cyrillic line a
     * word of look-alikes alone (Ја, је, Јој, which engines read as Latin) becomes Cyrillic too, except a word in
     * capitals outside a heading in capitals (a line with Cyrillic letters and no lowercase ones): in running text and
     * on buttons OK, TOP or TEXT are Latin abbreviations and names.
     */
    override fun ocrLines(lines: List<List<String>>): List<List<String>> {
        val balances = lines.map { line -> line.sumOf(::scriptBalance) }
        val page = balances.sum()
        return lines.mapIndexed { index, line ->
            if ((balances[index].takeIf { it != 0 } ?: page) <= 0) return@mapIndexed line
            val heading = line.none { word -> word.any(Char::isLowerCase) } &&
                line.any { word -> word.any(::isCyrillicLetter) }
            line.map { word ->
                if (isInCapitals(word) && !heading) {
                    CyrillicLookAlikes.fix(word, SERBIAN_LOOK_ALIKES)
                } else {
                    CyrillicLookAlikes.fixInScript(word, SERBIAN_LOOK_ALIKES)
                }
            }
        }
    }

    private fun isInCapitals(word: String): Boolean {
        val letters = word.filter(Char::isLetter)
        return letters.length > 1 && letters.all(Char::isUpperCase)
    }

    private fun isCyrillicLetter(c: Char) =
        c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.CYRILLIC

    /** Cyrillic letters less Latin letters that look like no Cyrillic one. */
    private fun scriptBalance(text: String): Int = text.sumOf { c ->
        when {
            !c.isLetter() -> 0
            isCyrillicLetter(c) -> 1
            CyrillicLookAlikes.isLookAlike(c) || c in SERBIAN_LOOK_ALIKES -> 0
            else -> -1
        }
    }

    private val SERBIAN_LOOK_ALIKES = mapOf('j' to 'ј', 'J' to 'Ј')
}

private const val SERBIAN_WIKIDATA_ID = "Q9299"
