package com.vpr.screenlate.dictionaries

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class CatalogOrderTest {

    private val targets = listOf("nl", "de", "ja", "sv", "fr", "es", "hu", "ru", "en", "sl", null)

    private fun order(interfaceLanguage: String) =
        targets.sortedWith(catalogTargetOrder("ja", Locale.forLanguageTag(interfaceLanguage)))

    @Test
    fun `English interface keeps the preferred order and sorts the rest by name`() {
        // English names: Dutch, Hungarian, Slovenian, Swedish.
        assertThat(order("en")).containsExactly("en", "ja", "ru", "es", "fr", "de", "nl", "hu", "sl", "sv", null).inOrder()
    }

    @Test
    fun `the interface language comes first`() {
        assertThat(order("ru").take(6)).containsExactly("ru", "en", "ja", "es", "fr", "de").inOrder()
        assertThat(order("de").take(6)).containsExactly("de", "en", "ja", "ru", "es", "fr").inOrder()
    }

    @Test
    fun `the rest follows names in the interface language`() {
        // Russian names: венгерский, нидерландский, словенский, шведский.
        assertThat(order("ru").drop(6)).containsExactly("hu", "nl", "sl", "sv", null).inOrder()
    }

    @Test
    fun `distinct codes with the same name stay apart in a sorted map`() {
        val map = listOf("zh", "zh-Hant", "en").associateWith { it }.toSortedMap(catalogTargetOrder("ja", Locale.ENGLISH))
        assertThat(map.keys).hasSize(3)
    }
}
