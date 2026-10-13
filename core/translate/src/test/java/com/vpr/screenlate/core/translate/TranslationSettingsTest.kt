package com.vpr.screenlate.core.translate

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TranslationSettingsTest {
    private val store = MemoryDataStore()
    private val repository = TranslationSettingsRepository(store)

    @Test
    fun defaults() = runBlocking<Unit> {
        val settings = repository.current()
        assertThat(settings.button).isFalse()
        assertThat(settings.ankiField).isTrue()
        assertThat(settings.enabledServices)
            .containsExactly(TranslationService.BING, TranslationService.GOOGLE, TranslationService.EDGE).inOrder()
        assertThat(settings.language).isNull()
        assertThat(settings.favorites).isEmpty()
    }

    @Test
    fun `order, switches, language and favorites`() = runBlocking<Unit> {
        repository.setServices(listOf(ServiceChoice(TranslationService.EDGE), ServiceChoice(TranslationService.GOOGLE), ServiceChoice(TranslationService.BING)))
        repository.setServiceEnabled(TranslationService.GOOGLE, false)
        repository.setLanguage("de")
        repository.setFavorite("de", true)
        repository.setFavorite("fr", true)
        repository.setFavorite("de", false)
        repository.setButton(false)
        repository.setAnkiField(false)
        val settings = repository.current()
        assertThat(settings.services).containsExactly(
            ServiceChoice(TranslationService.EDGE),
            ServiceChoice(TranslationService.GOOGLE, enabled = false),
            ServiceChoice(TranslationService.BING),
        ).inOrder()
        assertThat(settings.language).isEqualTo("de")
        assertThat(settings.favorites).containsExactly("fr")
        assertThat(settings.button).isFalse()
        assertThat(settings.ankiField).isFalse()
        repository.setLanguage(null)
        assertThat(repository.current().language).isNull()
    }

    @Test
    fun `settings of an older version keep the button on unless it was turned off`() = runBlocking<Unit> {
        store.edit { TranslationSettingsRepository.keepButtonOn(it) }
        assertThat(repository.current().button).isTrue()
        repository.setButton(false)
        store.edit { TranslationSettingsRepository.keepButtonOn(it) }
        assertThat(repository.current().button).isFalse()
    }

    @Test
    fun `a backup carries the button even when it was never switched`() {
        val button = booleanPreferencesKey("translation_button")
        assertThat(TranslationSettingsRepository.withButton(emptyPreferences())[button]).isFalse()
        val on = preferencesOf(button to true)
        assertThat(TranslationSettingsRepository.withButton(on)[button]).isTrue()
    }

    @Test
    fun `services of another version`() = runBlocking<Unit> {
        store.edit {
            it[stringPreferencesKey("translation_services")] =
                """[{"service":"DEEPL","enabled":true},{"service":"EDGE","enabled":false},{"service":"BING"},{"service":"EDGE"}]"""
        }
        assertThat(repository.current().services).containsExactly(
            ServiceChoice(TranslationService.EDGE, enabled = false),
            ServiceChoice(TranslationService.BING),
            ServiceChoice(TranslationService.GOOGLE),
        ).inOrder()
    }

    @Test
    fun `broken values fall back to the defaults`() = runBlocking<Unit> {
        store.edit {
            it[stringPreferencesKey("translation_services")] = "not json"
            it[stringPreferencesKey("translation_language")] = "xx-unknown"
        }
        val settings = repository.current()
        assertThat(settings.services).isEqualTo(TranslationSettings.DEFAULT_SERVICES)
        assertThat(settings.language).isNull()
    }
}
