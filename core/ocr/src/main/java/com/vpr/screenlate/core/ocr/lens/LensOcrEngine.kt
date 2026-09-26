package com.vpr.screenlate.core.ocr.lens

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrPage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OCR through the unofficial Google Lens endpoint used by Chrome's Lens overlay.
 *
 * The image is downscaled to [MAX_DIMENSION] and sent as JPEG. Tests showed no quality loss against full resolution
 * even for ~9 px glyphs, while the upload is about three times smaller.
 */
@Singleton
class LensOcrEngine @Inject constructor(
    private val client: OkHttpClient,
) : OcrEngine {

    override val type = OcrEngineType.LENS

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        val (jpeg, sentWidth, sentHeight) = withContext(Dispatchers.Default) { encode(image) }
        val body = LensProtocol.buildRequest(
            image = jpeg,
            width = sentWidth,
            height = sentHeight,
            language = language.code,
            requestId = Random.nextLong() and Long.MAX_VALUE,
        )
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("X-Goog-Api-Key", API_KEY)
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody(PROTOBUF))
            .build()
        val bytes = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw LensHttpException(response.code)
            response.body.bytes()
        }
        return withContext(Dispatchers.Default) { LensProtocol.parseResponse(bytes, image.width, image.height) }
    }

    private fun encode(image: Bitmap): Triple<ByteArray, Int, Int> {
        val longest = max(image.width, image.height)
        val scaled = if (longest > MAX_DIMENSION) {
            val factor = MAX_DIMENSION.toFloat() / longest
            image.scale((image.width * factor).roundToInt(), (image.height * factor).roundToInt())
        } else {
            image
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val result = Triple(out.toByteArray(), scaled.width, scaled.height)
        if (scaled !== image) scaled.recycle()
        return result
    }

    private companion object {
        const val ENDPOINT = "https://lensfrontend-pa.googleapis.com/v1/crupload"

        // Public key embedded in Chrome, taken from chrome-lens-ocr.
        const val API_KEY = "AIzaSyDr2UxVnv_U85AbhhY8XSHSIavUW0DC-sY"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MAX_DIMENSION = 1500
        const val JPEG_QUALITY = 85
        val PROTOBUF = "application/x-protobuf".toMediaType()
    }
}

/** Lens answered with an HTTP error; 429 and 403 mean it refuses requests for now. */
class LensHttpException(val code: Int) : IOException("Lens HTTP $code") {
    val refused: Boolean get() = code == 429 || code == 403
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) = continuation.resume(response)

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        },
    )
}
