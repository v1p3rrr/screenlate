package com.vpr.screenlate.core.common.language

/**
 * Regions named by the tags of a phonetic transcription in a Wiktionary dictionary: flag emoji, which every
 * language's dictionary uses, and accent names that [LanguageSupport.transcriptionRegionNames] maps per language.
 * Regions are ISO 3166-1 alpha-2 codes in lower case.
 */
object TranscriptionRegions {

    /** The regions of [tags] in their order, each once; tags that name no region are skipped. */
    fun of(tags: List<String>, names: Map<String, String>): List<String> =
        tags.mapNotNull { ofFlag(it) ?: names[it] }.distinct()

    /**
     * The country of a flag emoji: a pair of regional indicators (🇺🇸 → `us`), or a subdivision flag, which counts as
     * its country (England's 🏴 tag sequence `gbeng` → `gb`). Null for anything else, a flag with more after it too.
     */
    fun ofFlag(tag: String): String? = regionalIndicators(tag) ?: subdivision(tag)

    private fun regionalIndicators(tag: String): String? {
        val points = tag.codePoints().toArray()
        if (points.size != 2 || points.any { it !in REGIONAL_A..REGIONAL_Z }) return null
        return points.joinToString("") { (it - REGIONAL_A + 'a'.code).toChar().toString() }
    }

    private fun subdivision(tag: String): String? {
        val points = tag.codePoints().toArray()
        if (points.size < 5 || points.first() != BLACK_FLAG || points.last() != CANCEL_TAG) return null
        val tags = points.copyOfRange(1, points.size - 1)
        if (tags.any { it !in TAG_A..TAG_Z && it !in TAG_0..TAG_9 }) return null
        val country = tags.take(2)
        if (country.any { it !in TAG_A..TAG_Z }) return null
        return country.joinToString("") { (it - TAG_A + 'a'.code).toChar().toString() }
    }

    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF
    private const val BLACK_FLAG = 0x1F3F4
    private const val TAG_0 = 0xE0030
    private const val TAG_9 = 0xE0039
    private const val TAG_A = 0xE0061
    private const val TAG_Z = 0xE007A
    private const val CANCEL_TAG = 0xE007F
}
