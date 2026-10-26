package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.photos.LookupSource
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoViewport
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class PhotoViewerGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pinchAndPanMoveOnlyTheViewportAndReopeningFitsAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("photo-gesture-", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val recognition = PhotoRecognition(Language.ENGLISH, PhotoOcrSettings(OcrEngines.CLOUD),
            OcrPage(3200, 2400, emptyList(), OcrEngineType.LENS), recognizedAt = 0)
        val document = PhotoDocument(1, file, 3200, 2400, PhotoOrientation.NORMAL, 0, 1, PhotoText.Recognized(recognition))
        val shown = AtomicReference<PhotoViewport?>(null)
        val visible = mutableStateOf(true)
        val sources = LookupSources()
        val lookup = CameraLookup(sources)
        lookup.onForeground()
        try {
            compose.setContent {
                ScreenlateTheme {
                    if (visible.value) PhotoViewer(document) { _, viewport ->
                        shown.set(viewport)
                        lookup.show(if (viewport == null) CameraView.Nothing else CameraView.Photo(document, viewport))
                    }
                }
            }
            compose.waitUntil(5000) { shown.get() != null }
            val fitted = shown.get()!!
            val generation = sources.source.value.generation
            compose.onRoot().performTouchInput {
                pinch(Offset(center.x - width * 0.08f, center.y), Offset(center.x + width * 0.08f, center.y),
                    Offset(center.x - width * 0.3f, center.y), Offset(center.x + width * 0.3f, center.y), 600)
            }
            compose.waitUntil(5000) { shown.get()!!.scale > fitted.scale * 2 }
            val zoomed = shown.get()!!
            compose.onRoot().performTouchInput { swipe(center, Offset(center.x + width * 0.15f, center.y), 400) }
            compose.waitUntil(5000) { shown.get()!!.offsetX > zoomed.offsetX }
            assertThat(sources.source.value.generation).isEqualTo(generation)
            assertThat((sources.source.value as LookupSource.Photo).recognition).isSameInstanceAs(recognition)
            compose.runOnIdle { visible.value = false }
            compose.waitUntil(5000) { shown.get() == null }
            compose.runOnIdle { visible.value = true }
            compose.waitUntil(5000) { shown.get() != null }
            assertThat(shown.get()).isEqualTo(fitted)
        } finally { lookup.onBackground(); file.delete() }
    }
}
