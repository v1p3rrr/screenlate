package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** The services' requests against a local server that every host is redirected to. */
class ServiceRequestsTest {
    private val server = MockWebServer()
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    /** URLs as the translators built them, before the redirect to the test server. */
    private val urls = Collections.synchronizedList(mutableListOf<okhttp3.HttpUrl>())
    private val routes = Collections.synchronizedMap(mutableMapOf<String, () -> MockResponse>())
    private val pages = AtomicInteger()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                urls += original.url
                val url = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun tokenPage(delayMs: Long = 0): MockResponse {
        pages.incrementAndGet()
        val page = """<div data-iid="translator.5023"></div><script>_G={IG:"ABC"};""" +
            """var params_AbusePreventionHelper = [1791500740887,"tok${pages.get()}",3600000];</script>"""
        return MockResponse.Builder().code(200).addHeader("Set-Cookie", "MUID=1; Path=/").body(page)
            .headersDelay(delayMs, TimeUnit.MILLISECONDS).build()
    }

    private fun microsoftAnswer(text: String) = json("""[{"translations":[{"text":"$text","to":"ru"}]}]""")

    @Test
    fun `Google sends the text in the body`() = runBlocking<Unit> {
        routes["/translate_a/single"] = { json("""[[["Привет","こんにちは",null,null,3]],null,"ja"]""") }
        val result = GoogleTranslator(client).translate("こんにちは", "ja", "ru")
        assertThat(result).isEqualTo("Привет")
        val request = requests.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.body?.utf8()).isEqualTo("q=%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF")
        val url = urls.single()
        assertThat(url.host).isEqualTo("translate.googleapis.com")
        assertThat(url.queryParameter("sl")).isEqualTo("ja")
        assertThat(url.queryParameter("tl")).isEqualTo("ru")
        assertThat(url.toString()).doesNotContain("%E3")
    }

    @Test
    fun `Edge sends a JSON list`() = runBlocking<Unit> {
        routes["/translate/translatetext"] = { microsoftAnswer("Привет") }
        assertThat(EdgeTranslator(client).translate("こんにちは", "ja", "zh-Hans")).isEqualTo("Привет")
        val request = requests.single()
        assertThat(request.body?.utf8()).isEqualTo("""["こんにちは"]""")
        assertThat(request.headers["Content-Type"]).startsWith("application/json")
        assertThat(urls.single().queryParameter("to")).isEqualTo("zh-Hans")
    }

    @Test
    fun `a limit and other statuses`() = runBlocking<Unit> {
        routes["/translate_a/single"] = { json("", code = 429) }
        val limited = assertThrows(TranslationException::class.java) { runBlocking { GoogleTranslator(client).translate("a", "ja", "ru") } }
        assertThat(limited.error).isEqualTo(TranslationError(TranslationError.Kind.LIMITED, 429))
        routes["/translate/translatetext"] = { json("", code = 503) }
        val status = assertThrows(TranslationException::class.java) { runBlocking { EdgeTranslator(client).translate("a", "ja", "ru") } }
        assertThat(status.error).isEqualTo(TranslationError(TranslationError.Kind.HTTP_STATUS, 503))
    }

    @Test
    fun `Bing fetches its token once and sends the page's cookies back`() = runBlocking<Unit> {
        routes["/translator"] = { tokenPage() }
        routes["/ttranslatev3"] = { microsoftAnswer("Привет") }
        val bing = BingTranslator(client)
        assertThat(bing.translate("こんにちは", "ja", "ru")).isEqualTo("Привет")
        assertThat(bing.translate("さようなら", "ja", "ru")).isEqualTo("Привет")
        assertThat(pages.get()).isEqualTo(1)
        val translations = requests.filter { it.url.encodedPath == "/ttranslatev3" }
        assertThat(translations).hasSize(2)
        assertThat(translations[0].url.queryParameter("IID")).isEqualTo("translator.5023")
        assertThat(translations[1].url.queryParameter("IID")).isEqualTo("translator.5023.1")
        assertThat(translations[0].url.queryParameter("IG")).isEqualTo("ABC")
        assertThat(translations[0].headers["Cookie"]).isEqualTo("MUID=1")
        val form = translations[0].body?.utf8().orEmpty()
        assertThat(form).contains("fromLang=ja")
        assertThat(form).contains("to=ru")
        assertThat(form).contains("token=tok1")
        assertThat(form).contains("key=1791500740887")
    }

    @Test
    fun `a refused token is fetched again once`() = runBlocking<Unit> {
        routes["/translator"] = { tokenPage() }
        val answers = AtomicInteger()
        routes["/ttranslatev3"] = {
            if (answers.getAndIncrement() == 0) json("""{"statusCode":205,"errorMessage":""}""") else microsoftAnswer("Привет")
        }
        assertThat(BingTranslator(client).translate("a", "ja", "ru")).isEqualTo("Привет")
        assertThat(pages.get()).isEqualTo(2)

        routes["/ttranslatev3"] = { json("""{"statusCode":205}""") }
        val refused = assertThrows(TranslationException::class.java) { runBlocking { BingTranslator(client).translate("a", "ja", "ru") } }
        assertThat(refused.error.kind).isEqualTo(TranslationError.Kind.REJECTED)
    }

    @Test
    fun `an expired token is fetched again`() = runBlocking<Unit> {
        routes["/translator"] = { tokenPage() }
        routes["/ttranslatev3"] = { microsoftAnswer("Привет") }
        var now = 0L
        val bing = BingTranslator(client, clock = { now })
        bing.translate("a", "ja", "ru")
        now = 3_600_000L
        bing.translate("a", "ja", "ru")
        assertThat(pages.get()).isEqualTo(2)
    }

    @Test
    fun `a token that comes after the cascade moved on serves the next request`() = runBlocking<Unit> {
        routes["/translator"] = { tokenPage(delayMs = 500) }
        routes["/ttranslatev3"] = { microsoftAnswer("Привет") }
        routes["/translate_a/single"] = { json("""[[["Google","a",null,null,3]]]""") }
        val bing = BingTranslator(client)
        val translator = SentenceTranslator(
            listOf(bing, GoogleTranslator(client), EdgeTranslator(client)),
            TranslationSettingsRepository(MemoryDataStore()),
            { Locale.forLanguageTag("ru") },
            serviceTimeoutMs = 200,
        )
        assertThat(translator.translate("a", Language.JAPANESE)).isEqualTo(TranslationResult.Success("Google", TranslationService.GOOGLE))
        delay(800)
        assertThat(withTimeoutOrNull(200) { bing.translate("b", "ja", "ru") }).isEqualTo("Привет")
        assertThat(pages.get()).isEqualTo(1)
    }
}
