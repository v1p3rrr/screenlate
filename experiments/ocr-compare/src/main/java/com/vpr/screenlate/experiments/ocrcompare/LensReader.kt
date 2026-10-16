package com.vpr.screenlate.experiments.ocrcompare

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.lens.LensOcrEngine
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

/**
 * Cloud recognition as the app calls it ([LensOcrEngine]). The app's [Language] has only some of the compared
 * languages, so the request goes out as English and an interceptor puts [language] (an ISO code) into its locale.
 */
class LensReader(language: String) : Reader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val body = request.body!!
            val bytes = Buffer().also(body::writeTo).readByteArray()
            val localized = replaceField(bytes, LOCALE_LANGUAGE, language.toByteArray())
            chain.proceed(request.newBuilder().post(localized.toRequestBody(body.contentType())).build())
        }
        .build()
    private val engine = LensOcrEngine(client)

    override fun recognize(image: Bitmap): OcrPage = runBlocking { engine.recognize(image, Language.ENGLISH) }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private companion object {
        /** objects_request > request_context > client_context > locale_context > language (`lens-protocol.md`). */
        val LOCALE_LANGUAGE = listOf(1, 1, 4, 4, 1)
    }
}

/**
 * [message] with the length-delimited field at [path] (field numbers from the outermost message inward) set to
 * [value]; every other field is copied as it is.
 */
internal fun replaceField(message: ByteArray, path: List<Int>, value: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    var position = 0
    while (position < message.size) {
        val start = position
        val (tag, afterTag) = readVarint(message, position)
        position = afterTag
        when (val type = (tag and 7).toInt()) {
            0 -> position = readVarint(message, position).second
            1 -> position += 8
            5 -> position += 4
            2 -> {
                val (length, content) = readVarint(message, position)
                position = content + length.toInt()
                if ((tag ushr 3).toInt() == path.first()) {
                    val inner = message.copyOfRange(content, position)
                    val replaced = if (path.size == 1) value else replaceField(inner, path.drop(1), value)
                    writeVarint(out, tag)
                    writeVarint(out, replaced.size.toLong())
                    out.write(replaced)
                    continue
                }
            }
            else -> error("Unsupported wire type $type")
        }
        out.write(message, start, position - start)
    }
    return out.toByteArray()
}

private fun readVarint(bytes: ByteArray, from: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var position = from
    while (true) {
        val byte = bytes[position++].toInt()
        result = result or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) return result to position
        shift += 7
    }
}

private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var rest = value
    while (rest and 0x7FL.inv() != 0L) {
        out.write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    out.write(rest.toInt())
}
