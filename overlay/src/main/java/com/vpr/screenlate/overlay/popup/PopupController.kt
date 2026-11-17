package com.vpr.screenlate.overlay.popup

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
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
 * window stack: [attach] it before the bubble and the bubble always stays on top. When [hideOnOutsideTap] is enabled,
 * a transparent full-screen backdrop sits immediately below the popup while it is visible to catch and consume taps
 * outside the popup without interfering with the bubble above.
 *
 * The page keeps the last frame of the view it showed until a new one is drawn, so a new view must not appear before
 * that: a popup opened again stays transparent and untouchable until then, and an open one moves to the new word then.
 */
class PopupController(
    context: Context,
    private val windowManager: WindowManager,
    private val callbacks: LookupPage.Callbacks,
) {
    private val density = context.resources.displayMetrics.density
    private val edgeMargin = EDGE_MARGIN_DP * density
    private val params = OverlayWindows.popupParams()
    private val catcherParams = OverlayWindows.outsideCatcherParams()
    private var attached = false
    private var visible = false
    private var lastWord: Box? = null
    private var outsidePressed = false
    private var catcherActive = false

    /** When true, tapping anywhere outside the visible popup closes it and consumes that touch. */
    var hideOnOutsideTap: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateOutsideCatcher()
        }

    /**
     * Transparent touch catcher below the popup window. Closes on non-cancelled tap so system edge gestures (Back,
     * Home) that cancel the touch do not falsely dismiss the popup.
     */
    @SuppressLint("ClickableViewAccessibility")
    private val outsideCatcher = View(context).apply {
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> outsidePressed = true
                MotionEvent.ACTION_UP -> {
                    val tapped = outsidePressed
                    outsidePressed = false
                    if (tapped && visible && hideOnOutsideTap) {
                        hide()
                        callbacks.onClose()
                    }
                }
                MotionEvent.ACTION_CANCEL -> outsidePressed = false
            }
            true
        }
    }

    /** Counts [show] and [hide] calls, so a view drawn late does not change a newer one. */
    private var generation = 0

    /** The popup was opened again and waits, transparent, for its new view to be drawn. */
    private var concealed = false

    /** The open popup waits for its new view to be drawn before moving to [bounds]. */
    private var placing = false

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

    /** Adds the hidden backdrop and popup windows; false when the system no longer takes the service's windows ([OverlayWindows.add]). */
    fun attach(): Boolean {
        if (attached) return true
        setHiddenParams()
        setCatcherHiddenParams()
        outsideCatcher.visibility = View.GONE
        page.container.visibility = View.GONE
        catcherActive = false
        attached = OverlayWindows.addAll(
            OverlayWindows.Entry(
                { OverlayWindows.add(windowManager, outsideCatcher, catcherParams) },
                { windowManager.removeView(outsideCatcher) },
            ),
            OverlayWindows.Entry(
                { OverlayWindows.add(windowManager, page.container, params) },
                { windowManager.removeView(page.container) },
            ),
        )
        return attached
    }

    fun detach() {
        if (!attached) return
        windowManager.removeView(page.container)
        windowManager.removeView(outsideCatcher)
        attached = false
        visible = false
        bounds = null
        lastWord = null
        generation++
        concealed = false
        placing = false
        outsidePressed = false
        catcherActive = false
        applyAlpha()
    }

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: String, word: Box, bubble: Box?, screen: Box, maxWidth: Float) {
        if (!attach()) return
        val placed = place(word, bubble, screen, maxWidth)
        bounds = placed
        val continued = visible
        val refined = visible && lastWord?.let { isSameWord(it, word) } == true
        lastWord = word
        visible = true
        val shown = ++generation
        if (continued) {
            placing = true
        } else {
            concealed = true
            applyAlpha()
            setBounds(placed)
            windowManager.updateViewLayout(page.container, params)
            page.container.visibility = View.VISIBLE
        }
        page.render(state, continued, refined)
        page.whenDrawn { if (shown == generation) onDrawn() }
    }

    private fun onDrawn() {
        val placed = bounds ?: return
        concealed = false
        placing = false
        applyAlpha()
        val old = listOf(params.flags, params.x, params.y, params.width, params.height)
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        setBounds(placed)
        if (listOf(params.flags, params.x, params.y, params.width, params.height) != old) {
            windowManager.updateViewLayout(page.container, params)
        }
        updateOutsideCatcher()
    }

    /**
     * Places the shown popup next to [word] again, as [show] would, keeping what it shows: the word moved on the screen.
     * Only the window moves; nothing is laid out again unless its size changes.
     */
    fun move(word: Box, bubble: Box?, screen: Box, maxWidth: Float) {
        if (!visible) return
        val placed = place(word, bubble, screen, maxWidth)
        bounds = placed
        if (placing) return
        val old = listOf(params.x, params.y, params.width, params.height)
        setBounds(placed)
        if (listOf(params.x, params.y, params.width, params.height) != old) windowManager.updateViewLayout(page.container, params)
    }

    private fun place(word: Box, bubble: Box?, screen: Box, maxWidth: Float): Box {
        val area = Box(screen.left + edgeMargin, screen.top + edgeMargin, screen.right - edgeMargin, screen.bottom - edgeMargin)
        val size = PopupPlacement.size(screen, maxWidth)
        return PopupPlacement.place(word, bubble, size, area, margin = edgeMargin * 2)
    }

    private fun setBounds(placed: Box) {
        params.x = placed.left.roundToInt()
        params.y = placed.top.roundToInt()
        params.width = placed.width.roundToInt()
        params.height = placed.height.roundToInt()
    }

    /** Two detections are the same word on the screen when their bounding boxes heavily overlap (IoU >= 0.4). */
    private fun isSameWord(a: Box, b: Box): Boolean {
        val intersection = a.intersection(b) ?: return false
        val intersectionArea = intersection.width * intersection.height
        val unionArea = (a.width * a.height) + (b.width * b.height) - intersectionArea
        return unionArea > 0f && (intersectionArea / unionArea) >= 0.4f
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
        lastWord = null
        generation++
        concealed = false
        placing = false
        outsidePressed = false
        applyAlpha()
        page.container.visibility = View.GONE
        if (attached) {
            setHiddenParams()
            windowManager.updateViewLayout(page.container, params)
        }
        updateOutsideCatcher()
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

    /** Activates the full-screen outside-tap backdrop only while the popup is visible and the option is enabled. */
    private fun updateOutsideCatcher() {
        val active = attached && visible && !concealed && hideOnOutsideTap
        if (active == catcherActive) return
        catcherActive = active
        if (active) {
            catcherParams.flags = catcherParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            catcherParams.width = WindowManager.LayoutParams.MATCH_PARENT
            catcherParams.height = WindowManager.LayoutParams.MATCH_PARENT
            outsideCatcher.visibility = View.VISIBLE
            windowManager.updateViewLayout(outsideCatcher, catcherParams)
        } else {
            outsidePressed = false
            outsideCatcher.visibility = View.GONE
            setCatcherHiddenParams()
            if (attached) windowManager.updateViewLayout(outsideCatcher, catcherParams)
        }
    }

    /** A hidden window must not take touches meant for the app below. */
    private fun setHiddenParams() {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.width = 1
        params.height = 1
    }

    private fun setCatcherHiddenParams() {
        catcherParams.flags = catcherParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        catcherParams.width = 1
        catcherParams.height = 1
    }

    private companion object {
        const val EDGE_MARGIN_DP = 8f
    }
}
