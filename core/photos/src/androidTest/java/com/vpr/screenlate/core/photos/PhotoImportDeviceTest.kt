package com.vpr.screenlate.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoImportDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aJpegKeepsItsBytesAndOrientation() = runBlocking {
        val source = File.createTempFile("import-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val target = File.createTempFile("import-target-", ".capture", context.cacheDir)
        try {
            PhotoImporter(context).copy(Uri.fromFile(source).toString(), target)
            assertThat(target.readBytes()).isEqualTo(source.readBytes())
        } finally {
            source.delete()
            target.delete()
        }
    }

    @Test
    fun aBitmapFileBecomesAPngTheViewerReads() = runBlocking {
        val source = File.createTempFile("import-source-", ".bmp", context.cacheDir).apply { writeBytes(bmp(5, 3)) }
        val target = File.createTempFile("import-target-", ".capture", context.cacheDir)
        try {
            assertThat(PlatformImageFormats.readable(source)).isFalse()
            PhotoImporter(context).copy(Uri.fromFile(source).toString(), target)
            assertThat(PlatformImageFormats.readable(target)).isTrue()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            assertThat(bounds.outMimeType).isEqualTo("image/png")
            assertThat(bounds.outWidth to bounds.outHeight).isEqualTo(5 to 3)
            val pixels = BitmapFactory.decodeFile(target.path)
            assertThat(pixels.getPixel(0, 0)).isEqualTo(Color.RED)
            pixels.recycle()
        } finally {
            source.delete()
            target.delete()
        }
    }

    @Test
    fun textIsNoImage() = runBlocking {
        val source = File.createTempFile("import-source-", ".txt", context.cacheDir).apply { writeText("not an image") }
        val target = File.createTempFile("import-target-", ".capture", context.cacheDir)
        try {
            try {
                PhotoImporter(context).copy(Uri.fromFile(source).toString(), target)
                error("Expected failure")
            } catch (_: InvalidPhotoException) { }
            assertThat(target.exists()).isFalse()
        } finally {
            source.delete()
            target.delete()
        }
    }

    /** A 24-bit BMP, red everywhere. */
    private fun bmp(width: Int, height: Int): ByteArray {
        val row = (width * 3 + 3) / 4 * 4
        val size = 54 + row * height
        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte()); put('M'.code.toByte()); putInt(size); putInt(0); putInt(54)
            putInt(40); putInt(width); putInt(height); putShort(1); putShort(24); putInt(0); putInt(row * height)
            putInt(2835); putInt(2835); putInt(0); putInt(0)
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            repeat(height) {
                val line = ByteArray(row)
                for (x in 0 until width) line[x * 3 + 2] = 0xFF.toByte()
                write(line)
            }
        }.toByteArray()
    }
}
