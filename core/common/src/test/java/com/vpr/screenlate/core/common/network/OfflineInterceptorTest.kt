package com.vpr.screenlate.core.common.network

import com.google.common.truth.Truth.assertThat
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class OfflineInterceptorTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `requests proceed when offline mode is false`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        var offline = false
        val client = OkHttpClient.Builder()
            .addInterceptor(OfflineInterceptor { offline })
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/test")).build()).execute()
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body.string()).isEqualTo("ok")
    }

    @Test
    fun `requests throw OfflineModeException when offline mode is true for external hosts`() {
        var offline = true
        val client = OkHttpClient.Builder()
            .addInterceptor(OfflineInterceptor { offline })
            .build()

        val exception = assertThrows(OfflineModeException::class.java) {
            client.newCall(Request.Builder().url("https://example.com/test").build()).execute()
        }
        assertThat(exception.message).contains("Forced Offline Mode")
    }

    @Test
    fun `requests to localhost or loopback proceed even when offline mode is true`() {
        server.enqueue(MockResponse.Builder().code(200).body("local").build())
        var offline = true
        val client = OkHttpClient.Builder()
            .addInterceptor(OfflineInterceptor { offline })
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/local")).build()).execute()
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body.string()).isEqualTo("local")
    }
}
