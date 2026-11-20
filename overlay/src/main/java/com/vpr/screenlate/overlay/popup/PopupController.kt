package com.vpr.screenlate.overlay.popup

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
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
    private val context: Context,
    private val windowManager: WindowManager,
    private val callbacks: LookupPage.Callbacks,
) {
    private val density = context.resources.displayMetrics.density
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val edgeMargin = EDGE_MARGIN_DP * density
    private val params = OverlayWindows.popupParams()
    private val catcherParams = OverlayWindows.outsideCatcherParams()
    private var attached = false
    private var visible = false
    private var lastWord: Box? = null
    private val outsideTap = OutsideTapState()
    private var catcherActive = false
    private var screenOffRegistered = false

    /** When true, the sentence audio editor is being interacted with; outside touches pass through to the app below. */
    var isAudioEditorActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateOutsideCatcher()
        }

    private var normalBounds: Box? = null
    private var lastScreen: Box? = null
    private var lastMaxWidth: Float = 0f
    private var isExpanded: Boolean = false
    private var isCollapsed: Boolean = false

    /**
     * Overlays stay above the lock screen, where an active full-screen backdrop would swallow the first touch of the
     * unlock gesture. Closing on screen-off avoids blocking unlock or always-on display interactions.
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (visible && hideOnOutsideTap) dismissFromOutside()
        }
    }

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
                MotionEvent.ACTION_DOWN -> outsideTap.onDown()
                MotionEvent.ACTION_UP -> if (outsideTap.onUp(isCatcherActive())) dismissFromOutside()
                MotionEvent.ACTION_CANCEL -> outsideTap.onCancel()
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
        hideParams(params)
        hideParams(catcherParams)
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
        unregisterScreenOff()
        windowManager.removeView(page.container)
        windowManager.removeView(outsideCatcher)
        attached = false
        visible = false
        bounds = null
        lastWord = null
        generation++
        concealed = false
        placing = false
        outsideTap.reset()
        catcherActive = false
        applyAlpha()
    }

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: String, word: Box, bubble: Box?, screen: Box, maxWidth: Float) {
        lastScreen = screen
        lastMaxWidth = maxWidth
        resetAudioEditorPresentation()
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
            updateScreenOffReceiver()
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
        lastScreen = screen
        lastMaxWidth = maxWidth
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
        resetAudioEditorPresentation()
        visible = false
        bounds = null
        lastWord = null
        generation++
        concealed = false
        placing = false
        outsideTap.reset()
        applyAlpha()
        page.container.visibility = View.GONE
        if (attached) {
            hideParams(params)
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

    private fun dismissFromOutside() {
        hide()
        callbacks.onClose()
    }

    fun enterAudioEditor() {
        isAudioEditorActive = true
        if (!visible) return
        if (normalBounds == null) normalBounds = bounds
        val screen = lastScreen ?: return
        val maxW = if (lastMaxWidth > 0f) lastMaxWidth else screen.width - edgeMargin * 2
        val size = PopupPlacement.audioEditorSize(screen, maxW, expanded = false)
        val x = screen.left + (screen.width - size.width) / 2f
        val y = screen.top + (screen.height - size.height) / 2f
        val editorBox = Box(x, y, x + size.width, y + size.height)
        bounds = editorBox
        setBounds(editorBox)
        if (attached) {
            windowManager.updateViewLayout(page.container, params)
        }
    }

    fun dragBy(dx: Float, dy: Float) {
        val current = bounds ?: return
        val screen = lastScreen ?: return
        val minLeft = screen.left + edgeMargin
        val maxLeft = maxOf(minLeft, screen.right - edgeMargin - current.width)
        val minTop = screen.top + edgeMargin
        val maxTop = maxOf(minTop, screen.bottom - edgeMargin - current.height)
        val newLeft = (current.left + dx).coerceIn(minLeft, maxLeft)
        val newTop = (current.top + dy).coerceIn(minTop, maxTop)
        val newBounds = Box(newLeft, newTop, newLeft + current.width, newTop + current.height)
        bounds = newBounds
        setBounds(newBounds)
        if (visible && attached) {
            windowManager.updateViewLayout(page.container, params)
        }
    }

    fun setAudioEditorExpanded(expanded: Boolean) {
        if (!visible || isCollapsed) return
        isExpanded = expanded
        val screen = lastScreen ?: return
        if (normalBounds == null) normalBounds = bounds
        val maxW = if (lastMaxWidth > 0f) lastMaxWidth else screen.width - edgeMargin * 2
        val size = PopupPlacement.audioEditorSize(screen, maxW, expanded = expanded)
        val x = screen.left + (screen.width - size.width) / 2f
        val y = screen.top + (screen.height - size.height) / 2f
        val wideBox = Box(x, y, x + size.width, y + size.height)
        bounds = wideBox
        setBounds(wideBox)
        if (attached) {
            windowManager.updateViewLayout(page.container, params)
        }
    }

    fun setAudioEditorCollapsed(collapsed: Boolean) {
        if (!visible) return
        isCollapsed = collapsed
        val screen = lastScreen ?: return
        if (collapsed) {
            if (normalBounds == null) normalBounds = bounds
            val pillWidth = 190f * density
            val pillHeight = 48f * density
            val x = screen.right - pillWidth - edgeMargin * 2
            val y = screen.bottom - pillHeight - edgeMargin * 2
            val pillBox = Box(x, y, x + pillWidth, y + pillHeight)
            bounds = pillBox
            setBounds(pillBox)
            if (attached) windowManager.updateViewLayout(page.container, params)
        } else {
            setAudioEditorExpanded(isExpanded)
        }
    }

    fun resetAudioEditorPresentation() {
        isAudioEditorActive = false
        isExpanded = false
        isCollapsed = false
        normalBounds?.let {
            bounds = it
            setBounds(it)
            normalBounds = null
            if (visible && attached) windowManager.updateViewLayout(page.container, params)
        }
    }

    private fun isCatcherActive(): Boolean =
        !isAudioEditorActive && OutsideTapState.shouldCatch(attached, visible, concealed, hideOnOutsideTap)

    /** Activates the full-screen outside-tap backdrop only while the popup is visible and the option is enabled. */
    private fun updateOutsideCatcher() {
        updateScreenOffReceiver()
        val active = isCatcherActive()
        if (active && powerManager?.isInteractive == false) {
            dismissFromOutside()
            return
        }
        if (active == catcherActive) return
        catcherActive = active
        if (active) {
            catcherParams.flags = catcherParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            catcherParams.width = WindowManager.LayoutParams.MATCH_PARENT
            catcherParams.height = WindowManager.LayoutParams.MATCH_PARENT
            outsideCatcher.visibility = View.VISIBLE
            windowManager.updateViewLayout(outsideCatcher, catcherParams)
        } else {
            outsideTap.reset()
            outsideCatcher.visibility = View.GONE
            hideParams(catcherParams)
            if (attached) windowManager.updateViewLayout(outsideCatcher, catcherParams)
        }
    }

    private fun updateScreenOffReceiver() {
        if (attached && visible && hideOnOutsideTap) registerScreenOff() else unregisterScreenOff()
    }

    private fun registerScreenOff() {
        if (screenOffRegistered) return
        ContextCompat.registerReceiver(
            context,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffRegistered = true
    }

    private fun unregisterScreenOff() {
        if (!screenOffRegistered) return
        screenOffRegistered = false
        runCatching { context.unregisterReceiver(screenOff) }
    }

    /** A hidden window must not take touches meant for the app below. */
    private fun hideParams(target: WindowManager.LayoutParams) {
        target.flags = target.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        target.width = 1
        target.height = 1
    }

    private companion object {
        const val EDGE_MARGIN_DP = 8f
    }
}

/**
 * Pure gesture and activation state for the popup's outside-tap backdrop.
 *
 * A non-cancelled `DOWN`..`UP` sequence dismisses the popup while active; a cancelled touch (system edge gesture) or a
 * reset from hiding the popup clears the pending press so it cannot dismiss later.
 */
internal class OutsideTapState {
    private var pressed = false

    fun onDown() {
        pressed = true
    }

    fun onUp(active: Boolean): Boolean {
        val tapped = pressed
        pressed = false
        return tapped && active
    }

    fun onCancel() {
        pressed = false
    }

    fun reset() {
        pressed = false
    }

    companion object {
        fun shouldCatch(attached: Boolean, visible: Boolean, concealed: Boolean, hideOnOutsideTap: Boolean): Boolean =
            attached && visible && !concealed && hideOnOutsideTap
    }
}
