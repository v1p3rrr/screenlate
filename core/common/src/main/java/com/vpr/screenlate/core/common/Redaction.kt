package com.vpr.screenlate.core.common

import java.net.URI

/**
 * Logs must not carry recognized text, looked-up words or URLs with terms. Exception messages often do (HTTP clients
 * put the request URL there), so failures are logged through a copy that keeps the type and the stack trace only.
 */
fun Throwable.redacted(): Throwable = RedactedThrowable(this)

/** A URL reduced to its scheme and host, e.g. `https://example.com/…`. */
fun redactUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val scheme = uri?.scheme ?: return "<url>"
    val host = uri.host ?: return "$scheme:…"
    return "$scheme://$host/…"
}

private class RedactedThrowable(original: Throwable) :
    Throwable(original.javaClass.name, original.cause?.takeIf { it !== original }?.let(::RedactedThrowable)) {
    init {
        stackTrace = original.stackTrace
    }

    override fun toString(): String = message.orEmpty()
}
