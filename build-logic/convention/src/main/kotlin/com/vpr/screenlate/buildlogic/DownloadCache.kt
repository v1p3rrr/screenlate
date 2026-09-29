package com.vpr.screenlate.buildlogic

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Downloaded files kept in [dir] by name. The URL of each file is stored next to it (`<name>.url`), so a file
 * cached from another URL is downloaded again. A `.zip` name must receive a readable zip archive.
 */
internal class DownloadCache(private val dir: File, private val timeoutMillis: Int = TIMEOUT_MILLIS) {
    /** The cached file [name] from [url]; [onDownload] is called before a download. */
    fun get(name: String, url: String, onDownload: () -> Unit = {}): File {
        dir.mkdirs()
        val file = File(dir, name)
        val source = File(dir, "$name.url")
        if (!file.exists() || !source.exists() || source.readText() != url) {
            onDownload()
            fetch(url, file)
            source.writeText(url)
        }
        return file
    }

    private fun fetch(url: String, destination: File) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        var location = url
        repeat(MAX_REDIRECTS) {
            val connection = URI(location).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("User-Agent", "screenlate-build")
            try {
                when (val code = connection.responseCode) {
                    in 300..399 -> {
                        val next = connection.getHeaderField("Location")
                            ?: error("Download of $url: HTTP $code without a Location header")
                        location = URI(location).resolve(next).toString()
                    }
                    HttpURLConnection.HTTP_OK -> {
                        connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                        if (destination.name.endsWith(".zip") && !isZip(partial)) {
                            partial.delete()
                            error("Download of $url is not a zip archive")
                        }
                        Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        return
                    }
                    else -> error("Download of $url failed with HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects for $url")
    }

    private fun isZip(file: File): Boolean = runCatching { ZipFile(file).use { it.size() > 0 } }.getOrDefault(false)

    private companion object {
        const val MAX_REDIRECTS = 10
        const val TIMEOUT_MILLIS = 60_000
    }
}
