package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A downloaded pronunciation. */
data class AudioClip(val file: File, val url: String, val extension: String)

/**
 * A pronunciation a source offers, not downloaded yet. [id] is stable for the same sources and term.
 *
 * @property url empty for text-to-speech, which has nothing to download.
 * @property name the speaker or file name when the source has one, otherwise empty.
 */
data class AudioCandidate(
    val id: String,
    val sourceIndex: Int,
    val source: AudioSource,
    val url: String,
    val name: String,
) {
    val isSpeech: Boolean get() = source.type == AudioSourceType.TEXT_TO_SPEECH
}

/** What to play for a term. */
sealed interface Pronunciation {
    data class Clip(val clip: AudioClip) : Pronunciation

    /** Read [text] aloud with the system's text-to-speech. */
    data class Speech(val text: String, val language: Language) : Pronunciation
}

/** A source that failed recently for another reason than having no audio for a word. */
data class AudioSourceFailure(val source: AudioSource, val error: AudioError)

/** Finds word audio by trying the configured sources in order. Results are cached for the process lifetime. */
@Singleton
class AudioFinder internal constructor(
    private val directory: File,
    private val httpClient: OkHttpClient,
    private val settings: AudioSettingsRepository,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        settings: AudioSettingsRepository,
    ) : this(File(context.cacheDir, "audio"), httpClient, settings)

    init {
        directory.mkdirs()
    }
    private val clipCache = mutableMapOf<ClipKey, AudioClip?>()
    private val failures = ConcurrentHashMap<AudioSource, AudioSourceFailure>()
    private val json = Json { ignoreUnknownKeys = true }

    /** The first clip of the sources in priority order, for notes; text-to-speech is skipped. */
    suspend fun find(term: String, reading: String, language: Language): AudioClip? {
        val sources = settings.current().sources
        // Other sources may have the clip, or no longer have it.
        val key = ClipKey(sources, language, term, reading)
        synchronized(clipCache) { if (key in clipCache) return clipCache[key] }
        var failed = false
        val started = System.currentTimeMillis()
        var asked = 0
        val clip = sources.withIndex()
            .filter { it.value.type != AudioSourceType.TEXT_TO_SPEECH }
            .firstNotNullOfOrNull { (index, source) ->
                asked++
                firstClip(index, source, term, reading, language).onFailure { failed = true }.getOrNull()
            }
        Log.d(TAG, "Audio: ${if (clip != null) "found" else "none"} after $asked sources in ${System.currentTimeMillis() - started} ms")
        // A network error is worth retrying later; "no audio for this word" is not.
        if (clip != null || !failed) synchronized(clipCache) { clipCache[key] = clip }
        return clip
    }

    /**
     * What to play: a clip of the sources, or text-to-speech when it is the first source or no source has a clip.
     */
    suspend fun pronunciation(term: String, reading: String, language: Language): Pronunciation? {
        val sources = settings.current().sources
        val speech = Pronunciation.Speech(reading.ifEmpty { term }, language)
        if (sources.firstOrNull()?.type == AudioSourceType.TEXT_TO_SPEECH) return speech
        find(term, reading, language)?.let { return Pronunciation.Clip(it) }
        return speech.takeIf { sources.any { it.type == AudioSourceType.TEXT_TO_SPEECH } }
    }

    /**
     * Everything the sources offer for a term, in priority order, for choosing a clip or testing sources. Lists
     * are fetched; clips are downloaded only by [download]. Sources that fail are skipped.
     */
    suspend fun candidates(
        term: String,
        reading: String,
        language: Language,
        sources: List<AudioSource>? = null,
    ): List<AudioCandidate> = withContext(Dispatchers.IO) {
        (sources ?: settings.current().sources).withIndex().flatMap { (index, source) ->
            candidatesOrNull(index, source, term, reading, language).orEmpty()
        }
    }

    /** Downloads a candidate; null when it is missing, a placeholder, or text-to-speech. */
    suspend fun download(candidate: AudioCandidate, term: String, reading: String): AudioClip? {
        if (candidate.isSpeech) return null
        return withContext(Dispatchers.IO) {
            runCatching { downloadChecked(candidate, term, reading) }
                .onFailure { note(candidate.source, it) }
                .getOrNull()
        }
    }

    /** What one source offers for a term, or why it failed, for testing a source in the settings. */
    suspend fun test(source: AudioSource, term: String, reading: String, language: Language): Result<List<AudioCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching { candidatesOf(0, source, term, reading, language) }
                .onSuccess { failures.remove(source) }
                .onFailure { note(source, it) }
        }

    /** Sources that failed since the last [clearFailures], e.g. a local audio server that is not running. */
    fun recentFailures(): List<AudioSourceFailure> = failures.values.toList()

    fun clearFailures() = failures.clear()

    /** Fails when the source could not be asked, also without a network, which [note] does not count as a failure. */
    private suspend fun firstClip(
        index: Int,
        source: AudioSource,
        term: String,
        reading: String,
        language: Language,
    ): Result<AudioClip?> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = candidatesOf(index, source, term, reading, language)
            failures.remove(source)
            candidates.firstNotNullOfOrNull { downloadChecked(it, term, reading) }
        }.onFailure { note(source, it) }
    }

    private fun candidatesOrNull(
        index: Int,
        source: AudioSource,
        term: String,
        reading: String,
        language: Language,
    ): List<AudioCandidate>? = runCatching { candidatesOf(index, source, term, reading, language) }
        .onSuccess { failures.remove(source) }
        .onFailure { note(source, it) }
        .getOrNull()

    private fun note(source: AudioSource, error: Throwable) {
        Log.i(TAG, "Audio source ${source.type} failed", error.redacted())
        // Without a network every source fails; that is not a setup problem.
        if (error is UnknownHostException) return
        failures[source] = AudioSourceFailure(source, AudioError.of(error))
    }

    private fun candidatesOf(
        index: Int,
        source: AudioSource,
        term: String,
        reading: String,
        language: Language,
    ): List<AudioCandidate> {
        fun candidates(found: List<AudioPages.Found>) =
            found.mapIndexed { n, it -> AudioCandidate("$index:$n", index, source, it.url, it.name) }
        return when (source.type) {
            AudioSourceType.JAPANESE_POD_101 -> {
                val kana = reading.ifEmpty { term }
                val url = buildString {
                    append("https://assets.languagepod101.com/dictionary/japanese/audiomp3.php?kana=")
                    append(encode(kana))
                    if (term != kana) append("&kanji=").append(encode(term))
                }
                listOf(AudioCandidate("$index:0", index, source, url, ""))
            }
            AudioSourceType.LANGUAGE_POD_101 -> {
                val form = FormBody.Builder()
                    .add("post", "dictionary_reference")
                    .add("match_type", "exact")
                    .add("search_query", term)
                    .add("vulgar", "true")
                    .build()
                val html = fetch(request(LANGUAGE_POD_SEARCH).post(form).build())
                candidates(AudioPages.languagePod(html, term, reading))
            }
            AudioSourceType.JISHO -> {
                val url = "https://jisho.org/search/".toHttpUrl().newBuilder().addPathSegment(term).build()
                candidates(AudioPages.jisho(fetch(request(url).build()), term, reading))
            }
            AudioSourceType.LINGUA_LIBRE -> {
                val support = language.support
                val files = commonsFiles(AudioPages.linguaLibreSearch(term, support.wikidataId, support.iso639Part3))
                candidates(files.map { (title, url) -> AudioPages.Found(url, AudioPages.linguaLibreSpeaker(title)) })
            }
            AudioSourceType.WIKTIONARY -> {
                val files = commonsFiles(AudioPages.wiktionarySearch(term, language.code))
                candidates(files.map { (title, url) -> AudioPages.Found(url, title.removePrefix("File:")) })
            }
            AudioSourceType.TEXT_TO_SPEECH -> listOf(AudioCandidate("$index:0", index, source, "", ""))
            AudioSourceType.URL ->
                listOf(AudioCandidate("$index:0", index, source, expand(source.url, term, reading, language), ""))
            AudioSourceType.CUSTOM_JSON -> {
                val listUrl = expand(source.url, term, reading, language).toHttpUrl()
                val body = fetch(request(listUrl).build())
                val items = try {
                    json.parseToJsonElement(body).jsonObject["audioSources"]?.jsonArray.orEmpty().map { item ->
                        val url = item.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                        url to item.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                } catch (e: IllegalArgumentException) {
                    // Also covers SerializationException: not JSON, or JSON of another shape.
                    throw NotAudioListException(e)
                }
                items
                    .mapNotNull { (url, name) ->
                        url ?: return@mapNotNull null
                        (listUrl.resolve(url)?.let { fromListHost(it, listUrl) }?.toString() ?: url) to name
                    }
                    .mapIndexed { n, (url, name) -> AudioCandidate("$index:$n", index, source, url, name) }
            }
        }
    }

    /** Titles and file URLs of Wikimedia Commons files matching a search. */
    private fun commonsFiles(search: String): List<Pair<String, String>> {
        val searchUrl = COMMONS_API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", search)
            .addQueryParameter("srnamespace", "6")
            .addQueryParameter("srlimit", COMMONS_LIMIT.toString())
            .build()
        val titles = AudioPages.commonsTitles(json.parseToJsonElement(fetch(request(searchUrl).build())))
        if (titles.isEmpty()) return emptyList()
        val infoUrl = COMMONS_API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url")
            .addQueryParameter("titles", titles.joinToString("|"))
            .build()
        return AudioPages.commonsUrls(json.parseToJsonElement(fetch(request(infoUrl).build())), titles).toList()
    }

    private fun downloadChecked(candidate: AudioCandidate, term: String, reading: String): AudioClip? =
        download(candidate.url, term, reading)?.takeUnless {
            candidate.source.type == AudioSourceType.JAPANESE_POD_101 && sha256(it.file) == JPOD_PLACEHOLDER_SHA256
        }

    /** Null when the server has no such clip (4xx or a non-audio answer); throws for server and network errors. */
    private fun download(url: String, term: String, reading: String): AudioClip? =
        httpClient.newCall(request(url).build()).execute().use { response ->
            if (response.code >= 500) throw HttpStatusException(response.code)
            if (!response.isSuccessful) return null
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.startsWith("text/") || contentType.contains("json")) return null
            // A word clip is small; a stream or a large file from a mistyped URL is not read into memory.
            if (response.body.contentLength() > MAX_CLIP_BYTES) return null
            val body = response.body.source()
            if (body.request(MAX_CLIP_BYTES + 1)) return null
            val bytes = body.buffer.readByteArray()
            if (bytes.isEmpty()) return null
            val extension = extensionFor(contentType, url)
            val file = File(directory, "${sha256("$term\n$reading\n$url".toByteArray()).take(16)}.$extension")
            file.writeBytes(bytes)
            AudioClip(file, url, extension)
        }

    private fun fetch(request: Request): String = httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw HttpStatusException(response.code)
        response.body.string()
    }

    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", USER_AGENT)

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("User-Agent", USER_AGENT)

    /** Yomitan's placeholders of a URL template. */
    private fun expand(template: String, term: String, reading: String, language: Language): String =
        template.replace("{term}", encode(term))
            .replace("{reading}", encode(reading.ifEmpty { term }))
            .replace("{language}", encode(language.code))

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun sha256(file: File): String = sha256(file.readBytes())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class ClipKey(val sources: List<AudioSource>, val language: Language, val term: String, val reading: String)

    companion object {
        /**
         * The clip file's extension: by content type, else the extension of the URL's file name (a dot in the host or
         * a folder does not count), else mp3.
         */
        internal fun extensionFor(contentType: String, url: String): String = when {
            contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
            contentType.contains("ogg") || contentType.contains("opus") -> "ogg"
            contentType.contains("wav") -> "wav"
            contentType.contains("flac") -> "flac"
            contentType.contains("webm") -> "webm"
            contentType.contains("aac") || contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
            else -> url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
                .substringAfterLast('/').substringAfterLast('.', "").lowercase()
                .takeIf { it.length in 1..4 && it.all(Char::isLetterOrDigit) }
                ?: "mp3"
        }

        private val LOCAL_HOSTS = setOf("0.0.0.0", "localhost", "127.0.0.1", "::1")

        /**
         * A list from another machine may name its files by a local address such as `0.0.0.0`, which on this device
         * points to the device itself; such files are fetched from the list's scheme, host and port instead.
         */
        internal fun fromListHost(file: HttpUrl, list: HttpUrl): HttpUrl =
            if (file.host !in LOCAL_HOSTS || list.host in LOCAL_HOSTS) file
            else file.newBuilder().scheme(list.scheme).host(list.host).port(list.port).build()

        private const val TAG = "AudioFinder"
        private const val USER_AGENT = "Screenlate (https://github.com/v1p3rrr/screenlate)"
        private const val LANGUAGE_POD_SEARCH = "https://www.japanesepod101.com/learningcenter/reference/dictionary_post"
        private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
        private const val COMMONS_LIMIT = 10
        private const val MAX_CLIP_BYTES = 10L * 1024 * 1024

        /** JapanesePod101 answers unknown words with this "not available" clip (the same check as Yomitan). */
        private const val JPOD_PLACEHOLDER_SHA256 = "ae6398b5a27bc8c0a771df6c907ade794be15518174773c58c7c7ddd17098906"
    }
}
