package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.PhotoAssembly
import com.vpr.screenlate.core.photos.FailedRegion
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoText
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Leaves an owned synthetic book page current for manual overlay checks; never runs against a physical device. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class PhotoWorkflowTest {
    @Test
    fun realDeviceRecognitionPublishesStoredOriginalCoordinates() = runBlocking {
        assumeTrue("Synthetic photo replacement is emulator-only", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val documents = access.documents()
        if (Language.JAPANESE !in access.profiles().current().turnedOn) access.profiles().turnOn(Language.JAPANESE, activate = false)
        access.preferences().select(Language.JAPANESE)
        val capture = documents.newCaptureFile()
        val bitmap = Bitmap.createBitmap(2800, 2400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 66f }
            for (y in 150..2300 step 140) {
                drawText("これは日本語の写真です。", 100f, y.toFloat(), paint)
                drawText("これは日本語の写真です。", 1550f, y.toFloat(), paint)
            }
        }
        capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val photo = documents.replaceCurrent(capture)
        val partialCloud = InstrumentationRegistry.getArguments().getString("photoFixture") == "partialCloud"
        val settings = if (partialCloud) PhotoOcrSettings(OcrEngines.CLOUD) else PhotoOcrSettings(OcrEngines.DEVICE, "mlkit")
        access.jobs().recognize(photo, Language.JAPANESE, settings)
        // The work uses real engine threads and the process-owned scope, not this test's virtual-time scheduler.
        val stored = withTimeout(90_000) {
            documents.current.first { it?.id == photo.id && it.revision > photo.revision }!!
        }
        val recognized = (stored.text as PhotoText.Recognized).recognition
        assertThat(recognized.failed).isEmpty()
        assertThat(recognized.tiles).hasSize(4)
        assertThat(recognized.language).isEqualTo(Language.JAPANESE)
        assertThat(recognized.page.width to recognized.page.height).isEqualTo(2800 to 2400)
        assertThat(recognized.page.text.contains("日本語")).isTrue()
        val layout = TextLayout(recognized.page)
        assertThat(layout.paragraphs.flatten().size > 100).isTrue()
        val onRight = layout.paragraphs.flatten().first { it.text == "日" && it.box.left > 1500 }
        assertThat(layout.hitTest(onRight.box.centerX, onRight.box.centerY, 0f)?.paragraphIndex).isEqualTo(onRight.paragraphIndex)
        assertThat(documents.current.first()!!.attempt).isNull()
        if (partialCloud) {
            // Opt-in synthetic fixture for checking the real warning/retry UI with one missing cloud tile.
            val missing = recognized.tiles.last()
            val kept = recognized.tiles.dropLast(1)
            val partial = recognized.copy(
                page = PhotoAssembly.assemble(photo.width, photo.height, recognized.language, kept),
                tiles = kept, failed = listOf(FailedRegion(missing.tile.box, FailureCause.NETWORK)),
            )
            assertThat(documents.commitRecognition(photo.id, stored.revision, partial)).isNotNull()
        }
    }
}
