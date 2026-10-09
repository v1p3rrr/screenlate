package com.vpr.screenlate.core.translate

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The free translation services, all without a key. */
enum class TranslationService(val label: String) {
    /** Bing Translator's web page: a token from the page, then the translation. Usually the most accurate. */
    BING("Bing"),

    /** Google's `gtx` endpoint. */
    GOOGLE("Google"),

    /** The translator built into the Edge browser; Microsoft's codes, no token. */
    EDGE("Edge"),
}

/** Why a service gave no translation, without the text; [code] is the HTTP status for [Kind.HTTP_STATUS]. */
data class TranslationError(val kind: Kind, val code: Int = 0) {
    enum class Kind {
        /** The host could not be found or reached. */
        OFFLINE,

        /** No answer in time. */
        TIMEOUT,

        /** The secure connection failed: a certificate the device does not trust (or a wrong clock) or TLS. */
        SECURE_CONNECTION,

        /** Another network failure. */
        NETWORK,

        HTTP_STATUS,

        /** The service limits requests (HTTP 401 or 429). */
        LIMITED,

        /** The service asks to prove a person is there. */
        CAPTCHA,

        /** The service refused the request, also with a new token. */
        REJECTED,

        /** The service does not translate into or from the language. */
        UNSUPPORTED_LANGUAGE,

        /** The text is longer than the service takes. */
        TOO_LONG,

        /** The answer has an unexpected shape: the service may have changed. */
        BAD_ANSWER,

        OTHER,
    }

    companion object {
        fun of(error: Throwable): TranslationError = when (error) {
            is TranslationException -> error.error
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> TranslationError(Kind.OFFLINE)
            is InterruptedIOException -> TranslationError(Kind.TIMEOUT)
            is SSLException -> TranslationError(Kind.SECURE_CONNECTION)
            is IOException -> TranslationError(Kind.NETWORK)
            else -> TranslationError(Kind.OTHER)
        }
    }
}

internal class TranslationException(val error: TranslationError) : IOException(error.toString()) {
    constructor(kind: TranslationError.Kind, code: Int = 0) : this(TranslationError(kind, code))
}
