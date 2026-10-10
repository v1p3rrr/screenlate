package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.AudioRegion
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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

/**
 * Finds word audio in the configured sources. All sources are asked at once, each for at most [sourceTimeoutMs], and
 * the highest one in the list with a clip wins. Answers and downloaded clips are kept for [CACHE_MS].
 */
@Singleton
class AudioFinder internal constructor(
    private val directory: File,
    client: OkHttpClient,
    private val settings: AudioSettingsRepository,
    private val sourceTimeoutMs: Long = SOURCE_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
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

    /**
     * Every request opens its own connection: some mobile networks freeze a connection to foreign hosting after its
     * first answers, and a request sent over such a kept-open connection hangs.
     */
    private val httpClient = client.newBuilder().connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)).build()
    private val clipCache = mutableMapOf<ClipKey, Kept<AudioClip?>>()
    private val downloads = ConcurrentHashMap<String, Kept<AudioClip>>()
    private val failures = ConcurrentHashMap<AudioSource, AudioSourceFailure>()
    private val json = Json { ignoreUnknownKeys = true }

    /** The first clip of the sources in priority order, for notes; text-to-speech is skipped. */
    suspend fun find(term: String, reading: String, language: Language): AudioClip? = withContext(Dispatchers.IO) {
        val sources = settings.current(language).sources
        // Other sources may have the clip, or no longer have it.
        val key = ClipKey(sources, language, term, reading)
        synchronized(clipCache) {
            val kept = clipCache[key]
            if (kept != null && kept.fresh()) return@withContext kept.value
            clipCache.remove(key)
        }
        var failed = false
        val started = clock()
        var position = 0
        val asked = sources.withIndex().filter { it.value.type != AudioSourceType.TEXT_TO_SPEECH }
        val clip = coroutineScope {
            val answers = asked.map { (index, source) -> async { firstClip(index, source, term, reading, language) } }
            answers.firstNotNullOfOrNull { answer ->
                position++
                answer.await().onFailure { failed = true }.getOrNull()
            }.also {
                // The sources below the one with a clip are not needed; their requests stop.
                answers.forEach { it.cancel() }
            }
        }
        val outcome = if (clip != null) "found at source $position of ${asked.size}" else "none in ${asked.size} sources"
        Log.d(TAG, "Audio: $outcome in ${clock() - started} ms")
        // A network error is worth retrying later; "no audio for this word" is not.
        if (clip != null || !failed) {
            synchronized(clipCache) {
                clipCache.values.removeAll { !it.fresh() }
                clipCache[key] = Kept(clip, clock())
            }
        }
        clip
    }

    /**
     * What to play: a clip of the sources, or text-to-speech when it is the first source or no source has a clip.
     */
    suspend fun pronunciation(term: String, reading: String, language: Language): Pronunciation? {
        val sources = settings.current(language).sources
        val speech = Pronunciation.Speech(reading.ifEmpty { term }, language)
        if (sources.firstOrNull()?.type == AudioSourceType.TEXT_TO_SPEECH) return speech
        find(term, reading, language)?.let { return Pronunciation.Clip(it) }
        return speech.takeIf { sources.any { it.type == AudioSourceType.TEXT_TO_SPEECH } }
    }

    /**
     * Everything the sources offer for a term, in priority order, for choosing a clip or testing sources. Lists
     * are fetched, from all sources at once; clips are downloaded only by [download]. Sources that fail are skipped.
     */
    suspend fun candidates(
        term: String,
        reading: String,
        language: Language,
        sources: List<AudioSource>? = null,
    ): List<AudioCandidate> = withContext(Dispatchers.IO) {
        val asked = (sources ?: settings.current(language).sources).withIndex()
            .map { (index, source) -> async { candidatesOrNull(index, source, term, reading, language) } }
        asked.flatMap { it.await().orEmpty() }
    }

    /** Downloads a candidate; null when it is missing, a placeholder, or text-to-speech. */
    suspend fun download(candidate: AudioCandidate, term: String, reading: String): AudioClip? {
        if (candidate.isSpeech) return null
        return withContext(Dispatchers.IO) {
            attempt(candidate.source) { downloadChecked(candidate, term, reading, Deadline()) }.getOrNull()
        }
    }

    /**
     * What one source offers for a term, or why it failed, for testing a source in the settings. A URL source's
     * answer is downloaded here, so a page or a source list instead of a clip shows in the test, not only on playing.
     */
    suspend fun test(source: AudioSource, term: String, reading: String, language: Language): Result<List<AudioCandidate>> =
        withContext(Dispatchers.IO) {
            val deadline = Deadline()
            attempt(source) {
                val candidates = candidatesOf(0, source, term, reading, language, deadline)
                if (source.type != AudioSourceType.URL) candidates
                else candidates.filter { downloadChecked(it, term, reading, deadline) != null }
            }.onSuccess { failures.remove(source) }
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
    ): Result<AudioClip?> {
        val deadline = Deadline()
        return attempt(source) {
            val candidates = candidatesOf(index, source, term, reading, language, deadline)
            failures.remove(source)
            candidates.firstNotNullOfOrNull { downloadChecked(it, term, reading, deadline) }
        }
    }

    private suspend fun candidatesOrNull(
        index: Int,
        source: AudioSource,
        term: String,
        reading: String,
        language: Language,
    ): List<AudioCandidate>? = attempt(source) { candidatesOf(index, source, term, reading, language, Deadline()) }
        .onSuccess { failures.remove(source) }
        .getOrNull()

    /**
     * Runs requests of [source] and notes a failure. A request cancelled because its answer is no longer needed fails
     * too, which is not the source's fault: the cancellation is passed on instead.
     */
    private suspend fun <T> attempt(source: AudioSource, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        note(source, e)
        Result.failure(e)
    }

    private fun note(source: AudioSource, error: Throwable) {
        Log.i(TAG, "Audio source ${source.type} failed", error.redacted())
        // Without a network every source fails; that is not a setup problem.
        if (error is UnknownHostException) return
        failures[source] = AudioSourceFailure(source, AudioError.of(error))
    }

    private suspend fun candidatesOf(
        index: Int,
        source: AudioSource,
        term: String,
        reading: String,
        language: Language,
        deadline: Deadline,
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
                val html = fetch(request(LANGUAGE_POD_SEARCH).post(form).build(), deadline)
                candidates(AudioPages.languagePod(html, term, reading))
            }
            AudioSourceType.JISHO -> {
                val url = "https://jisho.org/search/".toHttpUrl().newBuilder().addPathSegment(term).build()
                candidates(AudioPages.jisho(fetch(request(url).build(), deadline), term, reading))
            }
            AudioSourceType.LINGUA_LIBRE -> {
                val support = language.support
                val files = commonsFiles(AudioPages.linguaLibreSearch(term, support.wikidataId, support.iso639Part3), deadline)
                candidates(AudioPages.linguaLibre(files, term))
            }
            AudioSourceType.WIKTIONARY -> {
                val regions = AudioRegion.ordered(settings.current(language).regions, language.support.audioRegions)
                val files = AudioPages.byRegion(commonsFiles(AudioPages.wiktionarySearch(term, language.code), deadline), term, language.code, regions)
                candidates(files.map { (title, url) -> AudioPages.Found(url, title.removePrefix("File:")) })
            }
            AudioSourceType.TEXT_TO_SPEECH -> listOf(AudioCandidate("$index:0", index, source, "", ""))
            AudioSourceType.URL ->
                listOf(AudioCandidate("$index:0", index, source, expand(source.url, term, reading, language), ""))
            AudioSourceType.CUSTOM_JSON -> {
                val listUrl = expand(source.url, term, reading, language).toHttpUrl()
                val body = fetch(request(listUrl).build(), deadline)
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
    private suspend fun commonsFiles(search: String, deadline: Deadline): List<Pair<String, String>> {
        val searchUrl = COMMONS_API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", search)
            .addQueryParameter("srnamespace", "6")
            .addQueryParameter("srlimit", COMMONS_LIMIT.toString())
            .build()
        val titles = AudioPages.commonsTitles(json.parseToJsonElement(fetch(request(searchUrl).build(), deadline)))
        if (titles.isEmpty()) return emptyList()
        val infoUrl = COMMONS_API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url")
            .addQueryParameter("titles", titles.joinToString("|"))
            .build()
        return AudioPages.commonsUrls(json.parseToJsonElement(fetch(request(infoUrl).build(), deadline)), titles).toList()
    }

    private suspend fun downloadChecked(candidate: AudioCandidate, term: String, reading: String, deadline: Deadline): AudioClip? =
        download(candidate.url, term, reading, deadline, listIsError = candidate.source.type == AudioSourceType.URL)
            ?.takeUnless {
                candidate.source.type == AudioSourceType.JAPANESE_POD_101 && sha256(it.file) == JPOD_PLACEHOLDER_SHA256
            }

    /**
     * Null when the server has no such clip (4xx or a non-audio answer); throws for server and network errors, and
     * when [listIsError] for a Yomitan source list, which a URL source mistaken for a "Custom URL (JSON)" one gets.
     * A clip downloaded in the last [CACHE_MS] is not downloaded again.
     */
    private suspend fun download(url: String, term: String, reading: String, deadline: Deadline, listIsError: Boolean): AudioClip? {
        val key = "$term\n$reading\n$url"
        downloads[key]?.let { kept -> if (kept.fresh() && kept.value.file.exists()) return kept.value }
        val clip = execute(request(url).build(), deadline) { response ->
            if (response.code >= 500) throw HttpStatusException(response.code)
            if (!response.isSuccessful) return@execute null
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.startsWith("text/") || contentType.contains("json")) {
                if (listIsError && isSourceList(response)) throw SourceListException()
                return@execute null
            }
            // A word clip is small; a stream or a large file from a mistyped URL is not read into memory.
            if (response.body.contentLength() > MAX_CLIP_BYTES) return@execute null
            val body = response.body.source()
            if (body.request(MAX_CLIP_BYTES + 1)) return@execute null
            val bytes = body.buffer.readByteArray()
            if (bytes.isEmpty()) return@execute null
            val extension = extensionFor(contentType, url)
            val file = File(directory, "${sha256(key.toByteArray()).take(16)}.$extension")
            file.writeBytes(bytes)
            AudioClip(file, url, extension)
        }
        if (clip != null) {
            downloads.values.removeAll { !it.fresh() }
            downloads[key] = Kept(clip, clock())
        }
        return clip
    }

    /** Whether an answer is Yomitan's `audioSourceList`, with or without its `type`. */
    private fun isSourceList(response: Response): Boolean {
        val body = response.body.source()
        if (body.request(MAX_LIST_BYTES + 1)) return false
        val answer = runCatching { json.parseToJsonElement(body.buffer.readUtf8()) }.getOrNull()
        return (answer as? JsonObject)?.get("audioSources") is JsonArray
    }

    private suspend fun fetch(request: Request, deadline: Deadline): String = execute(request, deadline) { response ->
        if (!response.isSuccessful) throw HttpStatusException(response.code)
        response.body.string()
    }

    /**
     * Runs [request] within the source's [deadline] and reads its answer. Cancelling the coroutine cancels the request,
     * so a source whose answer is no longer needed stops at once instead of when its time is up.
     */
    private suspend fun <T> execute(request: Request, deadline: Deadline, read: (Response) -> T): T {
        val left = deadline.leftMs()
        if (left <= 0) throw InterruptedIOException("timeout")
        val call = httpClient.newCall(request)
        call.timeout().timeout(left, TimeUnit.MILLISECONDS)
        return withContext(Dispatchers.IO) {
            val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use(read)
            } finally {
                canceller.cancel()
            }
        }
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

    /** A remembered answer and when it was given. */
    private class Kept<T>(val value: T, val at: Long)

    private fun Kept<*>.fresh() = clock() - at < CACHE_MS

    /** The time one source has left; all its requests share it, so it answers or is skipped within [sourceTimeoutMs]. */
    private inner class Deadline {
        private val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sourceTimeoutMs)

        fun leftMs(): Long = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime())
    }

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
        private const val MAX_LIST_BYTES = 1024L * 1024

        /** A source that has not answered by then is skipped, so a hanging one does not hold up the others. */
        private const val SOURCE_TIMEOUT_MS = 5_000L

        /** How long a found clip, or "none" for a word, is kept before the sources are asked again. */
        internal const val CACHE_MS = 60_000L

        /** JapanesePod101 answers unknown words with this "not available" clip (the same check as Yomitan). */
        private const val JPOD_PLACEHOLDER_SHA256 = "ae6398b5a27bc8c0a771df6c907ade794be15518174773c58c7c7ddd17098906"
    }
}
