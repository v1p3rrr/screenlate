package com.vpr.screenlate.core.ocr.lens

import com.vpr.screenlate.diagnostics.Diag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens the connection to a host before a request that may follow, so that the request does not wait for the
 * handshake. [open] sends a request without content and calls back once it has an answer or failed.
 *
 * Nothing is opened while one is opening or within [interval] ms of the last contact with the host, whose connection
 * is still open then.
 */
internal class Preconnection(
    private val interval: Long,
    private val clock: () -> Long,
    private val open: (done: () -> Unit) -> Unit,
) {
    private var lastContact: Long? = null
    private var opening: CompletableDeferred<Unit>? = null

    fun start() {
        val done = synchronized(this) {
            val now = clock()
            if (opening != null || lastContact?.let { now - it < interval } == true) {
                Diag.count(if (opening != null) "lens_preconnect_opening" else "lens_preconnect_recent") // TEMPORARY DIAGNOSTICS BUILD
                return
            }
            lastContact = now
            CompletableDeferred<Unit>().also { opening = it }
        }
        open {
            synchronized(this) {
                lastContact = clock()
                opening = null
            }
            done.complete(Unit)
        }
    }

    /** A request reached the host; its connection stays open for a while. */
    fun contacted() = synchronized(this) { lastContact = clock() }

    /** Waits up to [timeout] ms for a connection being opened, which the caller's request then reuses. */
    suspend fun awaitOpening(timeout: Long) {
        val opening = synchronized(this) { opening } ?: return
        withTimeoutOrNull(timeout) { opening.await() }
    }
}
