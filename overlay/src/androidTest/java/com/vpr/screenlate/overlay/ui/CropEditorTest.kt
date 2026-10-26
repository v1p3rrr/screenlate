package com.vpr.screenlate.overlay.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.overlay.R
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CropEditorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shown = mutableListOf<View>()
    private val removed = CompletableDeferred<View>()

    /** Records the editor's windows instead of adding them, which needs an accessibility service. */
    private val windowManager: WindowManager = run {
        val real = context.getSystemService(WindowManager::class.java)
        Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView" -> {
                    val view = args[0] as View
                    shown += view
                    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, 1080, 1920)
                }
                "removeView" -> {
                    shown -= args[0] as View
                    removed.complete(args[0] as View)
                }
                else -> method.invoke(real, *(args ?: emptyArray()))
            }
        } as WindowManager
    }
    private val editor = CropEditor(context, windowManager)
    private val screenshot = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, false)

    private val source = com.vpr.screenlate.overlay.anki.ScreenNoteImage(
        com.vpr.screenlate.overlay.capture.SharedScreenshot(com.vpr.screenlate.overlay.capture.CapturedScreen(screenshot, 0, 0)))

    @Test
    fun cancelClosesTheEditorWithoutAPicture() = runBlocking(Dispatchers.Main) {
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null) }
        assertThat(shown).hasSize(1)

        editor.cancel()
        assertThat(picture.await()).isNull()
        assertThat(shown).isEmpty()
        assertThat(screenshot.isRecycled).isFalse()
    }

    @Test
    fun addReturnsANewBitmap() = runBlocking(Dispatchers.Main) {
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null) }
        button(shown.single(), R.string.crop_add).performClick()

        val result = picture.await()
        assertThat(result).isNotNull()
        assertThat(result).isNotSameInstanceAs(screenshot)
        assertThat(shown).isEmpty()
    }

    @Test
    fun aSecondEditorClosesTheFirstAsCancelled() = runBlocking(Dispatchers.Main) {
        val first = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null) }

        assertThat(first.await()).isNull()
        assertThat(shown).hasSize(1)
        button(shown.single(), R.string.crop_cancel).performClick()
        assertThat(second.await()).isNull()
        assertThat(shown).isEmpty()
    }

    @Test
    fun cancellingTheNoteClosesTheEditor() = runBlocking(Dispatchers.Main) {
        val note = launch(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null) }
        val window = shown.single()

        note.cancel()
        assertThat(withTimeout(5.seconds) { removed.await() }).isSameInstanceAs(window)
        assertThat(shown).isEmpty()
        // Nothing is left to close.
        editor.cancel()
    }

    @Test
    fun closingTheScanDuringPreviewDecodeCannotOpenALateEditor() = runBlocking(Dispatchers.Main) {
        val gate = CompletableDeferred<Unit>()
        val delayed = object : com.vpr.screenlate.overlay.anki.NoteImage by source {
            override suspend fun preview(maxWidth: Int, maxHeight: Int): Bitmap { gate.await(); return screenshot }
        }
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(delayed, null) }
        assertThat(shown).isEmpty()
        editor.cancel()
        gate.complete(Unit)
        assertThat(picture.await()).isNull()
        assertThat(shown).isEmpty()
    }

    @Test
    fun aFittedPhotoReturnsOriginalPixelSelectionRatherThanPreviewPixels() = runBlocking(Dispatchers.Main) {
        var selection: com.vpr.screenlate.core.common.geometry.Box? = null
        val photo = object : com.vpr.screenlate.overlay.anki.NoteImage by source {
            override val bounds = com.vpr.screenlate.core.common.geometry.Box(0f, 0f, 8000f, 6000f)
            override val fit = true
            override suspend fun crop(box: com.vpr.screenlate.core.common.geometry.Box): Bitmap {
                selection = box
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(photo, android.graphics.RectF(2000f, 1500f, 6000f, 4500f))
        }
        button(shown.single(), R.string.crop_add).performClick()
        picture.await()!!.recycle()
        assertThat(selection!!.width).isWithin(0.01f).of(4000f)
        assertThat(selection!!.left).isWithin(0.01f).of(2000f)
        assertThat(selection!!.height).isWithin(0.01f).of(3000f)
    }

    private fun button(root: View, text: Int): TextView {
        val label = context.getString(text)
        fun find(view: View): TextView? = when {
            view is TextView && view.text == label -> view
            view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        return checkNotNull(find(root)) { "No button $label" }
    }
}
