package com.vpr.screenlate.buildlogic

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val requests = CopyOnWriteArrayList<String>()

    @After
    fun stop() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun serve(path: String, handler: (HttpExchange) -> Unit) = server.createContext(path) { exchange ->
        requests += exchange.requestURI.path
        try {
            handler(exchange)
        } finally {
            exchange.close()
        }
    }

    private fun HttpExchange.reply(code: Int, body: ByteArray = ByteArray(0)) {
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.write(body)
    }

    // A zip stores each entry's time; a fixed one keeps two archives of the same text the same bytes.
    private fun zip(text: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("index.json").apply { time = ENTRY_TIME })
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private val cache get() = DownloadCache(folder.root.resolve("cache"), timeoutMillis = 2_000)

    @Test
    fun `a cached file is not downloaded again`() {
        val archive = zip("one")
        serve("/one.zip") { it.reply(200, archive) }
        val first = cache.get("dict.zip", url("/one.zip"))
        val second = cache.get("dict.zip", url("/one.zip"))
        assertArrayEquals(archive, second.readBytes())
        assertEquals(first, second)
        assertEquals(listOf("/one.zip"), requests)
    }

    @Test
    fun `a changed url is downloaded again`() {
        serve("/old.zip") { it.reply(200, zip("old")) }
        serve("/new.zip") { it.reply(200, zip("new")) }
        cache.get("dict.zip", url("/old.zip"))
        val file = cache.get("dict.zip", url("/new.zip"))
        assertArrayEquals(zip("new"), file.readBytes())
        assertEquals(listOf("/old.zip", "/new.zip"), requests)
    }

    @Test
    fun `a file cached without its url is downloaded again`() {
        serve("/one.zip") { it.reply(200, zip("new")) }
        folder.root.resolve("cache").mkdirs()
        folder.root.resolve("cache/dict.zip").writeBytes(zip("old"))
        assertArrayEquals(zip("new"), cache.get("dict.zip", url("/one.zip")).readBytes())
    }

    @Test
    fun `relative redirects are followed`() {
        serve("/latest") {
            it.responseHeaders.add("Location", "/files/one.zip")
            it.reply(302)
        }
        serve("/files/one.zip") { it.reply(200, zip("one")) }
        assertArrayEquals(zip("one"), cache.get("dict.zip", url("/latest")).readBytes())
    }

    @Test
    fun `a redirect without a location names the url`() {
        serve("/latest") { it.reply(302) }
        val error = assertThrows(IllegalStateException::class.java) { cache.get("dict.zip", url("/latest")) }
        assertTrue(error.message!!, error.message!!.contains(url("/latest")))
    }

    @Test
    fun `a body that is not a zip is refused and not cached`() {
        serve("/api") { it.reply(200, """{"error":"rate limited"}""".toByteArray()) }
        assertThrows(IllegalStateException::class.java) { cache.get("dict.zip", url("/api")) }
        assertFalse(folder.root.resolve("cache/dict.zip").exists())
        assertFalse(folder.root.resolve("cache/dict.zip.part").exists())
    }

    @Test
    fun `a failed download keeps the file cached before`() {
        serve("/old.zip") { it.reply(200, zip("old")) }
        serve("/missing.zip") { it.reply(404) }
        cache.get("dict.zip", url("/old.zip"))
        assertThrows(IllegalStateException::class.java) { cache.get("dict.zip", url("/missing.zip")) }
        assertArrayEquals(zip("old"), folder.root.resolve("cache/dict.zip").readBytes())
    }

    @Test
    fun `other files are not checked`() {
        serve("/notes.txt") { it.reply(200, "text".toByteArray()) }
        assertEquals("text", cache.get("notes.txt", url("/notes.txt")).readText())
    }

    @Test
    fun `a stalled server times out`() {
        serve("/slow.zip") {
            Thread.sleep(5_000)
            it.reply(200, zip("late"))
        }
        val error = runCatching { cache.get("dict.zip", url("/slow.zip")) }.exceptionOrNull()
        assertTrue(error.toString(), error is java.net.SocketTimeoutException)
        assertFalse(File(folder.root, "cache/dict.zip").exists())
    }

    private companion object {
        const val ENTRY_TIME = 1_700_000_000_000L
    }
}
