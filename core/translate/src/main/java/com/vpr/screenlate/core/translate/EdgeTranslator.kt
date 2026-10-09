package com.vpr.screenlate.core.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The translator of the Edge browser: Microsoft's language codes and answer shape, no token. */
internal class EdgeTranslator(client: OkHttpClient) : Translator {
    private val client = client.freshConnections().build()

    override val service = TranslationService.EDGE
    override val maxLength = MAX_LENGTH

    override fun code(language: TranslationLanguage): String? = language.microsoft

    override suspend fun translate(text: String, from: String, to: String): String {
        val url = URL.toHttpUrl().newBuilder()
            .addQueryParameter("from", from)
            .addQueryParameter("to", to)
            .addQueryParameter("isEnterpriseClient", "false")
            .build()
        val body = buildJsonArray { add(JsonPrimitive(text)) }.toString().toRequestBody(JSON)
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_USER_AGENT).post(body).build()
        return MicrosoftAnswer.parse(client.newCall(request).await { it.successBody() })
    }

    private companion object {
        const val URL = "https://edge.microsoft.com/translate/translatetext"
        const val MAX_LENGTH = 5000
        val JSON = "application/json".toMediaType()
    }
}

/** The answer of Microsoft's translators, Bing's and Edge's: `[{"translations": [{"text": …}]}, …]`. */
internal object MicrosoftAnswer {
    fun parse(body: String): String = parseAnswer {
        val first = Json.parseToJsonElement(body).jsonArray[0] as? JsonObject ?: badAnswer()
        val translations = first["translations"] as? JsonArray ?: badAnswer()
        (translations[0].jsonObject["text"] as? JsonPrimitive)?.content ?: badAnswer()
    }
}
