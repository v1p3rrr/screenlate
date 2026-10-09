package com.vpr.screenlate.core.anki.note

/**
 * Field templates recommended by the authors of popular mining note types, keyed by normalized field name. Fields
 * a preset does not list fall back to [FieldTemplate.guessField].
 */
internal object NoteTypePresets {

    private class Preset(val name: Regex, val requiredFields: Set<String>, val fields: Map<String, String>)

    /** Senren (github.com/BrenoAqua/Senren, docs/yomitan.md), current and earlier field names. */
    private val SENREN = Preset(
        name = Regex("senren", RegexOption.IGNORE_CASE),
        requiredFields = setOf("word", "sentence"),
        fields = mapOf(
            "word" to "{expression}",
            "reading" to "{reading}",
            "sentence" to """<span class="group">{cloze-prefix}<span class="highlight">{cloze-body}</span>{cloze-suffix}</span>""",
            "sentencefurigana" to """<span class="group">{sentence-furigana}</span>""",
            "selectiontext" to "{popup-selection-text}",
            "wordaudio" to "{audio}",
            "picture" to "{screenshot}",
            "glossary" to "{glossary}",
            "pitchaccents" to "{pitch-accents}",
            "pitchpositions" to "{pitch-accent-positions}",
            "pitchposition" to "{pitch-accent-positions}",
            "pitchcategories" to "{pitch-accent-categories}",
            "pitch" to "{pitch-accent-categories}",
            "frequencies" to "{frequencies}",
            "frequency" to "{frequencies}",
            "freqsort" to "{frequency-harmonic-rank}",
            "miscinfo" to "{document-title}",
            // Senren leaves the sentence translation to the user; this app can fill it.
            "sentencetranslation" to "{sentence-translation}",
            "sentenceeng" to "{sentence-translation}",
            // Left empty on purpose: card type switches and fields filled by other tools.
            "definition" to "",
            "sentencecard" to "",
            "audiocard" to "",
            "notes" to "",
            "hint" to "",
            "sentenceaudio" to "",
            "dictionarypreference" to "",
        ),
    )

    /** Lapis (github.com/donkuri/lapis, README "Yomitan" table). */
    private val LAPIS = Preset(
        name = Regex("lapis", RegexOption.IGNORE_CASE),
        requiredFields = setOf("expression", "sentence"),
        fields = mapOf(
            "expression" to "{expression}",
            "expressionfurigana" to "{furigana-plain}",
            "expressionreading" to "{reading}",
            "expressionaudio" to "{audio}",
            "selectiontext" to "{popup-selection-text}",
            "maindefinition" to "{glossary-first}",
            "sentence" to "{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}",
            "picture" to "{screenshot}",
            "glossary" to "{glossary}",
            "pitchposition" to "{pitch-accent-positions}",
            "pitchcategories" to "{pitch-accent-categories}",
            "frequency" to "{frequencies}",
            "freqsort" to "{frequency-harmonic-rank}",
            "miscinfo" to "{document-title}",
            "definitionpicture" to "",
            "sentencefurigana" to "",
            "sentenceaudio" to "",
            "hint" to "",
            "iswordandsentencecard" to "",
            "isclickcard" to "",
            "issentencecard" to "",
            "isaudiocard" to "",
        ),
    )

    private val PRESETS = listOf(SENREN, LAPIS)

    /** The preset for a note type whose name and fields match, as normalized field name → template. */
    fun find(modelName: String, fieldNames: List<String>): Map<String, String>? {
        val normalized = fieldNames.map { it.normalizedFieldName() }.toSet()
        return PRESETS.firstOrNull { it.name.containsMatchIn(modelName) && normalized.containsAll(it.requiredFields) }
            ?.fields
    }
}
