package com.vpr.screenlate.overlay.capture

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Tracks one foreground consent request. Used from the main thread. */
internal class CaptureConsent<T> {
    private class Request<T>(val id: String) {
        val result = CompletableDeferred<T?>()
        var close: (() -> Unit)? = null
    }

    private var pending: Request<T>? = null

    suspend fun request(timeoutMillis: Long, launch: (String) -> Unit): T? {
        check(pending == null)
        val request = Request<T>(UUID.randomUUID().toString())
        pending = request
        return try {
            launch(request.id)
            withTimeoutOrNull(timeoutMillis) { request.result.await() }
        } finally {
            if (pending === request) pending = null
            request.close?.invoke()
        }
    }

    fun attach(id: String, close: () -> Unit): Boolean {
        val request = pending?.takeIf { it.id == id && it.result.isActive } ?: return false
        request.close = close
        return true
    }

    fun detach(id: String, close: () -> Unit) {
        val request = pending?.takeIf { it.id == id } ?: return
        if (request.close === close) request.close = null
    }

    fun complete(id: String, result: T?) {
        pending?.takeIf { it.id == id }?.result?.complete(result)
    }

    fun cancel() {
        pending?.result?.complete(null)
    }
}
