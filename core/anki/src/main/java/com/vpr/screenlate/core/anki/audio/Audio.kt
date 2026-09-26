package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioSourceType {
    /** JapanesePod101 word audio. */
    JAPANESE_POD_101,

    /** A URL template with `{term}` and `{reading}` that returns an audio file. */
    URL,

    /** A URL template that returns Yomitan's `audioSourceList` JSON. */
    CUSTOM_JSON,
}

@Serializable
data class AudioSource(val type: AudioSourceType, val url: String = "")

/** Audio sources in priority order, as in Yomitan. */
@Serializable
data class AudioSettings(
    val sources: List<AudioSource> = listOf(AudioSource(AudioSourceType.JAPANESE_POD_101)),
    val autoPlay: Boolean = false,
)

@Singleton
class AudioSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AudioSettings> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { json.decodeFromString<AudioSettings>(it) }.getOrNull() } ?: AudioSettings()
    }

    suspend fun current(): AudioSettings = settings.first()

    suspend fun update(transform: (AudioSettings) -> AudioSettings) {
        val next = transform(current())
        dataStore.edit { it[KEY] = json.encodeToString(next) }
    }

    private companion object {
        val KEY = stringPreferencesKey("audio_settings")
    }
}

/** A downloaded pronunciation. */
data class AudioClip(val file: File, val url: String, val extension: String)

/** Finds word audio by trying the configured sources in order. Results are cached for the process lifetime. */
@Singleton
class AudioFinder @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
    private val settings: AudioSettingsRepository,
) {
    private val directory = File(context.cacheDir, "audio").apply { mkdirs() }
    private val cache = mutableMapOf<Pair<String, String>, AudioClip?>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun find(term: String, reading: String): AudioClip? {
        val key = term to reading
        synchronized(cache) { if (key in cache) return cache[key] }
        var failed = false
        val clip = withContext(Dispatchers.IO) {
            settings.current().sources.firstNotNullOfOrNull { source ->
                runCatching { fromSource(source, term, reading) }
                    .onFailure {
                        failed = true
                        Log.i(TAG, "Audio source ${source.type} failed: ${it.message}")
                    }
                    .getOrNull()
            }
        }
        // A network error is worth retrying later; "no audio for this word" is not.
        if (clip != null || !failed) synchronized(cache) { cache[key] = clip }
        return clip
    }

    private fun fromSource(source: AudioSource, term: String, reading: String): AudioClip? = when (source.type) {
        AudioSourceType.JAPANESE_POD_101 -> {
            val kana = reading.ifEmpty { term }
            val url = buildString {
                append("https://assets.languagepod101.com/dictionary/japanese/audiomp3.php?kana=")
                append(encode(kana))
                if (term != kana) append("&kanji=").append(encode(term))
            }
            download(url, term, reading)?.takeUnless { sha256(it.file) == JPOD_PLACEHOLDER_SHA256 }
        }
        AudioSourceType.URL -> download(expand(source.url, term, reading), term, reading)
        AudioSourceType.CUSTOM_JSON -> {
            val listUrl = expand(source.url, term, reading)
            val body = httpClient.newCall(Request.Builder().url(listUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
            val sources = json.parseToJsonElement(body).jsonObject["audioSources"]?.jsonArray.orEmpty()
            sources.firstNotNullOfOrNull { item ->
                val url = item.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
                download(listUrl.toHttpUrl().resolve(url)?.toString() ?: url, term, reading)
            }
        }
    }

    private fun download(url: String, term: String, reading: String): AudioClip? =
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body.bytes()
            if (bytes.isEmpty()) return null
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.startsWith("text/") || contentType.contains("json")) return null
            val extension = extensionFor(contentType, url)
            val file = File(directory, "${sha256("$term\n$reading\n$url".toByteArray()).take(16)}.$extension")
            file.writeBytes(bytes)
            AudioClip(file, url, extension)
        }

    private fun extensionFor(contentType: String, url: String): String = when {
        contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
        contentType.contains("ogg") || contentType.contains("opus") -> "ogg"
        contentType.contains("wav") -> "wav"
        contentType.contains("aac") || contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
        else -> url.substringBefore('?').substringAfterLast('.', "mp3").take(4).lowercase()
    }

    private fun expand(template: String, term: String, reading: String): String =
        template.replace("{term}", encode(term)).replace("{reading}", encode(reading.ifEmpty { term }))

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun sha256(file: File): String = sha256(file.readBytes())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "AudioFinder"

        /** JapanesePod101 answers unknown words with this "not available" clip (the same check as Yomitan). */
        const val JPOD_PLACEHOLDER_SHA256 = "ae6398b5a27bc8c0a771df6c907ade794be15518174773c58c7c7ddd17098906"
    }
}
