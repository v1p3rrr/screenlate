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
                "addView" -> shown += args[0] as View
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

    @Test
    fun cancelClosesTheEditorWithoutAPicture() = runBlocking(Dispatchers.Main) {
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(screenshot, 0f, 0f, null) }
        assertThat(shown).hasSize(1)

        editor.cancel()
        assertThat(picture.await()).isNull()
        assertThat(shown).isEmpty()
        assertThat(screenshot.isRecycled).isFalse()
    }

    @Test
    fun addReturnsANewBitmap() = runBlocking(Dispatchers.Main) {
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(screenshot, 0f, 0f, null) }
        button(shown.single(), R.string.crop_add).performClick()

        val result = picture.await()
        assertThat(result).isNotNull()
        assertThat(result).isNotSameInstanceAs(screenshot)
        assertThat(shown).isEmpty()
    }

    @Test
    fun aSecondEditorClosesTheFirstAsCancelled() = runBlocking(Dispatchers.Main) {
        val first = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(screenshot, 0f, 0f, null) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(screenshot, 0f, 0f, null) }

        assertThat(first.await()).isNull()
        assertThat(shown).hasSize(1)
        button(shown.single(), R.string.crop_cancel).performClick()
        assertThat(second.await()).isNull()
        assertThat(shown).isEmpty()
    }

    @Test
    fun cancellingTheNoteClosesTheEditor() = runBlocking(Dispatchers.Main) {
        val note = launch(start = CoroutineStart.UNDISPATCHED) { editor.edit(screenshot, 0f, 0f, null) }
        val window = shown.single()

        note.cancel()
        assertThat(withTimeout(5.seconds) { removed.await() }).isSameInstanceAs(window)
        assertThat(shown).isEmpty()
        // Nothing is left to close.
        editor.cancel()
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
