package com.vpr.screenlate.core.anki.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    private companion object {
        val ADDRESS = Regex("^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]+)")
    }
}

/**
 * Audio sources in priority order, as in Yomitan.
 *
 * @property volume playback volume in percent.
 */
@Serializable
data class AudioSettings(
    val sources: List<AudioSource> = defaultSources(Language.JAPANESE),
    val autoPlay: Boolean = false,
    val volume: Int = 100,
) {
    companion object {
        fun defaultSources(language: Language): List<AudioSource> = language.support.defaultAudioSources
            .mapNotNull { name -> AudioSourceType.entries.firstOrNull { it.name == name } }
            .map { AudioSource(it) }
    }
}

@Singleton
class AudioSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AudioSettings> = dataStore.data.map { prefs ->
        val stored = prefs[KEY]?.let { runCatching { json.decodeFromString<AudioSettings>(it) }.getOrNull() }
            ?: return@map AudioSettings()
        // Versions before the full source set stored their only default; that was never the user's choice.
        if (prefs[CHOSEN] != true && stored.sources == LEGACY_DEFAULT) stored.copy(sources = AudioSettings().sources) else stored
    }

    suspend fun current(): AudioSettings = settings.first()

    suspend fun update(transform: (AudioSettings) -> AudioSettings) {
        val next = transform(current())
        dataStore.edit {
            it[KEY] = json.encodeToString(next)
            it[CHOSEN] = true
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("audio_settings")
        val CHOSEN = booleanPreferencesKey("audio_sources_chosen")
        val LEGACY_DEFAULT = listOf(AudioSource(AudioSourceType.JAPANESE_POD_101))
    }
}
