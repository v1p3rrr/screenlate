package com.vpr.screenlate.diagnostics

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response

/**
 * One `net` event per HTTP call of the shared client: the host (never the path or query, which may carry words), the
 * phases (DNS, connect, TLS, waiting for the first byte, the body), the bytes, whether a pooled connection was reused
 * and how long the host had been idle since the previous call to it.
 */
internal class NetworkEvents(private val clock: () -> Long = System::nanoTime) : EventListener.Factory {
    /** When the last call to each host ended, for the idle time of the next one. */
    private val lastEnd = ConcurrentHashMap<String, Long>()

    override fun create(call: Call): EventListener = CallEvents(call.request().url.host, call.request().method)

    private inner class CallEvents(private val host: String, private val method: String) : EventListener() {
        private var start = 0L
        private var dnsStart = 0L
        private var dnsMs: Long? = null
        private var connectStart = 0L
        private var connectMs: Long? = null
        private var tlsStart = 0L
        private var tlsMs: Long? = null
        private var requestEnd = 0L
        private var waitMs: Long? = null
        private var headersEnd = 0L
        private var bodyMs: Long? = null
        private var sent = 0L
        private var received = 0L
        private var code: Int? = null
        private var protocol: String? = null
        private var reused = true
        private var tlsVersion: String? = null
        private var scan = 0

        override fun callStart(call: Call) {
            start = clock()
            scan = Diag.currentScan()
        }

        override fun dnsStart(call: Call, domainName: String) {
            dnsStart = clock()
        }

        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
            dnsMs = ms(dnsStart)
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connectStart = clock()
            reused = false
        }

        override fun secureConnectStart(call: Call) {
            tlsStart = clock()
        }

        override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            tlsMs = ms(tlsStart)
            tlsVersion = handshake?.tlsVersion?.javaName
        }

        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
            connectMs = ms(connectStart)
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            protocol = connection.protocol().toString()
        }

        override fun requestHeadersEnd(call: Call, request: okhttp3.Request) {
            requestEnd = clock()
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            sent = byteCount
            requestEnd = clock()
        }

        override fun responseHeadersStart(call: Call) {
            if (requestEnd != 0L) waitMs = ms(requestEnd)
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            headersEnd = clock()
            code = response.code
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            received = byteCount
            if (headersEnd != 0L) bodyMs = ms(headersEnd)
        }

        override fun callEnd(call: Call) = end(null)

        override fun callFailed(call: Call, ioe: IOException) = end(ioe)

        private fun end(error: IOException?) {
            val now = clock()
            val idle = lastEnd.put(host, now)?.let { (start - it) / 1_000_000 }
            val total = (now - start) / 1_000_000
            val fields = arrayOf<Pair<String, Any?>>(
                "host" to host,
                "method" to method,
                "code" to code,
                "ms" to total,
                "dns_ms" to dnsMs,
                "connect_ms" to connectMs,
                "tls_ms" to tlsMs,
                "tls" to tlsVersion,
                "wait_ms" to waitMs,
                "body_ms" to bodyMs,
                "sent_b" to sent,
                "received_b" to received,
                "kbps" to bodyMs?.takeIf { it >= MIN_SPEED_MS }?.let { received * 8 / it },
                "reused" to reused,
                "idle_ms" to idle,
                "proto" to protocol,
                "error" to error?.javaClass?.simpleName,
                "scan" to scan.takeIf { it > 0 },
            )
            Diag.event("net", *fields)
            if (scan > 0) Diag.scanNet(scan, host, total, reused, waitMs, error?.javaClass?.simpleName)
        }

        private fun ms(since: Long) = (clock() - since) / 1_000_000
    }

    private companion object {
        /** Bodies read faster than this give no meaningful speed. */
        const val MIN_SPEED_MS = 200L
    }
}
