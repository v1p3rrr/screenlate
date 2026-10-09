package com.vpr.screenlate.core.translate

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Bing Translator's web page: its token comes from the translator page (with cookies), then each text goes to
 * `/ttranslatev3`. The token lasts an hour. Its request runs on its own, so a page that arrives after the cascade moved
 * on to the next service still serves the next translation.
 */
internal class BingTranslator(
    client: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : Translator {
    private val cookies = MemoryCookieJar(clock)
    private val client = client.freshConnections().cookieJar(cookies).build()
    private val pageClient = this.client.newBuilder().callTimeout(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var token: BingToken? = null
    private var fetching: Deferred<BingToken>? = null

    override val service = TranslationService.BING
    override val maxLength = MAX_LENGTH

    override fun code(language: TranslationLanguage): String? = language.microsoft

    override suspend fun translate(text: String, from: String, to: String): String = try {
        request(token(renew = false), text, from, to)
    } catch (e: TokenRejectedException) {
        try {
            request(token(renew = true), text, from, to)
        } catch (again: TokenRejectedException) {
            throw TranslationException(TranslationError.Kind.REJECTED)
        }
    }

    private suspend fun request(token: BingToken, text: String, from: String, to: String): String {
        val count = token.requests.getAndIncrement()
        val url = token.origin.newBuilder()
            .addPathSegment("ttranslatev3")
            .addQueryParameter("isVertical", "1")
            .addQueryParameter("IG", token.ig)
            .addQueryParameter("IID", if (count == 0) token.iid else "${token.iid}.$count")
            .build()
        val form = FormBody.Builder()
            .add("fromLang", from)
            .add("to", to)
            .add("text", text)
            .add("token", token.token)
            .add("key", token.key)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", token.origin.newBuilder().addPathSegment("translator").build().toString())
            .post(form)
            .build()
        return parseTranslation(client.newCall(request).await { it.successBody() })
    }

    /** The kept token while it lasts; otherwise the page being fetched, or a new fetch. */
    private suspend fun token(renew: Boolean): BingToken {
        val job = synchronized(this) {
            if (renew) token = null
            token?.takeIf { it.expiresAt > clock() }?.let { return it }
            fetching?.takeIf { it.isActive } ?: scope.async { fetchToken() }.also { fetching = it }
        }
        return job.await()
    }

    private suspend fun fetchToken(): BingToken {
        val request = Request.Builder().url(PAGE_URL).header("User-Agent", BROWSER_USER_AGENT).build()
        val (page, url) = pageClient.newCall(request).await { it.successBody() to it.request.url }
        // The page may redirect to a regional host; the translation goes there too.
        return parseTokenPage(page, url, clock()).also { synchronized(this) { token = it } }
    }

    internal class BingToken(
        val origin: HttpUrl,
        val ig: String,
        val iid: String,
        val key: String,
        val token: String,
        val expiresAt: Long,
    ) {
        /** Requests sent with this token; the page numbers them in `IID`. */
        val requests = AtomicInteger()
    }

    /** The service did not accept the token. */
    internal class TokenRejectedException : Exception()

    /** Cookies of the translator page, sent back with the translations. */
    private class MemoryCookieJar(private val clock: () -> Long) : CookieJar {
        private val cookies = mutableMapOf<Triple<String, String, String>, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[Triple(it.name, it.domain, it.path)] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = clock()
            cookies.values.removeAll { it.expiresAt < now }
            return cookies.values.filter { it.matches(url) }
        }
    }

    companion object {
        private const val PAGE_URL = "https://www.bing.com/translator"
        private const val PAGE_TIMEOUT_MS = 20_000L
        private const val MAX_LENGTH = 1000

        /** A token is renewed this long before the page says it expires. */
        private const val EXPIRY_MARGIN_MS = 60_000L

        private val IG = Regex("""IG:"([^"]+)"""")
        private val IID = Regex("""data-iid="([^"]+)"""")
        private val HELPER = Regex("""params_AbusePreventionHelper\s*=\s*\[\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*(\d+)""")

        /** The token in the translator [page] loaded from [url]; it lasts as long as the page says from [now] on. */
        internal fun parseTokenPage(page: String, url: HttpUrl, now: Long): BingToken {
            val ig = IG.find(page)?.groupValues?.get(1) ?: badAnswer()
            val iid = IID.find(page)?.groupValues?.get(1) ?: badAnswer()
            val helper = HELPER.find(page)?.groupValues ?: badAnswer()
            val lifetime = helper[3].toLongOrNull() ?: badAnswer()
            val origin = HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).build()
            return BingToken(origin, ig, iid, helper[1], helper[2], now + lifetime - EXPIRY_MARGIN_MS)
        }

        /** A translation, a captcha, or a refused token (`{"statusCode": …}`). */
        internal fun parseTranslation(body: String): String {
            val answer = parseAnswer { Json.parseToJsonElement(body) }
            if (answer is JsonObject) {
                if ((answer["ShowCaptcha"] as? JsonPrimitive)?.booleanOrNull == true) {
                    throw TranslationException(TranslationError.Kind.CAPTCHA)
                }
                if (answer["statusCode"] != null) throw TokenRejectedException()
                badAnswer()
            }
            return MicrosoftAnswer.parse(body)
        }
    }
}
