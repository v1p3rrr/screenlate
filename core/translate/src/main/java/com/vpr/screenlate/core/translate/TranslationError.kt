package com.vpr.screenlate.core.translate

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
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

/**
 * Why a service gave no translation, without the text. [code] is the HTTP status for [Kind.HTTP_STATUS] and
 * [Kind.LIMITED]; [detail] tells the logs more (an answer's shape, a refusal's status) and never holds text.
 */
data class TranslationError(val kind: Kind, val code: Int = 0, val detail: String = "") {
    enum class Kind {
        /** The host could not be found or reached. */
        OFFLINE,

        /** No answer in time. */
        TIMEOUT,

        /** The server's certificate is not valid at the device's date: most likely the device's clock is wrong. */
        CERTIFICATE_DATE,

        /** The secure connection failed: a certificate the device does not trust, or TLS. */
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

    /** For the logs: `HTTP_STATUS 503`, `BAD_ANSWER (shape)`. */
    override fun toString(): String = buildString {
        append(kind)
        if (code != 0) append(' ').append(code)
        if (detail.isNotEmpty()) append(" (").append(detail).append(')')
    }

    companion object {
        fun of(error: Throwable): TranslationError = when (error) {
            is TranslationException -> error.error
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> TranslationError(Kind.OFFLINE)
            is InterruptedIOException -> TranslationError(Kind.TIMEOUT)
            is SSLException -> dateProblem(error)?.let { TranslationError(Kind.CERTIFICATE_DATE, detail = it) }
                ?: TranslationError(Kind.SECURE_CONNECTION)
            is IOException -> TranslationError(Kind.NETWORK)
            else -> TranslationError(Kind.OTHER)
        }

        /** A certificate among [error]'s causes that is not valid yet or no longer: that exception's name. */
        private fun dateProblem(error: Throwable): String? =
            generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }
                .take(MAX_CAUSES)
                .firstOrNull { it is CertificateNotYetValidException || it is CertificateExpiredException }
                ?.javaClass?.simpleName

        private const val MAX_CAUSES = 10
    }
}

internal class TranslationException(val error: TranslationError) : IOException(error.toString()) {
    constructor(kind: TranslationError.Kind, code: Int = 0) : this(TranslationError(kind, code))
}
