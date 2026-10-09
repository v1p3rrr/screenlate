package com.vpr.screenlate.core.anki.note

/**
 * Field templates recommended by the authors of popular mining note types, keyed by normalized field name. Fields
 * a preset does not list fall back to [FieldTemplate.guessField]. Where an author's table names a marker of their
 * own or a hand-picked dictionary, the preset uses this app's nearest marker; a picture field gets the screenshot,
 * and a field for the sentence's translation gets `{sentence-translation}`, which the authors leave to the user.
 */
internal object NoteTypePresets {

    /** [name] null: the fields alone identify the note type. */
    private class Preset(val name: Regex?, val requiredFields: Set<String>, val fields: Map<String, String>)

    private const val BOLD_SENTENCE = "{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}"

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
            "sentence" to BOLD_SENTENCE,
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

    /** Kiku (github.com/youyoumu/kiku, installation page): Lapis' fields and a few more. */
    private val KIKU = Preset(
        name = Regex("kiku", RegexOption.IGNORE_CASE),
        requiredFields = setOf("expression", "sentence"),
        fields = LAPIS.fields + mapOf(
            "sentencefurigana" to "{sentence-furigana-plain}",
            "sentencetranslation" to "{sentence-translation}",
            "relatedexpression" to "",
        ),
    )

    /** JP Mining Note (github.com/Aquafina-water-bottle/jp-mining-note, Yomichan setup and field reference). */
    private val JP_MINING_NOTE = Preset(
        name = Regex("jp[-_ ]?mining[-_ ]?note|jpmn", RegexOption.IGNORE_CASE),
        requiredFields = setOf("word", "sentence", "primarydefinition"),
        fields = mapOf(
            "key" to "{expression}",
            "word" to "{expression}",
            "wordreading" to "{furigana-plain}",
            // {jpmn-primary-definition}: the first dictionary's definitions.
            "primarydefinition" to "{glossary-first}",
            "sentence" to BOLD_SENTENCE,
            "picture" to "{screenshot}",
            "wordaudio" to "{audio}",
            "pagraphs" to "{pitch-accent-graphs}",
            "papositions" to "{pitch-accent-positions}",
            "pasilence" to "[sound:_silence.wav]",
            "wordreadinghiragana" to "{reading}",
            "frequenciesstylized" to "{frequencies}",
            "frequencysort" to "{frequency-harmonic-rank}",
            // The note type's own split of the other dictionaries has no counterpart here.
            "secondarydefinition" to "",
            "extradefinitions" to "",
            "utilitydictionaries" to "",
            "paoverride" to "",
            "paoverridetext" to "",
            "ajtwordpitch" to "",
            "primarydefinitionpicture" to "",
            "sentencereading" to "",
            "altdisplay" to "",
            "altdisplaypasentencecard" to "",
            "additionalnotes" to "",
            "issentencecard" to "",
            "isclickcard" to "",
            "ishovercard" to "",
            "istargetedsentencecard" to "",
            "pashowinfo" to "",
            "patestonlyword" to "",
            "padonottest" to "",
            "paseparatewordcard" to "",
            "paseparatesentencecard" to "",
            "separateclozedeletioncard" to "",
            "hint" to "",
            "hintnothidden" to "",
            "sentenceaudio" to "",
            "yomichanwordtags" to "",
            "comment" to "",
        ),
    )

    /** Kaishi 1.5k (github.com/donkuri/Kaishi): the fields its card templates use; the authors give no table. */
    private val KAISHI = Preset(
        name = Regex("kaishi", RegexOption.IGNORE_CASE),
        requiredFields = setOf("word", "wordmeaning", "sentence"),
        fields = mapOf(
            "word" to "{expression}",
            "wordreading" to "{reading}",
            "wordfurigana" to "{furigana-plain}",
            // The deck's meanings are short glosses.
            "wordmeaning" to "{glossary-first-brief}",
            "wordaudio" to "{audio}",
            "sentence" to BOLD_SENTENCE,
            "sentencefurigana" to "{sentence-furigana-plain}",
            "sentencemeaning" to "{sentence-translation}",
            "picture" to "{screenshot}",
            "pitchaccent" to "{pitch-accents}",
            "sentenceaudio" to "",
            "notes" to "",
            "pitchaccentnotes" to "",
        ),
    )

    /**
     * The Basic Mining Deck (github.com/friedrich-de/Basic-Mining-Deck, archived), known by its fields; its sentence
     * came from the clipboard.
     */
    private val BASIC_MINING_DECK = Preset(
        name = null,
        requiredFields = setOf("word", "reading", "glossary", "sentence", "graph", "sentenceaudio"),
        fields = mapOf(
            "word" to "{expression}",
            "reading" to "{reading}",
            "glossary" to "{glossary-no-dictionary}",
            "sentence" to "{sentence}",
            "picture" to "{screenshot}",
            "audio" to "{audio}",
            "graph" to "{pitch-accent-graphs}",
            "sentenceaudio" to "",
            "hint" to "",
        ),
    )

    /** Presets with a name first, so one that matches by name wins over one known by its fields alone. */
    private val PRESETS = listOf(SENREN, KIKU, LAPIS, JP_MINING_NOTE, KAISHI, BASIC_MINING_DECK)

    /**
     * The preset for a note type, as normalized field name → template: one whose name and fields match, or else one
     * known by its fields alone.
     */
    fun find(modelName: String, fieldNames: List<String>): Map<String, String>? {
        val normalized = fieldNames.map { it.normalizedFieldName() }.toSet()
        return PRESETS.firstOrNull {
            (it.name == null || it.name.containsMatchIn(modelName)) && normalized.containsAll(it.requiredFields)
        }?.fields
    }
}
