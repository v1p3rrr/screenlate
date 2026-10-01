package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.MemoryDataStore
import com.vpr.screenlate.core.common.Language
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.TimeUnit

class AudioFinderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()

    /** Requests as the app sent them, before being redirected to the test server. */
    private val requests = Collections.synchronizedList(mutableListOf<okhttp3.Request>())

    /** Every request fails as without a network. */
    @Volatile private var offline = false

    /** Answers by path; everything else is a 404. */
    private val routes = Collections.synchronizedMap(mutableMapOf<String, () -> MockResponse>())

    /** The index of each request on its connection, as the server saw it. */
    private val exchangeIndexes = Collections.synchronizedList(mutableListOf<Int>())

    /** The finder's clock for tests that create a finder with it. */
    @Volatile private var now = 0L

    private lateinit var client: OkHttpClient
    private lateinit var settings: AudioSettingsRepository
    private lateinit var finder: AudioFinder

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                exchangeIndexes += request.exchangeIndex
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        // Every host goes to the test server, so the fixed hosts of the built-in sources are covered too.
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                if (offline) throw java.net.UnknownHostException(original.url.host)
                requests += original
                val url = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .build()
        settings = AudioSettingsRepository(MemoryDataStore())
        finder = AudioFinder(folder.newFolder("audio"), client, settings)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun audio(bytes: ByteArray = byteArrayOf(1, 2, 3), type: String = "audio/mpeg", delayMs: Long = 0) =
        MockResponse.Builder().code(200).addHeader("Content-Type", type).body(Buffer().write(bytes))
            .headersDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

    private fun text(body: String, type: String = "text/html") =
        MockResponse.Builder().code(200).addHeader("Content-Type", type).body(body).build()

    private fun sources(vararg sources: AudioSource) = runBlocking { settings.update { it.copy(sources = sources.toList()) } }

    private fun find(term: String = "猫", reading: String = "ねこ") = runBlocking { finder.find(term, reading, Language.JAPANESE) }

    @Test
    fun `url template downloads the clip`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}&reading={reading}"))
        routes["/word"] = { audio(byteArrayOf(7, 8)) }
        val clip = find()!!
        assertThat(clip.extension).isEqualTo("mp3")
        assertThat(clip.file.readBytes()).isEqualTo(byteArrayOf(7, 8))
        val request = requests.single()
        assertThat(request.url.queryParameter("term")).isEqualTo("猫")
        assertThat(request.url.queryParameter("reading")).isEqualTo("ねこ")
        assertThat(request.header("User-Agent")).startsWith("Screenlate")
    }

    @Test
    fun `a missing clip is remembered, a server error is not`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}"))
        assertThat(find()).isNull()
        assertThat(find()).isNull()
        assertThat(requests).hasSize(1)
        assertThat(finder.recentFailures()).isEmpty()

        routes["/word"] = { MockResponse.Builder().code(503).build() }
        assertThat(find("犬", "いぬ")).isNull()
        assertThat(find("犬", "いぬ")).isNull()
        assertThat(requests).hasSize(3)
        assertThat(finder.recentFailures().single().error).isEqualTo(AudioError(AudioError.Kind.HTTP_STATUS, "HTTP 503"))
        finder.clearFailures()
        assertThat(finder.recentFailures()).isEmpty()
    }

    @Test
    fun `a clip remembered as missing is asked again after the sources changed`() {
        sources(AudioSource(AudioSourceType.URL, "https://first.example/word?term={term}"))
        assertThat(find()).isNull()
        sources(AudioSource(AudioSourceType.URL, "https://second.example/clip?term={term}"))
        routes["/clip"] = { audio() }
        assertThat(find()?.url).startsWith("https://second.example/clip")
    }

    @Test
    fun `no audio found without a network is asked again later`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}"))
        routes["/word"] = { audio() }
        offline = true
        assertThat(find()).isNull()
        assertThat(finder.recentFailures()).isEmpty()
        offline = false
        assertThat(find()).isNotNull()
    }

    @Test
    fun `url templates take the language`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}&lang={language}"))
        routes["/word"] = { audio() }
        find()
        assertThat(requests.single().url.queryParameter("lang")).isEqualTo("ja")
    }

    @Test
    fun `a clip larger than any word recording is not read`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/stream?term={term}"))
        routes["/stream"] = { audio(ByteArray(10 * 1024 * 1024 + 1)) }
        assertThat(find()).isNull()
    }

    @Test
    fun `the extension comes from the content type or the url's file name`() {
        assertThat(AudioFinder.extensionFor("audio/webm", "https://a.example/x")).isEqualTo("webm")
        assertThat(AudioFinder.extensionFor("application/octet-stream", "https://a.example/clip.OGG?x=1")).isEqualTo("ogg")
        // A dot in the host or a folder is not an extension.
        assertThat(AudioFinder.extensionFor("", "https://audio.example.com/word?term=a.b")).isEqualTo("mp3")
        assertThat(AudioFinder.extensionFor("", "https://audio.example.com")).isEqualTo("mp3")
        assertThat(AudioFinder.extensionFor("", "https://a.example/v1.2/word")).isEqualTo("mp3")
    }

    @Test
    fun `pages instead of audio are not clips`() {
        sources(
            AudioSource(AudioSourceType.URL, "https://first.example/page?term={term}"),
            AudioSource(AudioSourceType.URL, "https://second.example/clip.ogg?term={term}"),
        )
        routes["/page"] = { text("<html>not found</html>") }
        routes["/clip.ogg"] = { audio(type = "application/octet-stream") }
        val clip = find()!!
        assertThat(clip.url).startsWith("https://second.example/clip.ogg")
        assertThat(clip.extension).isEqualTo("ogg")
    }

    @Test
    fun `custom json lists named clips with resolved urls`() = runBlocking<Unit> {
        val source = AudioSource(AudioSourceType.CUSTOM_JSON, "https://local.example/list?term={term}")
        routes["/list"] = {
            text("""{"type": "audioSourceList", "audioSources": [{"name": "Speaker A", "url": "/a.mp3"}, {"url": "https://cdn.example/b.opus"}]}""", "application/json")
        }
        val candidates = finder.candidates("猫", "ねこ", Language.JAPANESE, listOf(source))
        assertThat(candidates.map { it.url }).containsExactly("https://local.example/a.mp3", "https://cdn.example/b.opus").inOrder()
        assertThat(candidates.map { it.name }).containsExactly("Speaker A", "").inOrder()
        assertThat(candidates.map { it.id }).containsExactly("0:0", "0:1").inOrder()

        routes["/a.mp3"] = { audio() }
        assertThat(finder.download(candidates[0], "猫", "ねこ")).isNotNull()
    }

    @Test
    fun `custom json files named by a local address come from the list's host`() = runBlocking<Unit> {
        val list = """{"audioSources": [{"url": "http://0.0.0.0:5050/a.opus"}, {"url": "http://localhost:5050/b.opus"}, {"url": "https://cdn.example/c.opus"}]}"""
        routes["/list"] = { text(list, "application/json") }
        val remote = AudioSource(AudioSourceType.CUSTOM_JSON, "https://audio.example:8443/list?term={term}")
        assertThat(finder.candidates("猫", "ねこ", Language.JAPANESE, listOf(remote)).map { it.url }).containsExactly(
            "https://audio.example:8443/a.opus",
            "https://audio.example:8443/b.opus",
            "https://cdn.example/c.opus",
        ).inOrder()

        val local = AudioSource(AudioSourceType.CUSTOM_JSON, "http://127.0.0.1:5050/list?term={term}")
        assertThat(finder.candidates("猫", "ねこ", Language.JAPANESE, listOf(local)).map { it.url }).containsExactly(
            "http://0.0.0.0:5050/a.opus",
            "http://localhost:5050/b.opus",
            "https://cdn.example/c.opus",
        ).inOrder()
    }

    @Test
    fun `custom json that is not a source list is reported as such`() = runBlocking<Unit> {
        val source = AudioSource(AudioSourceType.CUSTOM_JSON, "https://audio.example/list?term={term}")
        routes["/list"] = { text("<html>login</html>") }
        val error = finder.test(source, "猫", "ねこ", Language.JAPANESE).exceptionOrNull()!!
        assertThat(AudioError.of(error).kind).isEqualTo(AudioError.Kind.NOT_A_LIST)
        routes["/list"] = { text("""["a.mp3"]""", "application/json") }
        assertThat(finder.recentFailures()).isNotEmpty()
        assertThat(finder.test(source, "猫", "ねこ", Language.JAPANESE).exceptionOrNull()).isNotNull()
        assertThat(finder.recentFailures().single().error.kind).isEqualTo(AudioError.Kind.NOT_A_LIST)
    }

    @Test
    fun `japanesepod101 asks by kana and kanji`() {
        sources(AudioSource(AudioSourceType.JAPANESE_POD_101))
        routes["/dictionary/japanese/audiomp3.php"] = { audio() }
        assertThat(find()).isNotNull()
        val url = requests.single().url
        assertThat(url.host).isEqualTo("assets.languagepod101.com")
        assertThat(url.queryParameter("kana")).isEqualTo("ねこ")
        assertThat(url.queryParameter("kanji")).isEqualTo("猫")
    }

    @Test
    fun `lingua libre recordings come from commons with speakers`() = runBlocking<Unit> {
        val title = "File:LL-Q5287 (jpn)-Speaker1-猫.wav"
        routes["/w/api.php"] = {
            val url = requests.last().url
            if (url.queryParameter("list") == "search") {
                text("""{"query": {"search": [{"title": "$title"}]}}""", "application/json")
            } else {
                text("""{"query": {"pages": {"1": {"title": "$title", "imageinfo": [{"url": "https://upload.example/cat.wav"}]}}}}""", "application/json")
            }
        }
        val candidates = finder.candidates("猫", "ねこ", Language.JAPANESE, listOf(AudioSource(AudioSourceType.LINGUA_LIBRE)))
        assertThat(candidates.single().url).isEqualTo("https://upload.example/cat.wav")
        assertThat(candidates.single().name).isEqualTo("Speaker1")
        assertThat(requests.first().url.queryParameter("srsearch")).contains("LL-Q5287 \\(jpn\\)")
    }

    @Test
    fun `testing a source reports its error and clears it on success`() = runBlocking<Unit> {
        val source = AudioSource(AudioSourceType.CUSTOM_JSON, "https://local.example/list?term={term}")
        routes["/list"] = { MockResponse.Builder().code(500).build() }
        val failed = finder.test(source, "猫", "ねこ", Language.JAPANESE)
        assertThat(failed.isFailure).isTrue()
        assertThat(finder.recentFailures().map { it.source }).containsExactly(source)

        routes["/list"] = { text("""{"audioSources": []}""", "application/json") }
        assertThat(finder.test(source, "猫", "ねこ", Language.JAPANESE).getOrThrow()).isEmpty()
        assertThat(finder.recentFailures()).isEmpty()
    }

    @Test
    fun `text to speech is played but never downloaded`() = runBlocking<Unit> {
        sources(AudioSource(AudioSourceType.TEXT_TO_SPEECH), AudioSource(AudioSourceType.URL, "https://audio.example/w?t={term}"))
        routes["/w"] = { audio() }
        assertThat(finder.pronunciation("猫", "ねこ", Language.JAPANESE)).isEqualTo(Pronunciation.Speech("ねこ", Language.JAPANESE))
        assertThat(finder.find("猫", "ねこ", Language.JAPANESE)).isNotNull()

        sources(AudioSource(AudioSourceType.URL, "https://audio.example/none?t={term}"), AudioSource(AudioSourceType.TEXT_TO_SPEECH))
        assertThat(finder.pronunciation("犬", "", Language.JAPANESE)).isEqualTo(Pronunciation.Speech("犬", Language.JAPANESE))
        val speech = finder.candidates("犬", "", Language.JAPANESE).single { it.isSpeech }
        assertThat(finder.download(speech, "犬", "")).isNull()
    }

    @Test
    fun `a source that does not answer in time is skipped`() {
        finder = AudioFinder(folder.newFolder("short"), client, settings, sourceTimeoutMs = 300)
        val slow = AudioSource(AudioSourceType.URL, "https://slow.example/slow?term={term}")
        sources(slow, AudioSource(AudioSourceType.URL, "https://fast.example/fast?term={term}"))
        routes["/slow"] = { audio(delayMs = 3_000) }
        routes["/fast"] = { audio() }
        val started = System.nanoTime()
        assertThat(find()?.url).startsWith("https://fast.example/fast")
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000)
        val failure = finder.recentFailures().single()
        assertThat(failure.source).isEqualTo(slow)
        assertThat(failure.error.kind).isEqualTo(AudioError.Kind.TIMEOUT)
    }

    @Test
    fun `the highest source with a clip wins, also when a lower one answers first`() {
        sources(
            AudioSource(AudioSourceType.URL, "https://first.example/first?term={term}"),
            AudioSource(AudioSourceType.URL, "https://second.example/second?term={term}"),
        )
        routes["/first"] = { audio(delayMs = 300) }
        routes["/second"] = { audio() }
        assertThat(find()?.url).startsWith("https://first.example/first")
    }

    @Test
    fun `sources below the one with a clip are stopped`() {
        sources(
            AudioSource(AudioSourceType.URL, "https://first.example/first?term={term}"),
            AudioSource(AudioSourceType.URL, "https://second.example/hang?term={term}"),
        )
        // The first answers once the second's request is running.
        routes["/first"] = { audio(delayMs = 300) }
        routes["/hang"] = { audio(delayMs = 4_000) }
        val started = System.nanoTime()
        assertThat(find()?.url).startsWith("https://first.example/first")
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000)
        assertThat(requests).hasSize(2)
        assertThat(finder.recentFailures()).isEmpty()
    }

    @Test
    fun `a downloaded clip plays again without a request for a minute`() = runBlocking<Unit> {
        finder = AudioFinder(folder.newFolder("clock"), client, settings, clock = { now })
        val source = AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}")
        routes["/word"] = { audio() }
        val candidate = finder.candidates("猫", "ねこ", Language.JAPANESE, listOf(source)).single()
        assertThat(finder.download(candidate, "猫", "ねこ")).isNotNull()
        now += AudioFinder.CACHE_MS - 1
        assertThat(finder.download(candidate, "猫", "ねこ")).isNotNull()
        assertThat(requests).hasSize(1)
        now += 1
        assertThat(finder.download(candidate, "猫", "ねこ")).isNotNull()
        assertThat(requests).hasSize(2)
    }

    @Test
    fun `a word's answer is asked again after a minute`() {
        finder = AudioFinder(folder.newFolder("clock"), client, settings, clock = { now })
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}"))
        assertThat(find()).isNull()
        now += AudioFinder.CACHE_MS - 1
        assertThat(find()).isNull()
        assertThat(requests).hasSize(1)
        now += 1
        routes["/word"] = { audio() }
        assertThat(find()).isNotNull()
        assertThat(requests).hasSize(2)
    }

    @Test
    fun `every request opens its own connection`() {
        sources(AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}"))
        routes["/word"] = { audio() }
        find("猫", "ねこ")
        find("犬", "いぬ")
        find("鳥", "とり")
        assertThat(exchangeIndexes).containsExactly(0, 0, 0)
    }

    @Test
    fun `a url source answering with a source list says so`() = runBlocking<Unit> {
        val source = AudioSource(AudioSourceType.URL, "https://audio.example/?term={term}&reading={reading}")
        routes["/"] = {
            text("""{"type": "audioSourceList", "audioSources": [{"url": "https://audio.example/a.opus"}]}""", "application/json")
        }
        val error = finder.test(source, "猫", "ねこ", Language.JAPANESE).exceptionOrNull()!!
        assertThat(AudioError.of(error).kind).isEqualTo(AudioError.Kind.SOURCE_LIST)
        finder.clearFailures()
        sources(source)
        assertThat(find()).isNull()
        assertThat(finder.recentFailures().single().error.kind).isEqualTo(AudioError.Kind.SOURCE_LIST)
    }

    @Test
    fun `testing a url source downloads its clip, which then plays without another request`() = runBlocking<Unit> {
        val source = AudioSource(AudioSourceType.URL, "https://audio.example/word?term={term}")
        routes["/word"] = { audio() }
        val candidate = finder.test(source, "猫", "ねこ", Language.JAPANESE).getOrThrow().single()
        assertThat(finder.download(candidate, "猫", "ねこ")).isNotNull()
        assertThat(requests).hasSize(1)
        routes.remove("/word")
        assertThat(finder.test(source, "犬", "いぬ", Language.JAPANESE).getOrThrow()).isEmpty()
    }
}
