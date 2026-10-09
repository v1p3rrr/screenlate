package com.vpr.screenlate.core.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Google's `gtx` endpoint. The text goes in the POST body, so no URL carries it. */
internal class GoogleTranslator(client: OkHttpClient) : Translator {
    private val client = client.freshConnections().build()

    override val service = TranslationService.GOOGLE
    override val maxLength = MAX_LENGTH

    override fun code(language: TranslationLanguage): String? = language.google

    override suspend fun translate(text: String, from: String, to: String): String {
        val url = URL.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", from)
            .addQueryParameter("tl", to)
            .addQueryParameter("dt", "t")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(FormBody.Builder().add("q", text).build())
            .build()
        return parse(client.newCall(request).await { it.successBody() })
    }

    companion object {
        private const val URL = "https://translate.googleapis.com/translate_a/single"
        private const val MAX_LENGTH = 5000

        /** `[[["translated", "original", …], …], …]`: the translated parts of each sentence, joined. */
        internal fun parse(body: String): String = parseAnswer {
            val sentences = Json.parseToJsonElement(body).jsonArray[0] as? JsonArray ?: badAnswer()
            sentences.joinToString("") { sentence -> (sentence.jsonArray[0] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        }
    }
}
