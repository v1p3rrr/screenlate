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

class AudioFinderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()

    /** Requests as the app sent them, before being redirected to the test server. */
    private val requests = Collections.synchronizedList(mutableListOf<okhttp3.Request>())

    /** Answers by path; everything else is a 404. */
    private val routes = mutableMapOf<String, () -> MockResponse>()

    private lateinit var settings: AudioSettingsRepository
    private lateinit var finder: AudioFinder

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
        }
        server.start()
        // Every host goes to the test server, so the fixed hosts of the built-in sources are covered too.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
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

    private fun audio(bytes: ByteArray = byteArrayOf(1, 2, 3), type: String = "audio/mpeg") =
        MockResponse.Builder().code(200).addHeader("Content-Type", type).body(Buffer().write(bytes)).build()

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
        assertThat(finder.recentFailures().single().reason).isEqualTo("HTTP 503")
        finder.clearFailures()
        assertThat(finder.recentFailures()).isEmpty()
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
}
