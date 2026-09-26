package com.vpr.screenlate.overlay.ui

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager.LayoutParams

/** Layout params for our overlay windows. Positions are absolute screen coordinates. */
object OverlayWindows {

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
    }
}
