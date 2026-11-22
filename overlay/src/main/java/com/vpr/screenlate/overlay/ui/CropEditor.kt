package com.vpr.screenlate.overlay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.vpr.screenlate.core.anki.settings.NormalizedCrop
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.anki.NoteImage
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Full-screen editor for the picture attached to an Anki note: an owned image with a crop frame that starts
 * around the paragraph of the looked-up word (or the last remembered screen area when enabled), and buttons for
 * switching between the frame and the whole image, cancel and add.
 */
class CropEditor(private val context: Context, private val windowManager: WindowManager) {
    /**
     * Synchronized "Remember area" toggle for screen screenshots.
     *
     * @property initial toggle state when the editor opens.
     * @property remembered normalized crop to prefill when [initial] is true, or null.
     * @property updates live toggle updates from settings for two-way sync while the editor is open.
     * @property onChange persists the toggle state immediately when the user flips it in the editor.
     * @property onConfirm persists the confirmed selection in image coordinates when the toggle is on.
     */
    class RememberControl(
        val initial: Boolean,
        val remembered: NormalizedCrop?,
        val updates: Flow<Boolean>,
        val onChange: suspend (Boolean) -> Unit,
        val onConfirm: suspend (Box) -> Unit,
    )

    private class Selection(val box: Box, val remember: Boolean)

    private var open: Session? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var generation = 0L

    /**
     * Shows the editor and returns the cropped picture, a new bitmap the caller frees, or null if the user cancelled.
     * A photo is fitted above the controls, a screenshot keeps its place on the screen; the frame is cropped from
     * [image]'s own pixels either way. [image] must stay open until then. An editor still open for another note closes
     * as if cancelled.
     *
     * @param focus initial frame in [image]'s coordinates; the whole image when null.
     * @param remember optional synchronized "Remember area" control (used for screen screenshots, null for photos).
     */
    suspend fun edit(image: NoteImage, focus: Box?, remember: RememberControl? = null): Bitmap? {
        cancel()
        val request = generation
        val bounds = WindowMetricsCompat.screenBounds(windowManager)
        val preview = image.preview(bounds.width(), bounds.height())
        // A scan can close while the preview is decoded. It must not open an editor afterwards.
        if (request != generation) return null
        val selection = select(image, preview, focus, remember) ?: return null
        val cropped = image.crop(selection.box)
        if (selection.remember) {
            try {
                remember?.onConfirm?.invoke(selection.box)
            } catch (e: CancellationException) {
                cropped.recycle()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving screenshot crop area failed", e.redacted())
            }
        }
        return cropped
    }

