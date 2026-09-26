package com.vpr.screenlate.core.anki.note

/**
 * Field templates with `{marker}` placeholders, a subset of Yomitan's Anki markers.
 *
 * Unknown markers are left in place so a typo is visible on the card instead of silently producing an empty
 * field.
 */
object FieldTemplate {
    // Android's ICU regex needs the closing brace escaped, unlike the JVM's.
    private val MARKER = Regex("""\{([\p{L}\p{N}_-]+)\}""")

    /** Markers offered in the settings, in display order. `glossary-<dictionary>` is added per dictionary. */
    val MARKERS = listOf(
        "expression",
        "reading",
        "furigana",
        "furigana-plain",
        "glossary",
        "glossary-first",
        "sentence",
        "cloze-prefix",
        "cloze-body",
        "cloze-suffix",
        "pitch-accents",
        "pitch-accent-positions",
        "frequencies",
        "part-of-speech",
        "tags",
        "dictionary",
        "screenshot",
        "audio",
    )

    const val GLOSSARY_PREFIX = "glossary-"

    fun render(template: String, values: Map<String, String>): String =
        MARKER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }

    fun markersIn(template: String): Set<String> = MARKER.findAll(template).map { it.groupValues[1] }.toSet()

    /** Marker name for one dictionary's glossary, stable across dictionary revisions. */
    fun glossaryMarker(dictionaryTitle: String): String =
        GLOSSARY_PREFIX + dictionaryTitle
            .replace(Regex("""\s*[\[(].*?[\])]\s*$"""), "")
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "-")
            .trim('-')

    /**
     * A starting template for a field, guessed from its name (for example `Expression`, `Word`, `Sentence`,
     * `Meaning`). Returns an empty string when nothing fits.
     */
    fun guess(fieldName: String, index: Int): String {
        val name = fieldName.lowercase().replace(Regex("""[\s_-]+"""), "")
        return when {
            name in setOf("expression", "word", "term", "vocab", "vocabulary", "kanji", "front", "target") -> "{expression}"
            name.contains("sentence") ->
                if (listOf("furigana", "translation", "meaning", "audio").any(name::contains)) "" else "{sentence}"
            name.contains("furigana") -> "{furigana}"
            name.contains("reading") || name == "kana" -> "{reading}"
            name.contains("glossary") || name.contains("meaning") || name.contains("definition") || name == "back" ->
                "{glossary}"
            name.contains("pitch") && name.contains("position") -> "{pitch-accent-positions}"
            name.contains("pitch") -> "{pitch-accents}"
            name.contains("freq") -> "{frequencies}"
            name.contains("audio") || name.contains("sound") -> "{audio}"
            name.contains("picture") || name.contains("image") || name.contains("screenshot") -> "{screenshot}"
            index == 0 -> "{expression}"
            else -> ""
        }
    }
}
