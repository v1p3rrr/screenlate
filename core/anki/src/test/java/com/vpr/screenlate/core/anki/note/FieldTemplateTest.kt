package com.vpr.screenlate.core.anki.note

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class FieldTemplateTest {
    @Test
    fun replacesKnownMarkersAndKeepsUnknownOnes() {
        val rendered = FieldTemplate.render(
            "{expression}【{reading}】 {nope}",
            mapOf("expression" to "食べる", "reading" to "たべる"),
        )
        assertThat(rendered).isEqualTo("食べる【たべる】 {nope}")
    }

    @Test
    fun offersPitchMarkersForJapaneseAndTranscriptionsWithADictionary() {
        assertThat(FieldTemplate.markersFor(Language.JAPANESE)).contains("pitch-accents")
        assertThat(FieldTemplate.markersFor(Language.ENGLISH)).doesNotContain("pitch-accents")
        assertThat(FieldTemplate.markersFor(Language.ENGLISH)).contains(FieldTemplate.PHONETIC_TRANSCRIPTIONS)
        assertThat(FieldTemplate.markersFor(Language.ENGLISH, transcriptions = false))
            .doesNotContain(FieldTemplate.PHONETIC_TRANSCRIPTIONS)
        assertThat(FieldTemplate.isKnown(FieldTemplate.PHONETIC_TRANSCRIPTIONS)).isTrue()
    }

    @Test
    fun offersFuriganaMarkersOnlyWhereReadingsGoOverOrIntoWords() {
        val offering = Language.entries.filter { FieldTemplate.markersFor(it).containsAll(FieldTemplate.FURIGANA_MARKERS) }
        assertThat(offering).containsExactly(Language.JAPANESE, Language.CHINESE, Language.RUSSIAN)
        for (language in Language.entries - offering) {
            assertThat(FieldTemplate.markersFor(language)).containsNoneIn(FieldTemplate.FURIGANA_MARKERS)
        }
        // A field set up with one still gets it, and is no typo.
        assertThat(FieldTemplate.FURIGANA_MARKERS.all(FieldTemplate::isKnown)).isTrue()
        // Nor is a field named after one guessed in a language that does not offer it.
        assertThat(FieldTemplate.guessField("Furigana", 1, Language.ENGLISH)).isEmpty()
        assertThat(FieldTemplate.guessField("Furigana", 1, Language.JAPANESE)).isEqualTo("{furigana}")
    }

    @Test
    fun findsMarkers() {
        assertThat(FieldTemplate.markersIn("{sentence}<br>{single-glossary-jitendexorg-2026-08-11}"))
            .containsExactly("sentence", "single-glossary-jitendexorg-2026-08-11")
    }

    @Test
    fun dynamicMarkersUseYomitanNames() {
        assertThat(FieldTemplate.singleGlossaryMarker("Jitendex.org [2026-08-11]"))
            .isEqualTo("single-glossary-jitendexorg-2026-08-11")
        assertThat(FieldTemplate.singleGlossaryMarker("大辞林 第三版（画像のみ）")).isEqualTo("single-glossary-大辞林-第三版画像のみ")
        assertThat(FieldTemplate.singleFrequencyNumberMarker("JPDB")).isEqualTo("single-frequency-number-jpdb")
        assertThat(FieldTemplate.isKnown("single-glossary-jpdb")).isTrue()
        assertThat(FieldTemplate.isKnown("frequency-harmonic-rank")).isTrue()
        assertThat(FieldTemplate.isKnown("nope")).isFalse()
    }

    @Test
    fun guessesLikeYomitanByWholeFieldNames() {
        val fields = listOf(
            "Front", "Reading", "Sentence", "SentenceFurigana", "Meaning", "Word Audio", "Pitch Position",
            "FreqSort", "IsSentenceCard", "Notes",
        )
        assertThat(FieldTemplate.guess("Custom", fields, Language.JAPANESE)).containsExactlyEntriesIn(
            mapOf(
                "Front" to "{expression}",
                "Reading" to "{reading}",
                "Sentence" to "{sentence}",
                "SentenceFurigana" to "{sentence-furigana}",
                "Meaning" to "{glossary}",
                "Word Audio" to "{audio}",
                "Pitch Position" to "{pitch-accent-positions}",
                "FreqSort" to "{frequency-harmonic-rank}",
                "IsSentenceCard" to "",
                "Notes" to "",
            ),
        )
    }

    @Test
    fun senrenPresetKeepsCardSwitchesEmpty() {
        // The owner's Senren version, with the older field names.
        val fields = listOf(
            "word", "reading", "sentence", "sentenceFurigana", "sentenceEng", "picture", "definition", "glossary",
            "wordAudio", "sentenceAudio", "pitchPosition", "pitch", "notes", "hint", "frequency", "freqSort",
            "miscInfo", "sentenceCard", "selectionText", "audioCard", "dictionaryPreference", "wordFuriganaUnused",
        )
        val templates = FieldTemplate.guess("Senren", fields, Language.JAPANESE)

        assertThat(templates["word"]).isEqualTo("{expression}")
        assertThat(templates["sentence"]).contains("<span class=\"highlight\">{cloze-body}</span>")
        assertThat(templates["pitch"]).isEqualTo("{pitch-accent-categories}")
        assertThat(templates["pitchPosition"]).isEqualTo("{pitch-accent-positions}")
        assertThat(templates["frequency"]).isEqualTo("{frequencies}")
        assertThat(templates["freqSort"]).isEqualTo("{frequency-harmonic-rank}")
        assertThat(templates["picture"]).isEqualTo("{screenshot}")
        assertThat(templates["sentenceEng"]).isEqualTo("{sentence-translation}")
        for (flag in listOf("sentenceCard", "audioCard", "definition", "wordFuriganaUnused")) {
            assertThat(templates[flag]).isEmpty()
        }
    }

    @Test
    fun senrenSentenceTranslationGetsTheTranslation() {
        val templates = FieldTemplate.guess("Senren", listOf("Word", "Sentence", "SentenceTranslation"), Language.JAPANESE)

        assertThat(templates["SentenceTranslation"]).isEqualTo("{sentence-translation}")
    }

    @Test
    fun lapisPresetUsesPlainFurigana() {
        val fields = listOf("Expression", "ExpressionFurigana", "ExpressionReading", "MainDefinition", "Sentence", "IsClickCard")
        val templates = FieldTemplate.guess("Lapis", fields, Language.JAPANESE)

        assertThat(templates["ExpressionFurigana"]).isEqualTo("{furigana-plain}")
        assertThat(templates["MainDefinition"]).isEqualTo("{glossary-first}")
        assertThat(templates["Sentence"]).isEqualTo("{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}")
        assertThat(templates["IsClickCard"]).isEmpty()
    }

    @Test
    fun kikuPresetAddsItsFieldsToLapis() {
        val fields = listOf("Expression", "MainDefinition", "Sentence", "SentenceFurigana", "SentenceTranslation", "RelatedExpression", "Picture")
        val templates = FieldTemplate.guess("Kiku", fields, Language.JAPANESE)

        assertThat(templates["MainDefinition"]).isEqualTo("{glossary-first}")
        assertThat(templates["SentenceFurigana"]).isEqualTo("{sentence-furigana-plain}")
        assertThat(templates["SentenceTranslation"]).isEqualTo("{sentence-translation}")
        assertThat(templates["RelatedExpression"]).isEmpty()
        assertThat(templates["Picture"]).isEqualTo("{screenshot}")
        // Lapis keeps the sentence furigana empty, as its authors advise.
        assertThat(FieldTemplate.guess("Lapis", fields, Language.JAPANESE)["SentenceFurigana"]).isEmpty()
    }

    @Test
    fun jpMiningNoteGetsTheNearestMarkers() {
        val fields = listOf(
            "Key", "Word", "WordReading", "PrimaryDefinition", "Sentence", "IsTargetedSentenceCard", "Picture",
            "WordAudio", "PAGraphs", "PAPositions", "PASilence", "WordReadingHiragana", "FrequenciesStylized",
            "FrequencySort", "SecondaryDefinition", "Comment",
        )
        val templates = FieldTemplate.guess("JP Mining Note", fields, Language.JAPANESE)

        assertThat(templates).containsExactlyEntriesIn(
            mapOf(
                "Key" to "{expression}",
                "Word" to "{expression}",
                "WordReading" to "{furigana-plain}",
                "PrimaryDefinition" to "{glossary-first}",
                "Sentence" to "{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}",
                "IsTargetedSentenceCard" to "",
                "Picture" to "{screenshot}",
                "WordAudio" to "{audio}",
                "PAGraphs" to "{pitch-accent-graphs}",
                "PAPositions" to "{pitch-accent-positions}",
                "PASilence" to "[sound:_silence.wav]",
                "WordReadingHiragana" to "{reading}",
                "FrequenciesStylized" to "{frequencies}",
                "FrequencySort" to "{frequency-harmonic-rank}",
                "SecondaryDefinition" to "",
                "Comment" to "",
            ),
        )
    }

    @Test
    fun kaishiFillsItsSpacedFieldNames() {
        val fields = listOf(
            "Word", "Word Reading", "Word Furigana", "Word Meaning", "Word Audio", "Sentence", "Sentence Meaning",
            "Sentence Furigana", "Sentence Audio", "Picture", "Notes", "Pitch Accent", "Pitch Accent Notes",
        )
        val templates = FieldTemplate.guess("Kaishi 1.5k", fields, Language.JAPANESE)

        assertThat(templates["Word Furigana"]).isEqualTo("{furigana-plain}")
        assertThat(templates["Word Meaning"]).isEqualTo("{glossary-first-brief}")
        assertThat(templates["Sentence"]).isEqualTo("{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}")
        assertThat(templates["Sentence Meaning"]).isEqualTo("{sentence-translation}")
        assertThat(templates["Sentence Furigana"]).isEqualTo("{sentence-furigana-plain}")
        assertThat(templates["Pitch Accent"]).isEqualTo("{pitch-accents}")
        assertThat(templates["Pitch Accent Notes"]).isEmpty()
    }

    @Test
    fun basicMiningDeckIsKnownByItsFields() {
        val fields = listOf("Word", "Reading", "Glossary", "Sentence", "Picture", "Audio", "SentenceAudio", "Graph", "Hint")
        val templates = FieldTemplate.guess("Mining", fields, Language.JAPANESE)

        assertThat(templates["Glossary"]).isEqualTo("{glossary-no-dictionary}")
        assertThat(templates["Sentence"]).isEqualTo("{sentence}")
        assertThat(templates["Graph"]).isEqualTo("{pitch-accent-graphs}")
        assertThat(templates["SentenceAudio"]).isEmpty()
        // Without its fields the general rule applies.
        assertThat(FieldTemplate.guess("Mining", listOf("Word", "Glossary"), Language.JAPANESE)["Glossary"]).isEqualTo("{glossary}")
    }

    @Test
    fun chineseNoteTypesAreFilledByTheirFieldNames() {
        val fields = listOf("Simplified", "Traditional", "Pinyin", "English", "Sound", "Classifier", "Pitch")
        assertThat(FieldTemplate.guess("Chinese (Advanced)", fields, Language.CHINESE)).containsExactlyEntriesIn(
            mapOf(
                "Simplified" to "{expression-simplified}",
                "Traditional" to "{expression-traditional}",
                "Pinyin" to "{reading}",
                "English" to "{glossary}",
                "Sound" to "{audio}",
                "Classifier" to "",
                // Pitch accents are Japanese.
                "Pitch" to "",
            ),
        )
        assertThat(FieldTemplate.guess("Pleco", listOf("Hanzi", "Pinyin", "Meaning"), Language.CHINESE))
            .containsExactly("Hanzi", "{expression}", "Pinyin", "{reading}", "Meaning", "{glossary}")
        // The first field is the word; a script in its name chooses the spelling.
        assertThat(FieldTemplate.guess("Custom", listOf("Pinyin", "Hanzi"), Language.CHINESE)["Pinyin"])
            .isEqualTo("{expression}")
        assertThat(FieldTemplate.guess("Custom", listOf("Traditional", "Simplified"), Language.CHINESE)["Traditional"])
            .isEqualTo("{expression-traditional}")
        // Other languages keep these names to themselves.
        assertThat(FieldTemplate.guess("Custom", listOf("Word", "Pinyin", "Traditional"), Language.JAPANESE))
            .containsExactly("Word", "{expression}", "Pinyin", "", "Traditional", "")
        assertThat(FieldTemplate.markersFor(Language.CHINESE)).containsAtLeast("expression-simplified", "expression-traditional")
        assertThat(FieldTemplate.isKnown("expression-traditional")).isTrue()
    }

    @Test
    fun aliasesFillUnknownNoteTypes() {
        val fields = listOf(
            "Front", "Image", "Frequency Sort", "PrimaryDefinition", "Sentence_English", "SentenceEng", "Sentence Meaning",
            "Translation", "Meaning", "Pitch Graph",
        )
        assertThat(FieldTemplate.guess("Custom", fields, Language.JAPANESE)).containsExactlyEntriesIn(
            mapOf(
                "Front" to "{expression}",
                "Image" to "{screenshot}",
                "Frequency Sort" to "{frequency-harmonic-rank}",
                "PrimaryDefinition" to "{glossary-first}",
                "Sentence_English" to "{sentence-translation}",
                "SentenceEng" to "{sentence-translation}",
                "Sentence Meaning" to "{sentence-translation}",
                "Translation" to "",
                "Meaning" to "{glossary}",
                "Pitch Graph" to "{pitch-accent-graphs}",
            ),
        )
    }

    @Test
    fun arabicTextMarkersGetARightToLeftSpanOutsideTheFirstField() {
        val lapis = FieldTemplate.guess("Lapis", listOf("Expression", "ExpressionReading", "Sentence", "MainDefinition"), Language.ARABIC)

        assertThat(lapis).containsExactlyEntriesIn(
            mapOf(
                "Expression" to "{expression}",
                "ExpressionReading" to "<span dir=\"rtl\">{reading}</span>",
                "Sentence" to "<span dir=\"rtl\">{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}</span>",
                "MainDefinition" to "{glossary-first}",
            ),
        )
        // Senren's group: the span goes inside it, around the markers and the highlight between them.
        assertThat(FieldTemplate.guess("Senren", listOf("Word", "Sentence"), Language.ARABIC)["Sentence"]).isEqualTo(
            "<span class=\"group\"><span dir=\"rtl\">{cloze-prefix}<span class=\"highlight\">{cloze-body}</span>" +
                "{cloze-suffix}</span></span>",
        )
        // Guessed fields: the word in a second field gets one, the first field and meanings none.
        val guessed = FieldTemplate.guess("Custom", listOf("Front", "Word", "Meaning", "Sentence"), Language.ARABIC)
        assertThat(guessed["Front"]).isEqualTo("{expression}")
        assertThat(guessed["Word"]).isEqualTo("<span dir=\"rtl\">{expression}</span>")
        assertThat(guessed["Meaning"]).isEqualTo("{glossary}")
        assertThat(guessed["Sentence"]).isEqualTo("<span dir=\"rtl\">{sentence}</span>")
    }

    @Test
    fun persianTextMarkersGetTheSpanButItsLatinReadingDoesNot() {
        val lapis = FieldTemplate.guess("Lapis", listOf("Expression", "ExpressionReading", "Sentence", "MainDefinition"), Language.PERSIAN)

        assertThat(lapis).containsExactlyEntriesIn(
            mapOf(
                "Expression" to "{expression}",
                "ExpressionReading" to "{reading}",
                "Sentence" to "<span dir=\"rtl\">{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}</span>",
                "MainDefinition" to "{glossary-first}",
            ),
        )
        assertThat(FieldTemplate.withMarker("", "reading", Language.PERSIAN, firstField = false)).isEqualTo("{reading}")
        assertThat(FieldTemplate.withMarker("", "expression", Language.PERSIAN, firstField = false))
            .isEqualTo("<span dir=\"rtl\">{expression}</span>")
    }

    @Test
    fun hebrewWordReadingAndSentenceMarkersGetTheSpan() {
        val lapis = FieldTemplate.guess("Lapis", listOf("Expression", "ExpressionReading", "Sentence", "MainDefinition"), Language.HEBREW)

        assertThat(lapis).containsExactlyEntriesIn(
            mapOf(
                "Expression" to "{expression}",
                "ExpressionReading" to "<span dir=\"rtl\">{reading}</span>",
                "Sentence" to "<span dir=\"rtl\">{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}</span>",
                "MainDefinition" to "{glossary-first}",
            ),
        )
        assertThat(FieldTemplate.withMarker("", "expression", Language.HEBREW, firstField = false))
            .isEqualTo("<span dir=\"rtl\">{expression}</span>")
    }

    @Test
    fun theSpanKeepsTagsWholeAndLeavesOtherLanguagesAndSetDirectionsAlone() {
        // A tag opened between the markers and closed after them: each marker on its own.
        assertThat(FieldTemplate.rightToLeft("{cloze-prefix}<b>{cloze-body}{cloze-suffix}</b>", Language.ARABIC)).isEqualTo(
            "<span dir=\"rtl\">{cloze-prefix}</span><b><span dir=\"rtl\">{cloze-body}</span>" +
                "<span dir=\"rtl\">{cloze-suffix}</span></b>",
        )
        // Text between markers parts them; a line break is markup.
        assertThat(FieldTemplate.rightToLeft("{expression} – {sentence}", Language.ARABIC))
            .isEqualTo("<span dir=\"rtl\">{expression}</span> – <span dir=\"rtl\">{sentence}</span>")
        assertThat(FieldTemplate.rightToLeft("{expression}<br>{sentence}", Language.ARABIC))
            .isEqualTo("<span dir=\"rtl\">{expression}<br>{sentence}</span>")
        assertThat(FieldTemplate.rightToLeft("<div dir=\"auto\">{sentence}</div>", Language.ARABIC))
            .isEqualTo("<div dir=\"auto\">{sentence}</div>")
        assertThat(FieldTemplate.rightToLeft("{sentence}", Language.ENGLISH)).isEqualTo("{sentence}")
        assertThat(FieldTemplate.guess("Lapis", listOf("Expression", "Sentence"), Language.JAPANESE)["Sentence"])
            .isEqualTo("{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}")
    }

    @Test
    fun pickedArabicMarkersGoIntoTheSpanTheFieldEndsWith() {
        fun pick(template: String, vararg markers: String) =
            markers.fold(template) { text, marker -> FieldTemplate.withMarker(text, marker, Language.ARABIC, firstField = false) }

        assertThat(pick("", "cloze-prefix", "cloze-body", "cloze-suffix"))
            .isEqualTo("<span dir=\"rtl\">{cloze-prefix}{cloze-body}{cloze-suffix}</span>")
        assertThat(pick("", "expression", "glossary", "sentence"))
            .isEqualTo("<span dir=\"rtl\">{expression}</span>{glossary}<span dir=\"rtl\">{sentence}</span>")
        assertThat(FieldTemplate.withMarker("", "expression", Language.ARABIC, firstField = true)).isEqualTo("{expression}")
        assertThat(FieldTemplate.withMarker("", "expression", Language.ENGLISH, firstField = false)).isEqualTo("{expression}")
        // A span the field ends with but did not open.
        assertThat(pick("<span>a</span>", "sentence")).isEqualTo("<span>a</span><span dir=\"rtl\">{sentence}</span>")
    }
}
