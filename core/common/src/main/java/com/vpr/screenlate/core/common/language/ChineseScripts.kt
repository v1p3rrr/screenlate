package com.vpr.screenlate.core.common.language

/** Simplified and Traditional Chinese characters, from [ChineseScriptTable]. */
internal object ChineseScripts {
    const val SIMPLIFIED = "zh-Hans"
    const val TRADITIONAL = "zh-Hant"

    /** Spellings [convert] gives at most: one per likely form of the characters with several. */
    const val MAX_SPELLINGS = 4

    /** Forms of one character [convert] tries, the most common first. */
    private const val FORMS_PER_CHARACTER = 2

    private val simplifiedOnly: Set<Int> by lazy { ChineseScriptTable.SIMPLIFIED_ONLY.codePoints().toArray().toSet() }
    private val traditionalOnly: Set<Int> by lazy { ChineseScriptTable.TRADITIONAL_ONLY.codePoints().toArray().toSet() }
    private val traditionalForms: Map<Int, IntArray> by lazy { forms(ChineseScriptTable.TRADITIONAL_FORMS) }
    private val simplifiedForms: Map<Int, IntArray> by lazy { forms(ChineseScriptTable.SIMPLIFIED_FORMS) }

    /**
     * The script most of [text]'s characters belong to only: [SIMPLIFIED], [TRADITIONAL], or null when there are as
     * many of each (none in text written alike in both).
     */
    fun scriptOf(text: String): String? {
        var simplified = 0
        var traditional = 0
        text.codePoints().forEach {
            if (it in simplifiedOnly) simplified++ else if (it in traditionalOnly) traditional++
        }
        return when {
            simplified > traditional -> SIMPLIFIED
            traditional > simplified -> TRADITIONAL
            else -> null
        }
    }

    /**
     * [text] in the script with [tag], character by character: up to [MAX_SPELLINGS] spellings from the likely forms
     * of characters with several (发 → 發, 髮), the most likely first. Characters that belong to the target script
     * already stay. Empty when nothing changes.
     */
    fun convert(text: MappedText, tag: String): List<MappedText> {
        val traditional = tag == TRADITIONAL
        val table = if (traditional) traditionalForms else simplifiedForms
        val keep = if (traditional) traditionalOnly else simplifiedOnly
        val source = text.text
        var spellings = listOf(StringBuilder())
        val ends = ArrayList<Int>(source.length)
        var index = 0
        while (index < source.length) {
            val codePoint = source.codePointAt(index)
            val next = index + Character.charCount(codePoint)
            val found = table[codePoint]?.takeUnless { codePoint in keep }?.take(FORMS_PER_CHARACTER) ?: listOf(codePoint)
            // The spellings share one mapping to the source, so a character's forms must be of one length.
            val forms = found.filter { Character.charCount(it) == Character.charCount(found[0]) }
            spellings = spellings.flatMap { prefix -> forms.map { StringBuilder(prefix).appendCodePoint(it) } }
                .take(MAX_SPELLINGS)
            repeat(Character.charCount(forms[0])) { ends += next }
            index = next
        }
        val mapping = ends.toIntArray()
        return spellings.map { it.toString() }.filter { it != source }.distinct()
            .map { text.then(MappedText(it, mapping)) }
    }

    /** "发發髮 万萬" → {发: [發, 髮], 万: [萬]}. */
    private fun forms(groups: String): Map<Int, IntArray> = groups.split(' ').associate { group ->
        val codePoints = group.codePoints().toArray()
        codePoints[0] to codePoints.copyOfRange(1, codePoints.size)
    }
}
