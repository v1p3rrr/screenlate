package com.vpr.screenlate.overlay.popup

import android.content.Context
import android.view.WindowManager
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.ui.OverlayWindows
import com.vpr.screenlate.overlay.web.LookupPage
import kotlin.math.roundToInt

/** The popup window: a pre-warmed [LookupPage] placed next to the word under the aim. */
class PopupController(
    context: Context,
    private val windowManager: WindowManager,
    callbacks: LookupPage.Callbacks,
) {
    private val edgeMargin = EDGE_MARGIN_DP * context.resources.displayMetrics.density
    private val params = OverlayWindows.popupParams()
    private var attached = false

    val page = LookupPage(context, object : LookupPage.Callbacks by callbacks {
        override fun onClose() {
            // Also called when the renderer died: the window must go before the page is reused.
            hide()
            callbacks.onClose()
        }
    })

    var bounds: Box? = null
        private set

    val isShowing: Boolean get() = attached

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: String, word: Box, vertical: Boolean, bubble: Box?, screen: Box, maxWidth: Float) {
        val area = Box(screen.left + edgeMargin, screen.top + edgeMargin, screen.right - edgeMargin, screen.bottom - edgeMargin)
        val width = minOf(screen.width * WIDTH_FRACTION, maxWidth)
        val height = screen.height * HEIGHT_FRACTION
        val placed = PopupPlacement.place(word, vertical, bubble, width, height, area, margin = edgeMargin * 2)
        bounds = placed
        params.x = placed.left.roundToInt()
        params.y = placed.top.roundToInt()
        params.width = placed.width.roundToInt()
        params.height = placed.height.roundToInt()
        if (attached) {
            windowManager.updateViewLayout(page.container, params)
        } else {
            windowManager.addView(page.container, params)
            attached = true
        }
        page.render(state)
    }

    /** Re-renders the current view in place, if the popup is showing. */
    fun update(state: String) {
        if (attached) page.update(state)
    }

    /** Shows [state] on top of the current view; the popup's back button returns to it. */
    fun push(state: String) {
        if (attached) page.push(state)
    }

    fun hide() {
        if (!attached) return
        windowManager.removeView(page.container)
        attached = false
        bounds = null
    }

    fun release() {
        hide()
        page.destroy()
    }

    private companion object {
        const val WIDTH_FRACTION = 0.85f
        const val HEIGHT_FRACTION = 0.35f
        const val EDGE_MARGIN_DP = 8f
    }
}
