package com.vpr.screenlate.core.anki.note

import com.google.common.truth.Truth.assertThat
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
        assertThat(FieldTemplate.guess("Custom", fields)).containsExactlyEntriesIn(
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
        val templates = FieldTemplate.guess("Senren", fields)

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
        val templates = FieldTemplate.guess("Senren", listOf("Word", "Sentence", "SentenceTranslation"))

        assertThat(templates["SentenceTranslation"]).isEqualTo("{sentence-translation}")
    }

    @Test
    fun lapisPresetUsesPlainFurigana() {
        val fields = listOf("Expression", "ExpressionFurigana", "ExpressionReading", "MainDefinition", "Sentence", "IsClickCard")
        val templates = FieldTemplate.guess("Lapis", fields)

        assertThat(templates["ExpressionFurigana"]).isEqualTo("{furigana-plain}")
        assertThat(templates["MainDefinition"]).isEqualTo("{glossary-first}")
        assertThat(templates["Sentence"]).isEqualTo("{cloze-prefix}<b>{cloze-body}</b>{cloze-suffix}")
        assertThat(templates["IsClickCard"]).isEmpty()
    }

    @Test
    fun kikuPresetAddsItsFieldsToLapis() {
        val fields = listOf("Expression", "MainDefinition", "Sentence", "SentenceFurigana", "SentenceTranslation", "RelatedExpression", "Picture")
        val templates = FieldTemplate.guess("Kiku", fields)

        assertThat(templates["MainDefinition"]).isEqualTo("{glossary-first}")
        assertThat(templates["SentenceFurigana"]).isEqualTo("{sentence-furigana-plain}")
        assertThat(templates["SentenceTranslation"]).isEqualTo("{sentence-translation}")
        assertThat(templates["RelatedExpression"]).isEmpty()
        assertThat(templates["Picture"]).isEqualTo("{screenshot}")
        // Lapis keeps the sentence furigana empty, as its authors advise.
        assertThat(FieldTemplate.guess("Lapis", fields)["SentenceFurigana"]).isEmpty()
    }

    @Test
    fun jpMiningNoteGetsTheNearestMarkers() {
        val fields = listOf(
            "Key", "Word", "WordReading", "PrimaryDefinition", "Sentence", "IsTargetedSentenceCard", "Picture",
            "WordAudio", "PAGraphs", "PAPositions", "PASilence", "WordReadingHiragana", "FrequenciesStylized",
            "FrequencySort", "SecondaryDefinition", "Comment",
        )
        val templates = FieldTemplate.guess("JP Mining Note", fields)

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
        val templates = FieldTemplate.guess("Kaishi 1.5k", fields)

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
        val templates = FieldTemplate.guess("Mining", fields)

        assertThat(templates["Glossary"]).isEqualTo("{glossary-no-dictionary}")
        assertThat(templates["Sentence"]).isEqualTo("{sentence}")
        assertThat(templates["Graph"]).isEqualTo("{pitch-accent-graphs}")
        assertThat(templates["SentenceAudio"]).isEmpty()
        // Without its fields the general rule applies.
        assertThat(FieldTemplate.guess("Mining", listOf("Word", "Glossary"))["Glossary"]).isEqualTo("{glossary}")
    }

    @Test
    fun aliasesFillUnknownNoteTypes() {
        val fields = listOf(
            "Front", "Image", "Frequency Sort", "PrimaryDefinition", "Sentence_English", "SentenceEng", "Sentence Meaning",
            "Translation", "Meaning", "Pitch Graph",
        )
        assertThat(FieldTemplate.guess("Custom", fields)).containsExactlyEntriesIn(
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
}
