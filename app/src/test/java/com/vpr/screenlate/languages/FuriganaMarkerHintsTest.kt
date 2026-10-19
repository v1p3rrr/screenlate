package com.vpr.screenlate.languages

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.common.Language
import org.junit.Test

class FuriganaMarkerHintsTest {

    @Test
    fun `chinese and russian explain every furigana marker and only those`() {
        Language.entries.forEach { language ->
            val hints = language.furiganaMarkerHints
            if (language == Language.CHINESE || language == Language.RUSSIAN) {
                assertThat(hints.keys).containsExactlyElementsIn(FieldTemplate.FURIGANA_MARKERS)
                assertThat(FieldTemplate.markersFor(language)).containsAtLeastElementsIn(hints.keys)
                assertThat(hints.values.toSet()).hasSize(hints.size)
            } else {
                assertThat(hints).isEmpty()
            }
        }
    }
}
