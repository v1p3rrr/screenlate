package com.vpr.screenlate.dictionary.api.imports

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads into a file and continues a partial one with an HTTP range request. The first response's ETag or
 * Last-Modified is kept in a [sidecar] file and sent back as If-Range, so a file that changed on the server starts over
 * instead of being spliced; a server without either starts over too. The sidecar also records a finished download, so
 * a run started again after the app died during the import does not download the file again.
 */
internal class ResumableDownload(private val client: OkHttpClient) {
    /** An HTTP status that another attempt would not change: 4xx except 408 (timeout) and 429 (too many requests). */
    class FinalStatusException(val code: Int) : IOException("HTTP $code")

    /**
     * Downloads [url] into [file], continuing what it holds; [progress] gets the bytes in the file and the total, -1
     * when unknown. Throws [IOException] when the download breaks off; what arrived stays for the next call.
     */
    suspend fun fetch(url: String, file: File, sidecar: File, progress: suspend (Long, Long) -> Unit) {
        if (isComplete(file, sidecar)) return
        val validator = readValidator(sidecar)
        val existing = if (file.exists()) file.length() else 0L
        if (existing > 0 && validator != null) {
            val request = Request.Builder().url(url).header("Range", "bytes=$existing-").header("If-Range", validator).build()
            client.newCall(request).execute().use { response ->
                // 416: the range is past the end, e.g. the file got shorter; anything else unexpected starts over too.
                if (response.code == HTTP_PARTIAL && contentRangeStart(response.header("Content-Range")) == existing) {
                    write(response, file, sidecar, append = true, existing, progress)
                    return
                }
                if (response.code == HTTP_OK) {
                    write(response, file, sidecar, append = false, 0L, progress)
                    return
                }
                if (response.code != HTTP_RANGE_NOT_SATISFIABLE && response.code != HTTP_PARTIAL) check(response)
            }
        }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response)
            write(response, file, sidecar, append = false, 0L, progress)
        }
    }

    private fun check(response: Response) {
        if (response.isSuccessful) return
        val code = response.code
        if (code in 400..499 && code != 408 && code != 429) throw FinalStatusException(code)
        throw IOException("HTTP $code")
    }

    private suspend fun write(
        response: Response,
        file: File,
        sidecar: File,
        append: Boolean,
        start: Long,
        progress: suspend (Long, Long) -> Unit,
    ) {
        val body = response.body
        val total = if (append) {
            contentRangeTotal(response.header("Content-Range")) ?: body.contentLength().takeIf { it >= 0 }?.plus(start) ?: -1L
        } else {
            body.contentLength()
        }
        // Written before the body, so that a download cut short by the app dying can continue.
        if (!append) sidecar.writeText(validatorOf(response).orEmpty())
        var copied = start
        body.byteStream().use { input ->
            FileOutputStream(file, append).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                progress(copied, total)
                while (true) {
                    // Progress may not change for long (unknown size), so it cannot be what notices a cancel.
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    progress(copied, total)
                }
            }
        }
        if (total >= 0 && copied != total) throw IOException("Download ended at $copied of $total bytes")
        sidecar.writeText(readValidator(sidecar).orEmpty() + "\n" + COMPLETE)
    }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val COMPLETE = "complete"

        /** Whether [file] holds a download that finished. */
        fun isComplete(file: File, sidecar: File): Boolean =
            file.exists() && sidecar.exists() && sidecar.readLines().getOrNull(1) == COMPLETE

        private fun readValidator(sidecar: File): String? =
            sidecar.takeIf { it.exists() }?.readLines()?.firstOrNull()?.takeIf { it.isNotBlank() }

        /** A strong ETag, else Last-Modified: If-Range takes no weak ETag. */
        private fun validatorOf(response: Response): String? =
            response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")

        /** The first byte of `bytes 100-199/200`. */
        internal fun contentRangeStart(header: String?): Long? =
            header?.removePrefix("bytes ")?.substringBefore('-')?.trim()?.toLongOrNull()

        /** The total of `bytes 100-199/200`; null for `*` or a header that does not parse. */
        internal fun contentRangeTotal(header: String?): Long? = header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
    }
}
