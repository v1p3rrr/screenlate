package com.vpr.screenlate.core.translate

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Response

/** One translation service. */
internal interface Translator {
    val service: TranslationService

    /** The longest text the service takes, in characters. */
    val maxLength: Int

    /** The service's code of [language]; null when the service does not have it. */
    fun code(language: TranslationLanguage): String?

    /** Translates [text] between the service's language codes [from] and [to]. */
    suspend fun translate(text: String, from: String, to: String): String
}

/**
 * Every request opens its own connection: some mobile networks freeze a connection to foreign hosting after its first
 * answers, and a request sent over such a kept-open connection hangs.
 */
internal fun OkHttpClient.freshConnections(): OkHttpClient.Builder =
    newBuilder().connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))

/** Runs the call on the IO dispatcher and reads its answer; cancelling the coroutine cancels the request. */
internal suspend fun <T> Call.await(read: (Response) -> T): T = withContext(Dispatchers.IO) {
    val call = this@await
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

/** The body of a successful answer; a refusal or another status becomes a [TranslationException]. */
internal fun Response.successBody(): String {
    when {
        code == HTTP_UNAUTHORIZED || code == HTTP_TOO_MANY_REQUESTS -> throw TranslationException(TranslationError.Kind.LIMITED, code)
        !isSuccessful -> throw TranslationException(TranslationError.Kind.HTTP_STATUS, code)
    }
    return body.string()
}

/** Reads an answer with [parse]; an answer of another shape is [TranslationError.Kind.BAD_ANSWER]. */
internal inline fun <T> parseAnswer(parse: () -> T): T = try {
    parse()
} catch (e: TranslationException) {
    throw e
} catch (e: SerializationException) {
    throw TranslationException(TranslationError.Kind.BAD_ANSWER)
} catch (e: IllegalArgumentException) {
    throw TranslationException(TranslationError.Kind.BAD_ANSWER)
} catch (e: IndexOutOfBoundsException) {
    throw TranslationException(TranslationError.Kind.BAD_ANSWER)
}

internal fun badAnswer(): Nothing = throw TranslationException(TranslationError.Kind.BAD_ANSWER)

/** A desktop browser's: the endpoints are the ones the services' web pages and browsers use. */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429