    /** The frame the user added and whether to remember it, in [image]'s coordinates; null when cancelled. */
    private suspend fun select(
        image: NoteImage,
        preview: Bitmap,
        focus: Box?,
        remember: RememberControl?,
    ): Selection? = coroutineScope {
        var toggleSync: ToggleSync? = null
        try {
            suspendCancellableCoroutine { continuation ->
                val cropView = CropView(context, preview)
                val initialState = CropInitialState.resolve(
                    imageBounds = image.bounds,
                    focus = focus,
                    remembered = remember?.takeIf { it.initial }?.remembered,
                )
                val initial = initialState.initial
                // The frame to return to from the whole image: the paragraph, remembered frame, or middle of the image.
                var frame = initialState.frame
                var whole = initialState.whole
                // Known once the editor is laid out; the frame and Add wait for it.
                var placement: CropPlacement? = null
                lateinit var session: Session
                val density = context.resources.displayMetrics.density
                val buttonsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    val wholeLabel = if (image.fit) R.string.crop_whole_photo else R.string.crop_whole_screen
                    addView(button(if (whole) R.string.crop_frame else wholeLabel, primary = false) {
                        val shown = placement ?: return@button
                        if (whole) {
                            cropView.setFrame(shown.toScreen(frame).toRect())
                        } else {
                            frame = shown.toSource(cropView.frame.toBox())
                            cropView.selectAll()
                        }
                        whole = !whole
                        setText(if (whole) R.string.crop_frame else wholeLabel)
                    })
                    addView(button(R.string.crop_cancel, primary = false) { session.finish(null) })
                    addView(button(R.string.crop_add, primary = true) {
                        if (session.isOpen) {
                            val shouldRemember = toggleSync?.value ?: false
                            placement?.let { session.finish(Selection(it.toSource(cropView.frame.toBox()), shouldRemember)) }
                        }
                    })
                }
                val bar = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val padding = (12 * density).toInt()
                    setPadding(padding, padding, padding, padding + navigationBarHeight())
                    if (remember != null) {
                        lateinit var sync: ToggleSync
                        val (pill, setCheckedUi) = rememberPill(remember.initial) { isChecked ->
                            sync.setLocal(isChecked)
                        }
                        sync = ToggleSync(
                            initial = remember.initial,
                            updates = remember.updates,
                            persist = remember.onChange,
                            onExternalValue = setCheckedUi,
                            scope = this@coroutineScope,
                            dispatcher = Dispatchers.Main.immediate,
                        )
                        toggleSync = sync
                        sync.start()
                        addView(pill)
                    }
                    addView(buttonsRow)
                }
                val root = FrameLayout(context).apply {
                    addView(cropView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    addView(
                        bar,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM,
                        ),
                    )
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        if (width <= 0 || height <= 0 || bar.top <= 0) return@addOnLayoutChangeListener
                        val padding = 16 * density
                        val top = WindowMetricsCompat.statusBarHeight(context, windowManager) + padding
                        val area = Box(
                            padding,
                            top,
                            (width - padding).coerceAtLeast(padding + 1),
                            (bar.top - padding).coerceAtLeast(top + 1),
                        )
                        val next = CropPlacement.of(image.bounds, area, image.fit)
                        if (next != placement) {
                            val selected = placement?.toSource(cropView.frame.toBox()) ?: if (whole) image.bounds else initial
                            placement = next
                            cropView.place(next.shown.toRect())
                            cropView.setFrame(next.toScreen(selected).toRect())
                        }
                    }
                }
                session = Session(root, continuation, { toggleSync?.stop() }, { placement != null }) {
                    if (placement == null) false else {
                        buttonsRow.getChildAt(buttonsRow.childCount - 1).performClick()
                        true
                    }
                }
                if (!OverlayWindows.add(windowManager, root, OverlayWindows.editorParams())) {
                    // The service is being unbound: as if the user cancelled.
                    toggleSync?.stop()
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                open = session
                // Cancellation may come from any thread; the window is removed on the main one.
                continuation.invokeOnCancellation {
                    if (Looper.myLooper() === Looper.getMainLooper()) session.finish(null)
                    else mainHandler.post { session.finish(null) }
                }
            }
        } finally {
            toggleSync?.stop()
        }
    }

    /** Closes the open editor as if the user cancelled, and keeps one whose preview is still decoded from opening. */
    fun cancel() {
        generation++
        open?.finish(null)
    }

    val canConfirm: Boolean get() = open?.canConfirm?.invoke() == true
    fun confirm(): Boolean = open?.confirm?.invoke() == true

    private inner class Session(
        private val root: View,
        private val continuation: CancellableContinuation<Selection?>,
        private val onClose: () -> Unit,
        val canConfirm: () -> Boolean,
        val confirm: () -> Boolean,
    ) {
        val isOpen: Boolean get() = open === this

        fun finish(result: Selection?) {
            if (!isOpen) return
            open = null
            onClose()
            windowManager.removeView(root)
            if (continuation.isActive) continuation.resume(result)
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun rememberPill(initial: Boolean, onUserChange: (Boolean) -> Unit): Pair<View, (Boolean) -> Unit> {
        val density = context.resources.displayMetrics.density
        var syncing = false
        val labelText = context.getString(R.string.crop_remember_area)
        val label = TextView(context).apply {
            text = labelText
            textSize = 14f
            maxLines = 2
            setTextColor(Color.rgb(0xEC, 0xE8, 0xF2))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Weight 1 with WRAP_CONTENT keeps the pill compact while allowing long labels to wrap instead of clipping the switch.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginEnd = (10 * density).toInt() }
        }
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val toggle = Switch(context).apply {
            isChecked = initial
            isFocusable = false
            contentDescription = labelText
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(0xD0, 0xCC, 0xD8)))
            trackTintList = ColorStateList(states, intArrayOf(ACCENT, Color.argb(180, 0x6B, 0x67, 0x74)))
            setOnCheckedChangeListener { _, isChecked ->
                if (!syncing) onUserChange(isChecked)
            }
        }
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val horizontal = (14 * density).toInt()
            val vertical = (6 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            minimumHeight = (40 * density).toInt()
            background = GradientDrawable().apply {
                cornerRadius = 20 * density
                setColor(Color.argb(200, 0x2E, 0x2B, 0x35))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (8 * density).toInt() }
            addView(label)
            addView(toggle)
            setOnClickListener { toggle.toggle() }
        }
        val setCheckedUi: (Boolean) -> Unit = { value ->
            if (toggle.isChecked != value) {
                syncing = true
                toggle.isChecked = value
                syncing = false
            }
        }
        return pill to setCheckedUi
    }

    private fun RectF.toBox() = Box(left, top, right, bottom)
    private fun Box.toRect() = RectF(left, top, right, bottom)

    private fun button(text: Int, primary: Boolean, onClick: TextView.() -> Unit): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            setText(text)
            textSize = 15f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(if (primary) Color.WHITE else Color.rgb(0xEC, 0xE8, 0xF2))
            val horizontal = (18 * density).toInt()
            val vertical = (10 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(if (primary) ACCENT else Color.argb(200, 0x2E, 0x2B, 0x35))
            }
            // Equal shares of the bar, so long labels wrap instead of pushing buttons off the screen;
            // all buttons take the height of the tallest one.
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .apply { marginStart = (6 * density).toInt(); marginEnd = (6 * density).toInt() }
            setOnClickListener { onClick() }
        }
    }

    private fun navigationBarHeight(): Int {
        return WindowMetricsCompat.navigationBarHeight(context, windowManager)
    }

    private companion object {
        const val TAG = "CropEditor"
        val ACCENT = Color.rgb(0x7C, 0x5C, 0xFF)
    }
}

