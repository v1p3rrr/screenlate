package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ResumableDownloadTest {
    private val server = MockWebServer()
    private val directory: File = Files.createTempDirectory("download").toFile()
    private val file = File(directory, "a.zip")
    private val state = File(directory, "a.zip.state")
    private val download = ResumableDownload(OkHttpClient())
    private val progress = mutableListOf<Pair<Long, Long>>()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        directory.deleteRecursively()
    }

    private val url get() = server.url("/dict.zip").toString()

    private fun fetch() = runBlocking { download.fetch(url, file, state) { copied, total -> progress += copied to total } }

    private fun ok(body: String, etag: String? = "\"v1\"") =
        MockResponse.Builder().code(200).body(body).apply { if (etag != null) addHeader("ETag", etag) }.build()

    @Test
    fun `a fresh download is written and remembered as finished`() {
        server.enqueue(ok("0123456789"))

        fetch()

        assertThat(file.readText()).isEqualTo("0123456789")
        assertThat(ResumableDownload.isComplete(file, state)).isTrue()
        assertThat(progress.last()).isEqualTo(10L to 10L)
        // A run after the app died during the import does not download it again.
        fetch()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a partial file continues with a range request`() {
        file.writeText("01234")
        state.writeText("\"v1\"")
        server.enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 5-9/10").body("56789").build())

        fetch()

        val request = server.takeRequest()
        assertThat(request.headers["Range"]).isEqualTo("bytes=5-")
        assertThat(request.headers["If-Range"]).isEqualTo("\"v1\"")
        assertThat(file.readText()).isEqualTo("0123456789")
        assertThat(progress.first()).isEqualTo(5L to 10L)
        assertThat(ResumableDownload.isComplete(file, state)).isTrue()
    }

    @Test
    fun `a changed file on the server starts over`() {
        file.writeText("old")
        state.writeText("\"v1\"")
        // If-Range did not match: the whole new file comes back.
        server.enqueue(ok("new file", etag = "\"v2\""))

        fetch()

        assertThat(file.readText()).isEqualTo("new file")
        assertThat(state.readLines().first()).isEqualTo("\"v2\"")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a range past the end starts over with a plain request`() {
        file.writeText("0123456789")
        state.writeText("\"v1\"")
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(ok("short"))

        fetch()

        server.takeRequest()
        assertThat(server.takeRequest().headers["Range"]).isNull()
        assertThat(file.readText()).isEqualTo("short")
    }

    @Test
    fun `without a validator a partial file starts over`() {
        file.writeText("01234")
        server.enqueue(ok("0123456789", etag = null))

        fetch()

        assertThat(server.takeRequest().headers["Range"]).isNull()
        assertThat(file.readText()).isEqualTo("0123456789")
    }

    @Test
    fun `a weak ETag is not used, Last-Modified is`() {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("ETag", "W/\"weak\"")
                .addHeader("Last-Modified", "Tue, 14 Oct 2026 10:00:00 GMT").body("abc").build(),
        )

        fetch()

        assertThat(state.readLines().first()).isEqualTo("Tue, 14 Oct 2026 10:00:00 GMT")
    }

    @Test
    fun `a download that breaks off keeps what arrived for the next try`() {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("ETag", "\"v1\"").body("0123456789")
                .onResponseBody(SocketEffect.ShutdownConnection).build(),
        )

        assertThrows(IOException::class.java) { fetch() }

        assertThat(ResumableDownload.isComplete(file, state)).isFalse()
        assertThat(state.readLines().first()).isEqualTo("\"v1\"")
    }

    @Test
    fun `client errors are final, server errors are not`() {
        server.enqueue(MockResponse.Builder().code(404).build())
        val notFound = assertThrows(ResumableDownload.FinalStatusException::class.java) { fetch() }
        assertThat(notFound.code).isEqualTo(404)

        server.enqueue(MockResponse.Builder().code(503).build())
        val unavailable = assertThrows(IOException::class.java) { fetch() }
        assertThat(unavailable).isNotInstanceOf(ResumableDownload.FinalStatusException::class.java)

        server.enqueue(MockResponse.Builder().code(429).build())
        assertThat(assertThrows(IOException::class.java) { fetch() })
            .isNotInstanceOf(ResumableDownload.FinalStatusException::class.java)
    }

    @Test
    fun `content ranges are read`() {
        assertThat(ResumableDownload.contentRangeStart("bytes 100-199/200")).isEqualTo(100L)
        assertThat(ResumableDownload.contentRangeTotal("bytes 100-199/200")).isEqualTo(200L)
        assertThat(ResumableDownload.contentRangeTotal("bytes 100-199/*")).isNull()
        assertThat(ResumableDownload.contentRangeStart(null)).isNull()
    }
}
