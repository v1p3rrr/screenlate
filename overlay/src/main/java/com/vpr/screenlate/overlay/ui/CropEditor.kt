package com.vpr.screenlate.overlay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vpr.screenlate.overlay.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Full-screen editor for the picture attached to an Anki note: a clean screenshot with a crop frame that starts
 * around the paragraph of the looked-up word, and buttons for switching between the frame and the whole screen, cancel
 * and add.
 */
class CropEditor(private val context: Context, private val windowManager: WindowManager) {
    private var window: View? = null

    /**
     * Shows the editor and returns the cropped picture, or null if the user cancelled.
     *
     * @param focus initial frame in screen coordinates; the whole screenshot when null.
     */
    suspend fun edit(image: Bitmap, left: Float, top: Float, focus: RectF?): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val cropView = CropView(context, image, left, top)
            // The frame to return to from the whole screen: the paragraph, or the middle of the screenshot.
            val frame = RectF(focus ?: middleOf(image, left, top))
            var wholeScreen = focus == null
            if (wholeScreen) cropView.selectAll() else cropView.setFrame(frame)

            fun finish(result: Bitmap?) {
                dismiss()
                if (continuation.isActive) continuation.resume(result)
            }

            val density = context.resources.displayMetrics.density
            val bar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val padding = (12 * density).toInt()
                setPadding(padding, padding, padding, padding + navigationBarHeight())
                addView(
                    button(if (wholeScreen) R.string.crop_frame else R.string.crop_whole_screen, primary = false) {
                        if (wholeScreen) {
                            cropView.setFrame(frame)
                        } else {
                            frame.set(cropView.frame)
                            cropView.selectAll()
                        }
                        wholeScreen = !wholeScreen
                        setText(if (wholeScreen) R.string.crop_frame else R.string.crop_whole_screen)
                    },
                )
                addView(button(R.string.crop_cancel, primary = false) { finish(null) })
                addView(button(R.string.crop_add, primary = true) { finish(cropView.cropped()) })
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
            }
            windowManager.addView(root, OverlayWindows.editorParams())
            window = root
            continuation.invokeOnCancellation { root.post { dismiss() } }
        }

    fun dismiss() {
        window?.let(windowManager::removeView)
        window = null
    }

    private fun middleOf(image: Bitmap, left: Float, top: Float): RectF {
        val insetX = image.width / 4f
        val insetY = image.height / 4f
        return RectF(left + insetX, top + insetY, left + image.width - insetX, top + image.height - insetY)
    }

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
        return insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
    }

    private companion object {
        val ACCENT = Color.rgb(0x7C, 0x5C, 0xFF)
    }
}
