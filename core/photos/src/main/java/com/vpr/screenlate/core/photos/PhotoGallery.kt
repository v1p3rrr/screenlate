package com.vpr.screenlate.core.photos

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Explicit export of an ordinary original image; independent of private document/history lifetime. */
fun interface PhotoExports { suspend fun save(documentId: Long) }

class PhotoGallery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val documents: PhotoDocuments,
) : PhotoExports {
    override suspend fun save(documentId: Long) {
        val image = documents.pinImage(documentId) ?: throw IOException("Photo image unavailable")
        image.use { saveImage(it) }
    }

    internal suspend fun saveImage(image: PhotoImage): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = image.mimeType()
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val name = "Screenlate_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenlate")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        publishImage(object : ImagePublication<Uri> {
            override fun create() = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IOException("Cannot create gallery image")
            override fun open(item: Uri) = resolver.openOutputStream(item) ?: throw IOException("Cannot write gallery image")
            override fun publish(item: Uri) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(item, ready, null, null) != 1) throw IOException("Cannot publish gallery image")
            }
            override fun remove(item: Uri) { resolver.delete(item, null, null) }
        }, image::copyTo)
    }
}

/** Internal transaction seam: pending rows are removed on write, close, publish or cancellation failure. */
internal interface ImagePublication<T> {
    fun create(): T
    fun open(item: T): OutputStream
    fun publish(item: T)
    fun remove(item: T)
}

internal suspend fun <T> publishImage(target: ImagePublication<T>, copy: suspend (OutputStream) -> Unit): T {
    val item = target.create()
    try {
        target.open(item).use { copy(it) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        target.publish(item)
        return item
    } catch (e: Exception) {
        withContext(NonCancellable) { try { target.remove(item) } catch (_: Exception) { /* Preserve the original failure. */ } }
        throw e
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoExportModule {
    @Binds abstract fun bindExports(gallery: PhotoGallery): PhotoExports
}
