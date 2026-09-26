package com.vpr.screenlate.overlay.popup

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.ui.OverlayWindows
import com.vpr.screenlate.overlay.web.LookupPage
import kotlin.math.roundToInt

/**
 * The popup window: a pre-warmed [LookupPage] placed next to the word under the aim.
 *
 * The window stays attached while the overlay is shown and is hidden instead of removed, so it keeps its place in the
 * window stack: [attach] it before the bubble and the bubble always stays on top.
 */
class PopupController(
    context: Context,
    private val windowManager: WindowManager,
    callbacks: LookupPage.Callbacks,
) {
    private val density = context.resources.displayMetrics.density
    private val edgeMargin = EDGE_MARGIN_DP * density
    private val params = OverlayWindows.popupParams()
    private var attached = false
    private var visible = false

    val page = LookupPage(context, object : LookupPage.Callbacks by callbacks {
        override fun onClose() {
            // Also called when the renderer died: the window must go before the page is reused.
            hide()
            callbacks.onClose()
        }
    })

    var bounds: Box? = null
        private set

    val isShowing: Boolean get() = visible

    /** Adds the hidden window. */
    fun attach() {
        if (attached) return
        setHiddenParams()
        page.container.visibility = View.GONE
        windowManager.addView(page.container, params)
        attached = true
    }

    fun detach() {
        if (!attached) return
        windowManager.removeView(page.container)
        attached = false
        visible = false
        bounds = null
    }

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: String, word: Box, vertical: Boolean, bubble: Box?, screen: Box, maxWidth: Float) {
        if (!attached) attach()
        val area = Box(screen.left + edgeMargin, screen.top + edgeMargin, screen.right - edgeMargin, screen.bottom - edgeMargin)
        val width = minOf(screen.width * WIDTH_FRACTION, maxWidth)
        val height = screen.height * HEIGHT_FRACTION
        val minHeight = maxOf(screen.height * MIN_HEIGHT_FRACTION, MIN_HEIGHT_DP * density).coerceAtMost(height)
        val placed = PopupPlacement.place(word, vertical, bubble, width, height, minHeight, area, margin = edgeMargin * 2)
        bounds = placed
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.x = placed.left.roundToInt()
        params.y = placed.top.roundToInt()
        params.width = placed.width.roundToInt()
        params.height = placed.height.roundToInt()
        windowManager.updateViewLayout(page.container, params)
        page.container.visibility = View.VISIBLE
        visible = true
        page.render(state)
    }

    /** Re-renders the current view in place, if the popup is showing. */
    fun update(state: String) {
        if (visible) page.update(state)
    }

    /** Shows [state] on top of the current view; the popup's back button returns to it. */
    fun push(state: String) {
        if (visible) page.push(state)
    }

    fun hide() {
        if (!visible) return
        visible = false
        bounds = null
        page.container.visibility = View.GONE
        if (attached) {
            setHiddenParams()
            windowManager.updateViewLayout(page.container, params)
        }
    }

    fun release() {
        detach()
        page.destroy()
    }

    /** A hidden window must not take touches meant for the app below. */
    private fun setHiddenParams() {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.width = 1
        params.height = 1
    }

    private companion object {
        const val WIDTH_FRACTION = 0.85f
        const val HEIGHT_FRACTION = 0.35f
        const val MIN_HEIGHT_FRACTION = 0.2f
        const val MIN_HEIGHT_DP = 160f
        const val EDGE_MARGIN_DP = 8f
    }
}
