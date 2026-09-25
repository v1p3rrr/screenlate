package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrUpdate
import com.vpr.screenlate.core.ocr.OfflineException
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.overlay.capture.CaptureException
import com.vpr.screenlate.overlay.capture.CapturedScreen
import com.vpr.screenlate.overlay.capture.ScreenCapturer
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.ui.BubbleView
import com.vpr.screenlate.overlay.ui.LayerView
import com.vpr.screenlate.overlay.ui.OverlayWindows
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Owns the overlay windows and the bubble state machine: docked → dragging → floating.
 *
 * OCR runs only when the bubble is pulled out of the dock or tapped; aiming afterwards reuses that result.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val ocr: CompositeOcr,
    private val overlaySettings: OverlaySettingsRepository,
    private val appSettings: AppSettingsRepository,
    private val scope: CoroutineScope,
) {
    private enum class State { DOCKED, DRAGGING, FLOATING }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val bubbleSize = (BUBBLE_DP * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bubbleView = BubbleView(service)
    private val bubbleParams = OverlayWindows.bubbleParams(bubbleSize)
    private val layerView = LayerView(service)
    private val layerParams = OverlayWindows.layerParams()
    private val popup = PopupController(service, windowManager, onClose = { dock() })
    private val capturer = ScreenCapturer(service, service.mainExecutor)

    private var settings = OverlaySettings()
    private var themeMode = ThemeMode.SYSTEM
    private var state = State.DOCKED
    private var attached = false

    private var scanJob: Job? = null
    private var layout: TextLayout? = null
    private var ocrFinal = false
    private var ocrEngine: OcrEngineType? = null
    private var ocrOffline = false
    private var hit: TextPosition? = null
    private var aim: Pair<Float, Float>? = null
    private var pendingSingleTap: Runnable? = null

    fun start() {
        bubbleView.setOnTouchListener(BubbleTouchListener())
        scope.launch { overlaySettings.settings.collect(::applySettings) }
        scope.launch {
            appSettings.themeMode.collect {
                themeMode = it
                refreshPopup()
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        detachWindows()
        popup.release()
    }

    fun onConfigurationChanged() {
        if (attached) dock()
    }

    // region Settings and windows

    private fun applySettings(new: OverlaySettings) {
        val previous = settings
        settings = new
        if (new.bubbleVisible && !attached) attachWindows()
        if (!new.bubbleVisible && attached) {
            resetScan()
            popup.hide()
            detachWindows()
            state = State.DOCKED
        }
        if (!attached) return
        if (state == State.DOCKED && (previous.dockSide != new.dockSide || previous.dockY != new.dockY || !previous.bubbleVisible)) {
            placeDocked()
        }
        updateAimVisuals()
    }

    private fun attachWindows() {
        windowManager.addView(layerView, layerParams)
        placeDocked()
        windowManager.addView(bubbleView, bubbleParams)
        attached = true
    }

    private fun detachWindows() {
        if (!attached) return
        windowManager.removeView(bubbleView)
        windowManager.removeView(layerView)
        attached = false
    }

    private fun screenBounds(): Box {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return Box(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat())
    }

    /** Screen area not covered by system bars or the display cutout. */
    private fun usableBounds(): Box {
        val metrics = windowManager.maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val bounds = metrics.bounds
        return Box(
            insets.left.toFloat(),
            insets.top.toFloat(),
            (bounds.width() - insets.right).toFloat(),
            (bounds.height() - insets.bottom).toFloat(),
        )
    }

    // endregion

    // region Bubble position and state

    private fun bubbleCenter(): Pair<Float, Float> =
        bubbleParams.x + bubbleSize / 2f to bubbleParams.y + bubbleSize / 2f

    private fun bubbleBox(): Box {
        val (cx, cy) = bubbleCenter()
        return Box.fromCenter(cx, cy, bubbleSize.toFloat(), bubbleSize.toFloat())
    }

    private fun moveBubbleTo(centerX: Float, centerY: Float) {
        bubbleParams.x = (centerX - bubbleSize / 2f).roundToInt()
        bubbleParams.y = (centerY - bubbleSize / 2f).roundToInt()
        if (attached) windowManager.updateViewLayout(bubbleView, bubbleParams)
    }

    private fun placeDocked() {
        val screen = screenBounds()
        val visibleOffset = bubbleSize * DOCK_VISIBLE_FRACTION - bubbleSize / 2f
        val x = if (settings.dockSide == DockSide.RIGHT) screen.right - visibleOffset else screen.left + visibleOffset
        val usable = usableBounds()
        val y = (settings.dockY * screen.height).coerceIn(usable.top + bubbleSize, usable.bottom - bubbleSize)
        bubbleView.docked = true
        moveBubbleTo(x, y)
    }

    /**
     * Returns the bubble to the dock, closing the popup and dropping the OCR result.
     *
     * @param atCurrentHeight dock at the bubble's current height (when dragged into the dock) instead of the saved one.
     */
    private fun dock(side: DockSide = settings.dockSide, atCurrentHeight: Boolean = false) {
        state = State.DOCKED
        resetScan()
        popup.hide()
        layerView.clearAll()
        bubbleView.showCenterDot = false
        haptic()
        val yFraction = if (atCurrentHeight) bubbleCenter().second / screenBounds().height else settings.dockY
        if (side != settings.dockSide || yFraction != settings.dockY) {
            settings = settings.copy(dockSide = side, dockY = yFraction)
            scope.launch { overlaySettings.setDock(side, yFraction) }
        }
        placeDocked()
    }

    private fun aimPoint(): Pair<Float, Float> {
        val (cx, cy) = bubbleCenter()
        return when (settings.aimMode) {
            AimMode.ABOVE_FINGER -> cx to cy - (bubbleSize / 2f + AIM_GAP_DP * density)
            AimMode.BUBBLE_CENTER -> cx to cy
        }
    }

    private fun updateAimVisuals() {
        if (state == State.DOCKED) {
            layerView.clearAim()
            bubbleView.showCenterDot = false
            return
        }
        bubbleView.showCenterDot = settings.aimMode == AimMode.BUBBLE_CENTER
        val (x, y) = aimPoint()
        if (settings.aimMode == AimMode.ABOVE_FINGER) layerView.setAim(x, y) else layerView.clearAim()
    }

    // endregion

    // region Gestures

    private fun startDrag(fromDock: Boolean) {
        state = State.DRAGGING
        bubbleView.docked = false
        if (fromDock) {
            haptic()
            popup.hide()
            startScan(flashLines = false)
        }
        updateAimVisuals()
    }

    private fun dragTo(centerX: Float, centerY: Float) {
        moveBubbleTo(centerX, centerY)
        updateAimVisuals()
        val (x, y) = aimPoint()
        onAim(x, y)
    }

    private fun endDrag() {
        val (cx, _) = bubbleCenter()
        val screen = screenBounds()
        val dockZone = DOCK_ZONE_DP * density
        when {
            cx >= screen.right - dockZone -> dock(DockSide.RIGHT, atCurrentHeight = true)
            cx <= screen.left + dockZone -> dock(DockSide.LEFT, atCurrentHeight = true)
            else -> state = State.FLOATING
        }
    }

    private fun distanceFromDockEdge(x: Float): Float {
        val screen = screenBounds()
        return if (settings.dockSide == DockSide.RIGHT) screen.right - x else x - screen.left
    }

    private fun onTap() {
        if (state != State.FLOATING) return
        val pending = pendingSingleTap
        if (pending != null) {
            mainHandler.removeCallbacks(pending)
            pendingSingleTap = null
            toggleAimMode()
            return
        }
        val single = Runnable {
            pendingSingleTap = null
            startScan(flashLines = true)
        }
        pendingSingleTap = single
        mainHandler.postDelayed(single, ViewConfiguration.getDoubleTapTimeout().toLong())
    }

    private fun toggleAimMode() {
        val next = if (settings.aimMode == AimMode.ABOVE_FINGER) AimMode.BUBBLE_CENTER else AimMode.ABOVE_FINGER
        settings = settings.copy(aimMode = next)
        haptic()
        updateAimVisuals()
        scope.launch { overlaySettings.setAimMode(next) }
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var grabDx = 0f
        private var grabDy = 0f
        private var moved = false
        private var alongDock = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    val (cx, cy) = bubbleCenter()
                    grabDx = event.rawX - cx
                    grabDy = event.rawY - cy
                    moved = false
                    alongDock = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && hypot(dx, dy) > touchSlop) {
                        moved = true
                        if (state == State.DOCKED) {
                            val awayFromEdge = if (settings.dockSide == DockSide.RIGHT) dx < 0 else dx > 0
                            if (abs(dx) > abs(dy) && awayFromEdge) startDrag(fromDock = true) else alongDock = true
                        } else {
                            startDrag(fromDock = false)
                        }
                    }
                    // Moving along the edge repositions the dock; pulling away from the edge undocks, even mid-gesture.
                    if (moved && alongDock && distanceFromDockEdge(event.rawX) > UNDOCK_DISTANCE_DP * density) {
                        alongDock = false
                        startDrag(fromDock = true)
                    }
                    if (moved) {
                        if (alongDock) {
                            val (cx, _) = bubbleCenter()
                            moveBubbleTo(cx, event.rawY - grabDy)
                        } else {
                            dragTo(event.rawX - grabDx, event.rawY - grabDy)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> when {
                    !moved -> if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                        onTap()
                    }
                    alongDock -> {
                        val (_, cy) = bubbleCenter()
                        scope.launch { overlaySettings.setDock(settings.dockSide, cy / screenBounds().height) }
                    }
                    else -> endDrag()
                }
            }
            return true
        }
    }

    // endregion

    // region Scanning and lookup

    private fun resetScan() {
        scanJob?.cancel()
        scanJob = null
        layout = null
        ocrFinal = false
        ocrEngine = null
        ocrOffline = false
        hit = null
        bubbleView.loading = false
        pendingSingleTap?.let(mainHandler::removeCallbacks)
        pendingSingleTap = null
    }

    private fun startScan(flashLines: Boolean) {
        resetScan()
        bubbleView.loading = true
        scanJob = scope.launch {
            val captured = try {
                capture()
            } catch (e: CaptureException) {
                bubbleView.loading = false
                showMessage(
                    service.getString(
                        if (e is CaptureException.SecureWindow) R.string.overlay_error_secure else R.string.overlay_error_capture,
                    ),
                )
                return@launch
            }
            try {
                ocr.recognize(captured.bitmap, Language.JAPANESE)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        bubbleView.loading = false
                        showMessage(service.getString(R.string.overlay_error_ocr))
                    }
                    .collect { update -> onOcrUpdate(update, captured, flashLines) }
            } finally {
                captured.bitmap.recycle()
            }
        }
    }

    private suspend fun capture(): CapturedScreen {
        val (cx, cy) = bubbleCenter()
        if (!capturer.needsOverlayHiding) return capturer.capture(cx.roundToInt(), cy.roundToInt())
        setOverlaysAlpha(0f)
        delay(HIDE_FRAME_MS)
        return try {
            capturer.capture(cx.roundToInt(), cy.roundToInt())
        } finally {
            setOverlaysAlpha(1f)
        }
    }

    private fun setOverlaysAlpha(alpha: Float) {
        bubbleView.alpha = if (alpha == 0f) 0f else if (bubbleView.docked) 0.55f else 1f
        layerView.alpha = alpha
    }

    private fun onOcrUpdate(update: OcrUpdate, captured: CapturedScreen, flashLines: Boolean) {
        val page = update.page.offset(captured.left.toFloat(), captured.top.toFloat())
        val newLayout = TextLayout(page)
        layout = newLayout
        ocrEngine = page.engine
        ocrFinal = update is OcrUpdate.Final
        ocrOffline = (update as? OcrUpdate.Final)?.lensError is OfflineException
        if (ocrFinal) bubbleView.loading = false
        if (flashLines) layerView.flashLines(newLayout.lineBoxes(), FLASH_HOLD_MS)
        hit = null
        if (state == State.DOCKED) return
        val (x, y) = aim ?: aimPoint()
        onAim(x, y)
        if (hit == null && ocrFinal && page.paragraphs.isEmpty()) {
            showMessage(service.getString(R.string.overlay_no_text))
        }
    }

    private fun onAim(x: Float, y: Float) {
        aim = x to y
        val layout = layout ?: return
        val position = layout.hitTest(x, y, HIT_TOLERANCE_DP * density) ?: return
        if (position == hit) return
        hit = position
        haptic()
        showLookup(layout, position)
    }

    private fun showLookup(layout: TextLayout, position: TextPosition) {
        val wordLength = layout.remainingInWord(position)
        val boxes = layout.boxesFor(position, wordLength)
        layerView.setWordBoxes(if (settings.highlightWord) boxes else emptyList())
        val anchor = Box.unionOf(boxes) ?: return
        val state = baseState()
            .put("title", layout.textFrom(position, wordLength))
            .put("lookup", layout.textFrom(position, LOOKUP_LENGTH))
            .put("line", layout.lineText(position))
        popup.show(state, anchor, layout.characterAt(position).vertical, bubbleBox(), usableBounds(), MAX_POPUP_DP * density)
    }

    private fun showMessage(message: String) {
        val (x, y) = aim ?: aimPoint()
        val anchor = Box.fromCenter(x, y, 1f, 1f)
        popup.show(baseState().put("message", message), anchor, false, bubbleBox(), usableBounds(), MAX_POPUP_DP * density)
    }

    private fun refreshPopup() {
        val layout = layout ?: return
        val position = hit ?: return
        if (popup.isShowing) showLookup(layout, position)
    }

    private fun baseState(): JSONObject {
        val engineLabel = when {
            ocrEngine == OcrEngineType.LENS -> service.getString(R.string.overlay_engine_lens)
            ocrEngine == OcrEngineType.ML_KIT && ocrFinal && ocrOffline -> service.getString(R.string.overlay_engine_offline)
            ocrEngine == OcrEngineType.ML_KIT && ocrFinal -> service.getString(R.string.overlay_engine_device)
            ocrEngine == OcrEngineType.ML_KIT -> service.getString(R.string.overlay_engine_draft)
            else -> ""
        }
        return JSONObject()
            .put("theme", if (isDarkTheme()) "dark" else "light")
            .put("pending", scanJob?.isActive == true && !ocrFinal)
            .put("engine", engineLabel)
            .put(
                "labels",
                JSONObject()
                    .put("lookup", service.getString(R.string.overlay_label_lookup))
                    .put("line", service.getString(R.string.overlay_label_line)),
            )
    }

    private fun isDarkTheme(): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun haptic() {
        if (settings.haptics) bubbleView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // endregion

    private companion object {
        const val BUBBLE_DP = 56f
        const val AIM_GAP_DP = 20f
        const val DOCK_VISIBLE_FRACTION = 0.6f
        const val DOCK_ZONE_DP = 32f
        const val UNDOCK_DISTANCE_DP = 64f
        const val HIT_TOLERANCE_DP = 12f
        const val MAX_POPUP_DP = 420f
        const val LOOKUP_LENGTH = 16
        const val FLASH_HOLD_MS = 2500L
        const val HIDE_FRAME_MS = 48L
    }
}
