package com.vpr.screenlate.core.common

import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Logs must not carry recognized text, looked-up words or URLs with terms. Exception messages often do (HTTP clients
 * put the request URL there), so failures are logged through a copy that keeps the type and the stack trace only.
 */
fun Throwable.redacted(): Throwable = RedactedThrowable(this, Collections.newSetFromMap(IdentityHashMap()))

/** A URL reduced to its scheme and host, e.g. `https://example.com/…`. */
fun redactUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val scheme = uri?.scheme ?: return "<url>"
    val host = uri.host ?: return "$scheme:…"
    return "$scheme://$host/…"
}

/** [seen] holds the throwables of the chain copied so far: a chain of causes that loops ends at the first repeat. */
private class RedactedThrowable(original: Throwable, seen: MutableSet<Throwable>) :
    Throwable(
        original.javaClass.name,
        original.cause?.takeIf { seen.add(original) && it !in seen }?.let { RedactedThrowable(it, seen) },
    ) {
    init {
        stackTrace = original.stackTrace
    }

    override fun toString(): String = message.orEmpty()
}
