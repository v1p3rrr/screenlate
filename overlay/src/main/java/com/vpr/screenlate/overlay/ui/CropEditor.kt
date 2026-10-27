package com.vpr.screenlate.overlay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.anki.NoteImage
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Full-screen editor for the picture attached to an Anki note: an owned image with a crop frame that starts
 * around the paragraph of the looked-up word, and buttons for switching between the frame and the whole image, cancel
 * and add.
 */
class CropEditor(private val context: Context, private val windowManager: WindowManager) {
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
     */
    suspend fun edit(image: NoteImage, focus: Box?): Bitmap? {
        cancel()
        val request = generation
        val bounds = windowManager.maximumWindowMetrics.bounds
        val preview = image.preview(bounds.width(), bounds.height())
        // A scan can close while the preview is decoded. It must not open an editor afterwards.
        if (request != generation) return null
        val selection = select(image, preview, focus) ?: return null
        return image.crop(selection)
    }

    /** The frame the user added, in [image]'s coordinates; null when cancelled. */
    private suspend fun select(image: NoteImage, preview: Bitmap, focus: Box?): Box? =
        suspendCancellableCoroutine { continuation ->
            val cropView = CropView(context, preview)
            val initial = focus?.intersection(image.bounds) ?: image.bounds
            // The frame to return to from the whole image: the paragraph, or the middle of the image.
            var frame = if (focus == null) image.bounds.insetQuarter() else initial
            var whole = focus == null
            // Known once the editor is laid out; the frame and Add wait for it.
            var placement: CropPlacement? = null
            lateinit var session: Session
            val density = context.resources.displayMetrics.density
            val bar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val padding = (12 * density).toInt()
                setPadding(padding, padding, padding, padding + navigationBarHeight())
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
                    if (session.isOpen) placement?.let { session.finish(it.toSource(cropView.frame.toBox())) }
                })
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
                    val top = windowManager.maximumWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top + padding
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
            session = Session(root, continuation)
            if (!OverlayWindows.add(windowManager, root, OverlayWindows.editorParams())) {
                // The service is being unbound: as if the user cancelled.
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            open = session
            // Cancellation may come from any thread; the window is removed on the main one.
            continuation.invokeOnCancellation { mainHandler.post { session.finish(null) } }
        }

    /** Closes the open editor as if the user cancelled, and keeps one whose preview is still decoded from opening. */
    fun cancel() {
        generation++
        open?.finish(null)
    }

    private inner class Session(private val root: View, private val continuation: CancellableContinuation<Box?>) {
        val isOpen: Boolean get() = open === this

        fun finish(result: Box?) {
            if (!isOpen) return
            open = null
            windowManager.removeView(root)
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private fun RectF.toBox() = Box(left, top, right, bottom)
    private fun Box.toRect() = RectF(left, top, right, bottom)
    private fun Box.insetQuarter() = Box(left + width / 4, top + height / 4, right - width / 4, bottom - height / 4)

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
        val insets = windowManager.maximumWindowMetrics.windowInsets
        return insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
    }

    private companion object {
        val ACCENT = Color.rgb(0x7C, 0x5C, 0xFF)
    }
}
