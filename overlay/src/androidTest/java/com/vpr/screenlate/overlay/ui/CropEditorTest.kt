package com.vpr.screenlate.overlay.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Switch
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.settings.NormalizedCrop
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.anki.NoteImage
import com.vpr.screenlate.overlay.anki.ScreenNoteImage
import com.vpr.screenlate.overlay.capture.CapturedScreen
import com.vpr.screenlate.overlay.capture.SharedScreenshot
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val source = ScreenNoteImage(SharedScreenshot(CapturedScreen(screenshot, 0, 0)))

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
        val delayed = object : NoteImage by source {
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
        var selection: Box? = null
        val photo = object : NoteImage by source {
            override val bounds = Box(0f, 0f, 8000f, 6000f)
            override val fit = true
            override suspend fun crop(box: Box): Bitmap {
                selection = box
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(photo, Box(2000f, 1500f, 6000f, 4500f))
        }
        button(shown.single(), R.string.crop_add).performClick()
        picture.await()!!.recycle()
        assertThat(selection!!.width).isWithin(0.01f).of(4000f)
        assertThat(selection!!.left).isWithin(0.01f).of(2000f)
        assertThat(selection!!.height).isWithin(0.01f).of(3000f)
    }

    @Test
    fun resizingTheSameCropHostPreservesItsSourceFrame() = runBlocking(Dispatchers.Main) {
        var selection: Box? = null
        val image = object : NoteImage by source {
            override val bounds = Box(0f, 0f, 8000f, 6000f)
            override val fit = true
            override suspend fun crop(box: Box): Bitmap {
                selection = box
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(image, Box(2000f, 1500f, 6000f, 4500f))
        }
        val root = shown.single()
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1920, 1080)
        assertThat(shown).containsExactly(root)
        button(root, R.string.crop_add).performClick()
        picture.await()!!.recycle()
        assertThat(selection!!.left).isWithin(0.1f).of(2000f)
        assertThat(selection!!.top).isWithin(0.1f).of(1500f)
        assertThat(selection!!.width).isWithin(0.1f).of(4000f)
        assertThat(selection!!.height).isWithin(0.1f).of(3000f)
    }

    @Test
    fun wholePhotoSelectsAllOfItAndFrameReturnsToTheParagraph() = runBlocking(Dispatchers.Main) {
        val selections = mutableListOf<Box>()
        val photo = object : NoteImage by source {
            override val bounds = Box(0f, 0f, 8000f, 6000f)
            override val fit = true
            override suspend fun crop(selection: Box): Bitmap {
                selections += selection
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        val focus = Box(2000f, 1500f, 6000f, 4500f)
        val whole = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(photo, focus) }
        button(shown.single(), R.string.crop_whole_photo).performClick()
        button(shown.single(), R.string.crop_add).performClick()
        whole.await()!!.recycle()

        val frame = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(photo, focus) }
        button(shown.single(), R.string.crop_whole_photo).performClick()
        button(shown.single(), R.string.crop_frame).performClick()
        button(shown.single(), R.string.crop_add).performClick()
        frame.await()!!.recycle()

        assertThat(selections).hasSize(2)
        for ((selection, expected) in selections.zip(listOf(photo.bounds, focus))) {
            assertThat(selection.left).isWithin(0.5f).of(expected.left)
            assertThat(selection.top).isWithin(0.5f).of(expected.top)
            assertThat(selection.right).isWithin(0.5f).of(expected.right)
            assertThat(selection.bottom).isWithin(0.5f).of(expected.bottom)
        }
    }

    @Test
    fun rememberToggleSyncsAndSavesOnConfirmWithoutMovingCurrentFrame() = runBlocking(Dispatchers.Main) {
        val state = MutableStateFlow(false)
        var confirmed: Box? = null
        val focus = Box(4f, 6f, 24f, 20f)
        val remembered = NormalizedCrop(0.5f, 0.5f, 1f, 1f)
        val rememberControl = CropEditor.RememberControl(
            initial = false,
            remembered = remembered,
            updates = state,
            onChange = { state.value = it },
            onConfirm = { confirmed = it },
        )
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(source, focus, rememberControl)
        }
        val toggle = findSwitch(shown.single())!!
        assertThat(toggle.isChecked).isFalse()
        toggle.performClick()
        assertThat(state.value).isTrue()
        button(shown.single(), R.string.crop_add).performClick()
        picture.await()!!.recycle()

        // Toggling ON did not jump the current frame to remembered; it kept focus and confirmed it.
        val saved = checkNotNull(confirmed)
        assertThat(saved.left).isWithin(0.5f).of(focus.left)
        assertThat(saved.right).isWithin(0.5f).of(focus.right)
    }

    @Test
    fun rememberedAreaOpensPrefilledAndTurningOffSuppressesConfirmSave() = runBlocking(Dispatchers.Main) {
        val state = MutableStateFlow(true)
        var confirmed: Box? = null
        var croppedBox: Box? = null
        val tracking = object : NoteImage by source {
            override suspend fun crop(selection: Box): Bitmap {
                croppedBox = selection
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        val focus = Box(2f, 2f, 10f, 10f)
        val remembered = NormalizedCrop(0.5f, 0.5f, 1f, 1f) // Box(20, 15, 40, 30) on 40x30 source
        val rememberControl = CropEditor.RememberControl(
            initial = true,
            remembered = remembered,
            updates = state,
            onChange = { state.value = it },
            onConfirm = { confirmed = it },
        )
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(tracking, focus, rememberControl)
        }
        val toggle = findSwitch(shown.single())!!
        assertThat(toggle.isChecked).isTrue()
        // Turning off keeps the prefilled remembered frame for this crop, but skips saving on Add.
        toggle.performClick()
        assertThat(state.value).isFalse()

        button(shown.single(), R.string.crop_add).performClick()
        picture.await()!!.recycle()

        val used = checkNotNull(croppedBox)
        assertThat(used.left).isWithin(0.5f).of(20f)
        assertThat(used.top).isWithin(0.5f).of(15f)
        assertThat(confirmed).isNull()
    }

    @Test
    fun externalSettingsUpdateSyncsSwitchIntoOpenEditor() = runBlocking(Dispatchers.Main) {
        val state = MutableStateFlow(false)
        var confirmed: Box? = null
        val focus = Box(4f, 6f, 24f, 20f)
        val rememberControl = CropEditor.RememberControl(
            initial = false,
            remembered = NormalizedCrop(0.5f, 0.5f, 1f, 1f),
            updates = state,
            onChange = { state.value = it },
            onConfirm = { confirmed = it },
        )
        val picture = async(start = CoroutineStart.UNDISPATCHED) {
            editor.edit(source, focus, rememberControl)
        }
        val toggle = findSwitch(shown.single())!!
        assertThat(toggle.isChecked).isFalse()

        state.value = true
        kotlinx.coroutines.yield()
        assertThat(toggle.isChecked).isTrue()

        button(shown.single(), R.string.crop_add).performClick()
        picture.await()!!.recycle()

        val saved = checkNotNull(confirmed)
        assertThat(saved.left).isWithin(0.5f).of(focus.left)
        assertThat(saved.right).isWithin(0.5f).of(focus.right)
    }

    @Test
    fun noRememberToggleWhenControlIsNull() = runBlocking(Dispatchers.Main) {
        val picture = async(start = CoroutineStart.UNDISPATCHED) { editor.edit(source, null, remember = null) }
        assertThat(findSwitch(shown.single())).isNull()
        button(shown.single(), R.string.crop_cancel).performClick()
        assertThat(picture.await()).isNull()
    }

    private fun findSwitch(root: View): Switch? = when (root) {
        is Switch -> root
        is ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { findSwitch(root.getChildAt(it)) }
        else -> null
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
