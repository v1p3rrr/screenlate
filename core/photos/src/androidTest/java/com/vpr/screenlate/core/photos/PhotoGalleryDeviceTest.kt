package com.vpr.screenlate.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoGalleryDeviceTest {
    @Test
    fun ordinaryOriginalBytesAndOrientationArePublishedWithoutOcrData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (png in listOf(false, true)) {
            val file = File.createTempFile("gallery-fixture-", if (png) ".png" else ".jpg", context.cacheDir)
            val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            if (!png) ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
            }
            val expected = file.readBytes()
            PhotoImage(PhotoDocument(1, file, 80, 60, if (png) PhotoOrientation.NORMAL else PhotoOrientation.ROTATE_90,
                0, 0, PhotoText.None)).use { image ->
                val gallery = PhotoGallery(context, object : PhotoDocuments {
                    override val current = kotlinx.coroutines.flow.flowOf<PhotoDocument?>(null)
                    override suspend fun pinImage(documentId: Long): PhotoImage? = error("Uses owned fixture")
                    override suspend fun newCaptureFile(): File = error("Unused")
                    override suspend fun replaceCurrent(capture: File): PhotoDocument = error("Unused")
                    override suspend fun discard(capture: File) = Unit
                    override suspend fun clearCurrent() = Unit
                    override suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean) = false
                    override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? = null
                })
                val uri = gallery.saveImage(image)
                try {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    assertThat(bytes).isEqualTo(expected)
                    context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.MIME_TYPE), null, null, null)!!.use {
                        assertThat(it.moveToFirst()).isTrue()
                        assertThat(it.getInt(0)).isEqualTo(0)
                        assertThat(it.getString(1)).isEqualTo(if (png) "image/png" else "image/jpeg")
                    }
                } finally { context.contentResolver.delete(uri, null, null) }
            }
        }
    }
}
