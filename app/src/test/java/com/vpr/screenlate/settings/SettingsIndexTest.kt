package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsIndexTest {

    @Test
    fun `every settings page is represented in the index`() {
        val indexedPages = SettingsIndex.items.map { it.page }.toSet()
        val allPages = SettingsPage.entries.toSet()

        assertThat(indexedPages).containsExactlyElementsIn(allPages)
    }

    @Test
    fun `all indexed items have valid non-zero resource IDs`() {
        for (item in SettingsIndex.items) {
            assertThat(item.titleRes).isNotEqualTo(0)
            assertThat(item.sectionRes).isNotEqualTo(0)
            assertThat(item.iconRes).isNotEqualTo(0)
            item.summaryRes?.let { assertThat(it).isNotEqualTo(0) }
        }
    }

    @Test
    fun `searching by keyword returns matching items`() {
        val results = SettingsIndex.search("dock") { "mock" }
        assertThat(results.map { it.page }).contains(SettingsPage.BUBBLE)
    }

    @Test
    fun `search is case-insensitive and supports multiple tokens`() {
        val results = SettingsIndex.search("anki deck") { id ->
            when (id) {
                com.vpr.screenlate.R.string.anki_deck -> "Deck"
                com.vpr.screenlate.R.string.anki_title -> "Anki"
                else -> ""
            }
        }
        assertThat(results.map { it.page }).contains(SettingsPage.ANKI)
    }

    @Test
    fun `blank or whitespace query returns empty list`() {
        assertThat(SettingsIndex.search("") { "mock" }).isEmpty()
        assertThat(SettingsIndex.search("   ") { "mock" }).isEmpty()
    }
}