/**
 * Coordinates two-way synchronization between the in-editor toggle and settings without echoing stale writes back on
 * rapid flips.
 *
 * Local flips serialize through a single write loop and suppress incoming [updates] while a local write is in flight,
 * preventing an earlier write's emission from bouncing the toggle back. Once settled, external changes from [updates]
 * flow through to [onExternalValue].
 */
internal class ToggleSync(
    private val initial: Boolean,
    private val updates: Flow<Boolean>,
    private val persist: suspend (Boolean) -> Unit,
    private val onExternalValue: (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    var value: Boolean = initial
        private set

    private var pendingLocal: Boolean? = null
    private var latestExternal: Boolean? = null
    private var updatesJob: Job? = null
    private var writeJob: Job? = null

    fun start() {
        if (updatesJob != null) return
        updatesJob = scope.launch(dispatcher) {
            var skippedInitial = false
            updates.collect { external ->
                if (pendingLocal != null) {
                    skippedInitial = true
                    latestExternal = external
                    return@collect
                }
                if (!skippedInitial && external == initial) {
                    skippedInitial = true
                    return@collect
                }
                skippedInitial = true
                if (value != external) {
                    value = external
                    onExternalValue(external)
                }
            }
        }
    }

    fun setLocal(newValue: Boolean) {
        value = newValue
        pendingLocal = newValue
        latestExternal = null
        if (writeJob?.isActive == true) return
        writeJob = scope.launch(dispatcher) {
            try {
                while (true) {
                    val target = pendingLocal ?: break
                    latestExternal = null
                    withContext(NonCancellable) { persist(target) }
                    if (pendingLocal == target) {
                        pendingLocal = null
                        val external = latestExternal
                        latestExternal = null
                        if (updatesJob != null && external != null && external != value) {
                            value = external
                            onExternalValue(external)
                        }
                        break
                    }
                }
            } catch (e: CancellationException) {
                pendingLocal = null
                latestExternal = null
                throw e
            } catch (e: Exception) {
                pendingLocal = null
                latestExternal = null
                Log.w("CropEditor", "Persisting crop memory toggle failed", e.redacted())
            }
        }
    }

    /** Stops observing external updates; an in-flight local write finishes under [NonCancellable]. */
    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
    }
}

/**
 * Initial crop frame state when [CropEditor] opens.
 *
 * @property initial frame shown on screen first.
 * @property frame frame restored when switching back from the whole image.
 * @property whole whether the whole image is selected initially (so the toggle button offers "Frame").
 */
internal data class CropInitialState(
    val initial: Box,
    val frame: Box,
    val whole: Boolean,
) {
    companion object {
        fun resolve(imageBounds: Box, focus: Box?, remembered: NormalizedCrop?): CropInitialState {
            val clippedFocus = focus?.intersection(imageBounds)
            val fallbackFrame = clippedFocus ?: imageBounds.insetQuarter()
            if (remembered != null && remembered.isValid) {
                if (remembered.isWhole) {
                    return CropInitialState(initial = imageBounds, frame = fallbackFrame, whole = true)
                }
                val rememberedBox = remembered.toBox(imageBounds).intersection(imageBounds)
                if (rememberedBox != null) {
                    return CropInitialState(initial = rememberedBox, frame = rememberedBox, whole = false)
                }
            }
            return CropInitialState(
                initial = clippedFocus ?: imageBounds,
                frame = fallbackFrame,
                whole = clippedFocus == null,
            )
        }

        private fun Box.insetQuarter() = Box(left + width / 4, top + height / 4, right - width / 4, bottom - height / 4)
    }
}
