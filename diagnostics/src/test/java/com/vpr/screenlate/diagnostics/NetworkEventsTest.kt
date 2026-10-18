package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import java.util.Collections
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkEventsTest {
    private val server = MockWebServer()
    private val events = Collections.synchronizedList(mutableListOf<Map<String, Any?>>())
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        Diag.sink = { name, fields -> if (name == "net") events += fields.toMap() }
        client = OkHttpClient.Builder().eventListenerFactory(NetworkEvents()).build()
    }

    @After
    fun tearDown() {
        Diag.sink = null
        client.connectionPool.evictAll()
        server.close()
    }

    private fun get(path: String): String = client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }

    @Test
    fun `records the host, phases and bytes but not the path`() {
        server.enqueue(MockResponse.Builder().body("hello").build())
        get("/search?q=word")

        val event = events.single()
        assertThat(event["host"]).isEqualTo(server.hostName)
        assertThat(event["method"]).isEqualTo("GET")
        assertThat(event["code"]).isEqualTo(200)
        assertThat(event["received_b"]).isEqualTo(5L)
        assertThat(event["reused"]).isEqualTo(false)
        assertThat(event["connect_ms"]).isNotNull()
        assertThat(event["wait_ms"]).isNotNull()
        assertThat(event["idle_ms"]).isNull()
        assertThat(event.values.toString()).doesNotContain("word")
    }

    @Test
    fun `a second call reuses the connection and knows the idle time`() {
        server.enqueue(MockResponse.Builder().body("one").build())
        server.enqueue(MockResponse.Builder().body("two").build())
        get("/a")
        get("/b")

        assertThat(events).hasSize(2)
        assertThat(events[1]["reused"]).isEqualTo(true)
        assertThat(events[1]["connect_ms"]).isNull()
        assertThat(events[1]["idle_ms"]).isNotNull()
    }

    @Test
    fun `a failed call names the error`() {
        val url = server.url("/gone")
        server.close()
        runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() }

        assertThat(events.single()["error"]).isNotNull()
    }
}
