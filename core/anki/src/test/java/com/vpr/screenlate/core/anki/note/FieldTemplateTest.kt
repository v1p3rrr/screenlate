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
}
