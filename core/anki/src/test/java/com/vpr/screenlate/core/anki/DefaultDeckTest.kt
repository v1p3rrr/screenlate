package com.vpr.screenlate.core.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultDeckTest {
    @Test
    fun `the hidden default deck is added to a list without it`() {
        assertThat(withDefaultDeck(listOf(AnkiDeck(2, "Mining")))).containsExactly(AnkiDeck(2, "Mining"), AnkiDeck(1, "Default"))
    }

    @Test
    fun `a listed default deck and an empty answer stay as they are`() {
        val decks = listOf(AnkiDeck(1, "Standard"), AnkiDeck(2, "Mining"))
        assertThat(withDefaultDeck(decks)).isEqualTo(decks)
        assertThat(withDefaultDeck(emptyList())).isEmpty()
    }
}
