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
        assertThat(FieldTemplate.markersIn("{sentence}<br>{glossary-jitendex-org}"))
            .containsExactly("sentence", "glossary-jitendex-org")
    }

    @Test
    fun glossaryMarkerIgnoresRevision() {
        assertThat(FieldTemplate.glossaryMarker("Jitendex.org [2026-08-11]")).isEqualTo("glossary-jitendex-org")
        assertThat(FieldTemplate.glossaryMarker("Колобок 400k")).isEqualTo("glossary-колобок-400k")
    }

    @Test
    fun guessesTemplatesFromFieldNames() {
        assertThat(FieldTemplate.guess("Expression", 0)).isEqualTo("{expression}")
        assertThat(FieldTemplate.guess("ExpressionReading", 1)).isEqualTo("{reading}")
        assertThat(FieldTemplate.guess("ExpressionFurigana", 2)).isEqualTo("{furigana}")
        assertThat(FieldTemplate.guess("Sentence", 3)).isEqualTo("{sentence}")
        assertThat(FieldTemplate.guess("SentenceFurigana", 4)).isEqualTo("")
        assertThat(FieldTemplate.guess("MainDefinition", 5)).isEqualTo("{glossary}")
        assertThat(FieldTemplate.guess("Picture", 6)).isEqualTo("{screenshot}")
        assertThat(FieldTemplate.guess("ExpressionAudio", 7)).isEqualTo("{audio}")
        assertThat(FieldTemplate.guess("PitchPosition", 8)).isEqualTo("{pitch-accent-positions}")
        assertThat(FieldTemplate.guess("Front", 0)).isEqualTo("{expression}")
        assertThat(FieldTemplate.guess("Back", 1)).isEqualTo("{glossary}")
        assertThat(FieldTemplate.guess("Notes", 9)).isEqualTo("")
    }
}
