package com.vpr.screenlate.overlay.ui

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.vpr.screenlate.core.common.log.Log

/** Layout params for our overlay windows and adding them. Positions are absolute screen coordinates. */
object OverlayWindows {

    /** A window of a group added by [addAll]: [add] tells whether it was added, [remove] takes it away again. */
    class Entry(val add: () -> Boolean, val remove: () -> Unit)

    /**
     * Adds a window; false when the system no longer takes the service's window token. The system drops the token as
     * soon as it unbinds the service (turned off, or paused while a UiAutomation is connected), and the service learns
     * of it only later, in `onDestroy`: a window added in between fails with [WindowManager.BadTokenException]. Other
     * failures are passed on.
     */
    fun add(windowManager: WindowManager, view: View, params: LayoutParams): Boolean = try {
        windowManager.addView(view, params)
        true
    } catch (e: WindowManager.BadTokenException) {
        Log.w(TAG, "Window not added, the system is unbinding the service: ${e.message}")
        false
    }

    /**
     * Adds [windows] in order, the bottom one first. When one cannot be added, removes those added before it, the
     * newest first, and returns false: the overlay shows whole or not at all.
     */
    fun addAll(vararg windows: Entry): Boolean {
        for ((index, window) in windows.withIndex()) {
            if (window.add()) continue
            for (added in index - 1 downTo 0) windows[added].remove()
            return false
        }
        return true
    }

    fun bubbleParams(size: Int): LayoutParams = base(
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
    ).apply {
        width = size
        height = size
    }

    fun layerParams(): LayoutParams = base(
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE,
    ).apply {
        width = LayoutParams.MATCH_PARENT
        height = LayoutParams.MATCH_PARENT
    }

    fun popupParams(): LayoutParams = base(
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
    )

    /**
     * The held bubble's menu: focused, for Back, and touch modal, so every touch on the screen reaches it and one
     * outside closes it; no keyboard of its own. Hardware drawn for its shadow.
     */
    fun menuParams(): LayoutParams = base(
        LayoutParams.FLAG_ALT_FOCUSABLE_IM or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or LayoutParams.FLAG_HARDWARE_ACCELERATED,
    )

    /** Full-screen, touchable, without keyboard focus: the crop editor. */
    fun editorParams(): LayoutParams = base(LayoutParams.FLAG_NOT_FOCUSABLE).apply {
        width = LayoutParams.MATCH_PARENT
        height = LayoutParams.MATCH_PARENT
    }

    private fun base(flags: Int): LayoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        // A window that moves and changes size in one update would glide to its new place for about 400 ms. The bubble
        // never updates its window so (BubbleWindowMover); on Android 14 and later no overlay window glides at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setCanPlayMoveAnimation(false)
    }

    private const val TAG = "OverlayWindows"
}
