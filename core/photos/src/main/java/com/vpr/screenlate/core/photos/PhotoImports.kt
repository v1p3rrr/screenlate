package com.vpr.screenlate.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Brings an image from the system photo picker or the share sheet into the photo store. */
fun interface PhotoImports {
    /**
     * Writes the image at [uri], a content link, into [target], a file from [PhotoDocuments.newCaptureFile]: its
     * original bytes when the photo viewer reads the format (JPEG, PNG, WebP, HEIF), else a PNG of its upright pixels.
     * Throws [InvalidPhotoException] when the phone cannot decode it.
     */
    suspend fun copy(uri: String, target: File)
}

class PhotoImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhotoImports {
    override suspend fun copy(uri: String, target: File) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("Cannot open the image")
        importImage(input, target, PlatformImageFormats)
    }
}

/** What the photo viewer and recognition can read, and the conversion of everything else. */
internal interface ImageFormats {
    /** [file] can be read by region, as the photo viewer and recognition do. */
    fun readable(file: File): Boolean

    /** Replaces [file] with a PNG of its upright pixels; false, leaving it as it was, when it cannot be decoded. */
    fun convert(file: File): Boolean
}

/** Copies [input] into [target] and converts it when needed; [target] is deleted on any failure. */
internal suspend fun importImage(input: InputStream, target: File, formats: ImageFormats) {
    try {
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (!formats.readable(target) && !formats.convert(target)) throw InvalidPhotoException()
    } catch (e: Exception) {
        target.delete()
        throw e
    }
}

internal object PlatformImageFormats : ImageFormats {
    /** Larger images are scaled down on conversion, so that decoding them fits in memory. */
    private const val MAX_PIXELS = 24_000_000L

    override fun readable(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        return try {
            @Suppress("DEPRECATION") // The non-shareable overload is available on minSdk 30.
            val decoder = BitmapRegionDecoder.newInstance(file.path, false)
            try {
                decoder.width == bounds.outWidth && decoder.height == bounds.outHeight
            } finally {
                decoder.recycle()
            }
        } catch (_: IOException) {
            false
        }
    }

    override fun convert(file: File): Boolean {
        val decoded = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                val pixels = info.size.width.toLong() * info.size.height
                if (pixels > MAX_PIXELS) {
                    val scale = sqrt(MAX_PIXELS.toDouble() / pixels)
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (_: IOException) {
            return false
        }
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() } ?: return false
        }
        val temp = File(file.path + ".png")
        try {
            val written = temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!written || !temp.renameTo(file)) throw IOException("Cannot store the converted image")
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return true
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoImportModule {
    @Binds abstract fun bindImports(importer: PhotoImporter): PhotoImports
}
