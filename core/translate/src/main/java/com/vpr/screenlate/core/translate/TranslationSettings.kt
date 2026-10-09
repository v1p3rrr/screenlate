package com.vpr.screenlate.core.translate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vpr.screenlate.core.common.settings.cached
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/** A service in the cascade and whether it takes part. */
@Serializable
data class ServiceChoice(val service: TranslationService, val enabled: Boolean = true)

/**
 * @property button the 文A button in the popup.
 * @property ankiField whether notes get `{sentence-translation}`.
 * @property services the cascade: the first enabled service translates, the next ones take over when it fails.
 * @property language BCP 47 tag of the language translations go into; null follows the interface language.
 * @property favorites tags starred in the language list.
 */
data class TranslationSettings(
    val button: Boolean = true,
    val ankiField: Boolean = true,
    val services: List<ServiceChoice> = DEFAULT_SERVICES,
    val language: String? = null,
    val favorites: Set<String> = emptySet(),
) {
    val enabledServices: List<TranslationService> get() = services.filter { it.enabled }.map { it.service }

    companion object {
        val DEFAULT_SERVICES = TranslationService.entries.map { ServiceChoice(it) }
    }
}

@Singleton
class TranslationSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<TranslationSettings> = dataStore.data.map(::read)

    /** The settings as last read, for a screen's first frame; null before the first read. */
    val cachedSettings: TranslationSettings? get() = dataStore.cached(::read)

    suspend fun current(): TranslationSettings = settings.first()

    suspend fun setButton(on: Boolean) {
        dataStore.edit { it[BUTTON] = on }
    }

    suspend fun setAnkiField(on: Boolean) {
        dataStore.edit { it[ANKI] = on }
    }

    suspend fun setServices(services: List<ServiceChoice>) {
        dataStore.edit { it[SERVICES] = json.encodeToString(services) }
    }

    /** Reads and writes in one step, so quick successive switches do not overwrite each other. */
    suspend fun setServiceEnabled(service: TranslationService, enabled: Boolean) {
        dataStore.edit { prefs ->
            val services = readServices(prefs).map { if (it.service == service) it.copy(enabled = enabled) else it }
            prefs[SERVICES] = json.encodeToString(services)
        }
    }

    /** Null follows the interface language. */
    suspend fun setLanguage(tag: String?) {
        dataStore.edit { if (tag == null) it.remove(LANGUAGE) else it[LANGUAGE] = tag }
    }

    suspend fun setFavorite(tag: String, favorite: Boolean) {
        dataStore.edit { prefs ->
            val favorites = prefs[FAVORITES].orEmpty()
            prefs[FAVORITES] = if (favorite) favorites + tag else favorites - tag
        }
    }

    private fun read(prefs: Preferences) = TranslationSettings(
        button = prefs[BUTTON] ?: true,
        ankiField = prefs[ANKI] ?: true,
        services = readServices(prefs),
        language = prefs[LANGUAGE]?.takeIf { TranslationLanguages.of(it) != null },
        favorites = prefs[FAVORITES].orEmpty(),
    )

    /**
     * The stored order; services this version does not know (settings of a newer version) are left out, and services
     * missing from it (added by a newer version) come last, turned on.
     */
    private fun readServices(prefs: Preferences): List<ServiceChoice> {
        val raw = prefs[SERVICES] ?: return TranslationSettings.DEFAULT_SERVICES
        val stored = runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
                (item as? JsonObject)?.let { runCatching { json.decodeFromJsonElement<ServiceChoice>(it) }.getOrNull() }
            }
        }.getOrNull() ?: return TranslationSettings.DEFAULT_SERVICES
        val known = stored.distinctBy { it.service }
        return known + TranslationService.entries.filter { service -> known.none { it.service == service } }.map { ServiceChoice(it) }
    }

    companion object {
        /** Every key of this page starts with it, for the page's reset and the backup. */
        const val KEY_PREFIX = "translation_"

        private val BUTTON = booleanPreferencesKey("${KEY_PREFIX}button")
        private val ANKI = booleanPreferencesKey("${KEY_PREFIX}anki")
        private val SERVICES = stringPreferencesKey("${KEY_PREFIX}services")
        private val LANGUAGE = stringPreferencesKey("${KEY_PREFIX}language")
        private val FAVORITES = stringSetPreferencesKey("${KEY_PREFIX}favorites")

        private val json = Json { ignoreUnknownKeys = true }
    }
}
