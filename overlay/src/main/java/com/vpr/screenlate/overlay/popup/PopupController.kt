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
 *
 * The page keeps the last frame of the view it showed until a new one is drawn, so a new view must not appear before
 * that: a popup opened again stays transparent and untouchable until then, and an open one moves to the new word then.
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

    /** Counts [show] and [hide] calls, so a view drawn late does not change a newer one. */
    private var generation = 0

    /** The popup was opened again and waits, transparent, for its new view to be drawn. */
    private var concealed = false

    val page = LookupPage(context, object : LookupPage.Callbacks by callbacks {
        override fun onClose() {
            // Also called when the renderer died: the window must go before the page is reused.
            hide()
            callbacks.onClose()
        }
    })

    init {
        page.setOnSelecting(::setFocusable)
    }

    var bounds: Box? = null
        private set

    val isShowing: Boolean get() = visible

    /** Adds the hidden window; false when the system no longer takes the service's windows ([OverlayWindows.add]). */
    fun attach(): Boolean {
        if (attached) return true
        setHiddenParams()
        page.container.visibility = View.GONE
        attached = OverlayWindows.add(windowManager, page.container, params)
        return attached
    }

    fun detach() {
        if (!attached) return
        windowManager.removeView(page.container)
        attached = false
        visible = false
        bounds = null
        generation++
        concealed = false
        applyAlpha()
    }

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: String, word: Box, bubble: Box?, screen: Box, maxWidth: Float) {
        if (!attach()) return
        val area = Box(screen.left + edgeMargin, screen.top + edgeMargin, screen.right - edgeMargin, screen.bottom - edgeMargin)
        val size = PopupPlacement.size(screen, maxWidth)
        val placed = PopupPlacement.place(word, bubble, size, area, margin = edgeMargin * 2)
        bounds = placed
        val continued = visible
        visible = true
        val shown = ++generation
        if (!continued) {
            concealed = true
            applyAlpha()
            setBounds(placed)
            windowManager.updateViewLayout(page.container, params)
            page.container.visibility = View.VISIBLE
        }
        page.render(state, continued)
        page.whenDrawn { if (shown == generation) onDrawn() }
    }

    private fun onDrawn() {
        val placed = bounds ?: return
        concealed = false
        applyAlpha()
        val old = listOf(params.flags, params.x, params.y, params.width, params.height)
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        setBounds(placed)
        if (listOf(params.flags, params.x, params.y, params.width, params.height) != old) {
            windowManager.updateViewLayout(page.container, params)
        }
    }

    private fun setBounds(placed: Box) {
        params.x = placed.left.roundToInt()
        params.y = placed.top.roundToInt()
        params.width = placed.width.roundToInt()
        params.height = placed.height.roundToInt()
    }

    /** Re-renders the current view in place, if the popup is showing. */
    fun update(state: String) {
        if (visible) page.update(state)
    }

    /** Shows [state] on top of the current view; the popup's back button returns to it. */
    fun push(state: String) {
        if (visible) page.push(state)
    }

    /** See-through for a screenshot that must not show the popup; it keeps its place and state. */
    var alpha: Float = 1f
        set(value) {
            field = value
            applyAlpha()
        }

    private fun applyAlpha() {
        page.container.alpha = if (concealed) 0f else alpha
    }

    fun hide() {
        if (!visible) return
        visible = false
        bounds = null
        generation++
        concealed = false
        applyAlpha()
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

    /** The popup takes focus only while text in it is selected, as selection handles need a focused window. */
    private fun setFocusable(focusable: Boolean) {
        val flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (flags == params.flags) return
        params.flags = flags
        if (attached) windowManager.updateViewLayout(page.container, params)
    }

    /** A hidden window must not take touches meant for the app below. */
    private fun setHiddenParams() {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.width = 1
        params.height = 1
    }

    private companion object {
        const val EDGE_MARGIN_DP = 8f
    }
}
