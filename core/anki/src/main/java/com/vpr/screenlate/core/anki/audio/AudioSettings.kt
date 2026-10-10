package com.vpr.screenlate.core.anki.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.cached
import com.vpr.screenlate.core.common.settings.preferenceKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Yomitan's audio sources. */
enum class AudioSourceType(val hasUrl: Boolean = false) {
    /** JapanesePod101 word audio by kana and kanji. */
    JAPANESE_POD_101,

    /** The JapanesePod101 dictionary search, which has recordings JapanesePod101's word audio lacks. */
    LANGUAGE_POD_101,

    /** Recordings shown on jisho.org. */
    JISHO,

    /** Lingua Libre recordings on Wikimedia Commons, by speaker. */
    LINGUA_LIBRE,

    /** Wiktionary pronunciation files on Wikimedia Commons. */
    WIKTIONARY,

    /** The system's text-to-speech; played only, never put into notes. */
    TEXT_TO_SPEECH,

    /** A URL template with `{term}` and `{reading}` that returns an audio file. */
    URL(hasUrl = true),

    /** A URL template that returns Yomitan's `audioSourceList` JSON. */
    CUSTOM_JSON(hasUrl = true),
}

@Serializable
data class AudioSource(val type: AudioSourceType, val url: String = "") {
    /** Host and port of a URL source, to tell several of them apart; null for the built-in sources. */
    val address: String?
        get() = if (!type.hasUrl) null else ADDRESS.find(url.trim())?.groupValues?.get(1)?.substringAfterLast('@')

    // Not private: the serialization plugin puts the serializer here, which the settings decoding looks up.
    companion object {
        private val ADDRESS = Regex("^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]+)")
    }
}

/**
 * Audio sources in priority order, as in Yomitan. Each language has its own sources and regions; [autoPlay] and
 * [volume] are shared by every language.
 *
 * @property volume playback volume in percent.
 * @property regions ids of the language's regions (`AudioRegion`) in the order Wiktionary recordings are tried; empty
 *   for the default order.
 */
@Serializable
data class AudioSettings(
    val sources: List<AudioSource> = defaultSources(Language.JAPANESE),
    val autoPlay: Boolean = false,
    val volume: Int = 100,
    val regions: List<String> = emptyList(),
) {
    companion object {
        fun defaultSources(language: Language): List<AudioSource> = language.support.defaultAudioSources
            .mapNotNull { name -> AudioSourceType.entries.firstOrNull { it.name == name } }
            .map { AudioSource(it) }
    }
}

/**
 * Audio settings per language. Japanese keeps the keys it had before languages were added; another language keeps
 * its sources under its own key, while [AudioSettings.autoPlay] and [AudioSettings.volume] stay under Japanese's.
 */
@Singleton
class AudioSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun settings(language: Language): Flow<AudioSettings> = dataStore.data.map { read(it, language) }

    /** The settings as last read, for a screen's first frame; null before the first read. */
    fun cachedSettings(language: Language): AudioSettings? = dataStore.cached { read(it, language) }

    suspend fun current(language: Language): AudioSettings = settings(language).first()

    /** The volume every language plays at, in percent. */
    suspend fun volume(): Int = dataStore.data.map { read(it, Language.JAPANESE).volume }.first()

    /** Reads and writes in one step, so quick successive changes do not overwrite each other. */
    suspend fun update(language: Language, transform: (AudioSettings) -> AudioSettings) {
        dataStore.edit { prefs ->
            val updated = transform(read(prefs, language))
            if (language == Language.JAPANESE) {
                prefs[KEY] = json.encodeToString(updated)
                prefs[CHOSEN] = true
            } else {
                val shared = readOwn(prefs, Language.JAPANESE)
                if (updated.autoPlay != shared.autoPlay || updated.volume != shared.volume) {
                    prefs[KEY] = json.encodeToString(shared.copy(autoPlay = updated.autoPlay, volume = updated.volume))
                }
                prefs[stringPreferencesKey(language.preferenceKey(KEY.name))] =
                    json.encodeToString(AudioSettings(sources = updated.sources, regions = updated.regions))
            }
        }
    }

    /** Puts [AudioSettings.autoPlay] and [AudioSettings.volume] back to their defaults; every language's sources stay. */
    suspend fun resetShared() {
        dataStore.edit { prefs ->
            val stored = prefs[KEY]?.let(::decode) ?: return@edit
            val defaults = AudioSettings()
            prefs[KEY] = json.encodeToString(stored.copy(autoPlay = defaults.autoPlay, volume = defaults.volume))
        }
    }

    private fun read(prefs: Preferences, language: Language): AudioSettings {
        val shared = readOwn(prefs, Language.JAPANESE)
        if (language == Language.JAPANESE) return shared
        return readOwn(prefs, language).copy(autoPlay = shared.autoPlay, volume = shared.volume)
    }

    /** What [language]'s own key holds; for languages other than Japanese only the sources and regions count. */
    private fun readOwn(prefs: Preferences, language: Language): AudioSettings {
        if (language != Language.JAPANESE) {
            return prefs[stringPreferencesKey(language.preferenceKey(KEY.name))]?.let(::decode)
                ?: AudioSettings(sources = AudioSettings.defaultSources(language))
        }
        val stored = prefs[KEY]?.let(::decode) ?: return AudioSettings()
        // Versions before the full source set stored their only default; that was never the user's choice.
        return if (prefs[CHOSEN] != true && stored.sources == LEGACY_DEFAULT) stored.copy(sources = AudioSettings().sources) else stored
    }

    /** Sources of a type this version does not know (settings of a newer version) are left out, not the whole list. */
    private fun decode(raw: String): AudioSettings? = runCatching {
        val stored = json.parseToJsonElement(raw).jsonObject
        val sources = stored["sources"]?.jsonArray?.filter { source ->
            runCatching { json.decodeFromJsonElement<AudioSource>(source) }.isSuccess
        }
        json.decodeFromJsonElement<AudioSettings>(
            if (sources == null) stored else JsonObject(stored + ("sources" to JsonArray(sources))),
        )
    }.getOrNull()

    private companion object {
        val KEY = stringPreferencesKey("audio_settings")
        val CHOSEN = booleanPreferencesKey("audio_sources_chosen")
        val LEGACY_DEFAULT = listOf(AudioSource(AudioSourceType.JAPANESE_POD_101))
    }
}
