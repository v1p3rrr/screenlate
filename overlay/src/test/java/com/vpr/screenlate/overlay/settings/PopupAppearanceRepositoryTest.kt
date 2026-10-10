package com.vpr.screenlate.overlay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PopupAppearanceRepositoryTest {

    private class MemoryDataStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val store = MemoryDataStore()
    private val repository = PopupAppearanceRepository(store)

    @Test
    fun `the font, its scope and the CSS belong to a language, the sizes are shared`() = runTest {
        repository.setFont(Language.ENGLISH, "lora")
        repository.setFontForAllText(Language.ENGLISH, true)
        repository.setCustomCss(Language.ENGLISH, "body { color: red }")
        repository.setFontSize(18)
        repository.setTextWeight(600)

        val english = repository.appearance(Language.ENGLISH).first()
        assertThat(english.fontId).isEqualTo("lora")
        assertThat(english.fontForAllText).isTrue()
        assertThat(english.customCss).isEqualTo("body { color: red }")
        val japanese = repository.appearance(Language.JAPANESE).first()
        assertThat(japanese.fontId).isNull()
        assertThat(japanese.fontForAllText).isFalse()
        assertThat(japanese.customCss).isEmpty()
        assertThat(japanese.fontSize).isEqualTo(18)
        assertThat(japanese.textWeight).isEqualTo(600)
        assertThat(repository.fontSize()).isEqualTo(18)
    }

    @Test
    fun `Japanese keeps the keys it had before languages were added`() = runTest {
        repository.setFont(Language.JAPANESE, "noto")
        repository.setCustomCss(Language.JAPANESE, "a {}")
        repository.setCustomCss(Language.ENGLISH, "b {}")
        assertThat(store.state.value.asMap().keys.map { it.name })
            .containsExactly("popup_font", "popup_custom_css", "popup_custom_css_en")
    }
}
