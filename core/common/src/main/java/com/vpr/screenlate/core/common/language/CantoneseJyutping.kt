package com.vpr.screenlate.core.common.language

/**
 * Jyutping as typed into a search and as Cantonese dictionaries give it ("nei5 hou2"): the letters that identify a
 * reading and the tone digits after its syllables, so "neihou", "nei hou" and "nei5hou2" all find 你好.
 */
internal object CantoneseJyutping {

    /** The letters a to z of [reading] in lower case, without tones and separators; null for other text. */
    fun key(reading: String): String? = letters(reading)?.let { letters -> String(CharArray(letters.size) { letters[it].letter }) }

    /**
     * See [LanguageSupport.readingMatch]: a tone typed after a syllable counts for the syllable's last letter, which is
     * where the reading has it.
     */
    fun match(query: String, reading: String): Int {
        val typed = letters(query) ?: return 0
        val given = letters(reading) ?: return 0
        if (typed.size != given.size) return 0
        var score = 0
        for (i in typed.indices) {
            val tone = typed[i].tone
            if (tone != UNMARKED) score += if (given[i].tone == tone) 1 else -1
        }
        return score
    }

    /**
     * [reading] in Yale with tone numbers and without spaces, as Wiktionary titles its Cantonese recordings
     * (`Yue-ngo5dei6.ogg` for 我哋 ngo5 dei6); null unless every syllable is Jyutping with its tone.
     */
    fun yale(reading: String): String? {
        val syllables = reading.trim().lowercase().split(WHITESPACE)
        if (syllables.any { it.isEmpty() }) return null
        return buildString {
            for (syllable in syllables) {
                val (initial, final, tone) = SYLLABLE.matchEntire(syllable)?.destructured ?: return null
                append(
                    when (initial) {
                        "z" -> "j"
                        "c" -> "ch"
                        // Jyutping jyu is Yale yu.
                        "j" -> if (final.startsWith("yu")) "" else "y"
                        else -> initial
                    },
                )
                append(if (final == "aa") "a" else final.replace("oe", "eu").replace("eo", "eu"))
                append(tone)
            }
        }
    }

    private class Letter(val letter: Char, var tone: Int)

    /** The letters of [text] with the tones written after their syllables, on each syllable's last letter. */
    private fun letters(text: String): List<Letter>? {
        val letters = ArrayList<Letter>(text.length)
        for (c in text) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' -> letters.add(Letter(c.lowercaseChar(), UNMARKED))
                c in '1'..'6' -> {
                    val last = letters.lastOrNull()?.takeIf { it.tone == UNMARKED } ?: return null
                    last.tone = c - '0'
                }
                c in SEPARATORS -> Unit
                else -> return null
            }
        }
        return letters.takeIf { it.isNotEmpty() }
    }

    private const val UNMARKED = 0

    /** Between syllables or words: spaces, apostrophes, hyphens, commas of phrases. */
    private const val SEPARATORS = " '’-,，"

    private val WHITESPACE = Regex("\\s+")

    /** An initial, a final or a syllabic nasal (m4 唔, ng5 五), and the tone. */
    private val SYLLABLE = Regex("(gw|kw|ng|[bpmfdtnlgkhwzcsj]?)((?:aa|a|e|i|o|u|yu|oe|eo)(?:i|u|m|n|ng|p|t|k)?|m|ng)([1-6])")
}
