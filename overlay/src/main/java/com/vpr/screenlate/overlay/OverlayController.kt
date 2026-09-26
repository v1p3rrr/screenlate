package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.LookupResult
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
    private val lookup: DictionaryLookup,
    private val scope: CoroutineScope,
) {
    private enum class State { DOCKED, DRAGGING, FLOATING }

    /** What the popup shows for one lookup; kept to re-render on theme or OCR status changes. */
    private data class LookupView(
        val text: String,
        val matched: Int,
        val results: List<LookupResult>,
        val message: String?,
    )

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val bubbleSize = (BUBBLE_DP * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bubbleView = BubbleView(service)
    private val bubbleParams = OverlayWindows.bubbleParams(bubbleSize)
    private val layerView = LayerView(service)
    private val layerParams = OverlayWindows.layerParams()
    private val popup = PopupController(service, windowManager, PopupCallbacks())
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
    private var lookupJob: Job? = null
    private var shownLookup: LookupView? = null

    // Only Japanese is supported for now; this becomes a setting with more languages.
    private val language = Language.JAPANESE
    private val json = Json

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
        lookupJob?.cancel()
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
        lookupJob?.cancel()
        lookupJob = null
        shownLookup = null
        bubbleView.loading = false
        pendingSingleTap?.let(mainHandler::removeCallbacks)
        pendingSingleTap = null
    }

    private fun startScan(flashLines: Boolean) {
        resetScan()
        loadDictionaryStyles()
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
                ocr.recognize(captured.bitmap, language)
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
        val text = layout.textFrom(position, DictionaryLookup.DEFAULT_SCAN_LENGTH)
        val previous = lookupJob
        lookupJob = scope.launch {
            previous?.cancelAndJoin()
            val shown = shownLookup
            val results = if (shown != null && shown.text == text && popup.isShowing) {
                shown.results
            } else {
                lookupResults(text)
            }
            val matched = results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0
            val boxes = layout.boxesFor(position, matched.coerceAtLeast(1))
            layerView.setWordBoxes(if (settings.highlightWord) boxes else emptyList())
            val anchor = Box.unionOf(boxes) ?: return@launch
            val view = LookupView(text, matched, results, message = if (results.isEmpty()) noResultsMessage() else null)
            shownLookup = view
            popup.show(
                popupState(view),
                anchor,
                layout.characterAt(position).vertical,
                bubbleBox(),
                usableBounds(),
                MAX_POPUP_DP * density,
            )
        }
    }

    private suspend fun lookupResults(text: String, primaryReading: String? = null): List<LookupResult> = try {
        lookup.lookup(text, language, primaryReading = primaryReading)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lookup failed", e)
        emptyList()
    }

    private suspend fun noResultsMessage(): String = service.getString(
        if (lookup.hasTermDictionaries()) R.string.overlay_no_results else R.string.overlay_no_dictionaries,
    )

    /** Looks up a link target from inside the popup and shows it on top of the current view. */
    private fun lookupLink(query: String, primaryReading: String?) {
        scope.launch {
            val results = lookupResults(query, primaryReading)
            val matched = results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0
            val message = if (results.isEmpty()) noResultsMessage() else null
            popup.push(popupState(LookupView(query, matched, results, message)))
        }
    }

    private fun showMessage(message: String) {
        val (x, y) = aim ?: aimPoint()
        val anchor = Box.fromCenter(x, y, 1f, 1f)
        val view = LookupView("", 0, emptyList(), message)
        shownLookup = null
        popup.show(popupState(view), anchor, false, bubbleBox(), usableBounds(), MAX_POPUP_DP * density)
    }

    /** Pushes OCR status and theme changes into the popup without a new lookup. */
    private fun refreshPopup() {
        val view = shownLookup ?: return
        if (popup.isShowing) popup.update(popupState(view))
    }

    private fun loadDictionaryStyles() {
        scope.launch {
            val styles = try {
                lookup.styles(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Loading dictionary styles failed", e)
                return@launch
            }
            popup.setStyles(json.encodeToJsonElement(ListSerializer(DictionaryStyle.serializer()), styles))
        }
    }

    private fun popupState(view: LookupView): JsonObject {
        val engineLabel = when {
            ocrEngine == OcrEngineType.LENS -> service.getString(R.string.overlay_engine_lens)
            ocrEngine == OcrEngineType.ML_KIT && ocrFinal && ocrOffline -> service.getString(R.string.overlay_engine_offline)
            ocrEngine == OcrEngineType.ML_KIT && ocrFinal -> service.getString(R.string.overlay_engine_device)
            ocrEngine == OcrEngineType.ML_KIT -> service.getString(R.string.overlay_engine_draft)
            else -> ""
        }
        return buildJsonObject {
            put("theme", if (isDarkTheme()) "dark" else "light")
            put("pending", scanJob?.isActive == true && !ocrFinal)
            put("engine", engineLabel)
            putJsonObject("source") {
                put("text", view.text)
                put("matched", view.matched)
            }
            put("results", json.encodeToJsonElement(ListSerializer(LookupResult.serializer()), view.results))
            view.message?.let { put("message", it) }
            putJsonObject("labels") {
                put("noResults", service.getString(R.string.overlay_no_results))
            }
        }
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

    private inner class PopupCallbacks : PopupController.Callbacks {
        override fun onClose() = dock()

        override fun onLookup(query: String, primaryReading: String?) = lookupLink(query, primaryReading)

        override fun onOpenUrl(url: String) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { service.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open $url", it) }
            dock()
        }

        override fun media(dictionary: String, path: String): ByteArray? =
            runBlocking { lookup.media(dictionary, path) }
    }

    private companion object {
        const val TAG = "OverlayController"
        const val BUBBLE_DP = 56f
        const val AIM_GAP_DP = 20f
        const val DOCK_VISIBLE_FRACTION = 0.6f
        const val DOCK_ZONE_DP = 32f
        const val UNDOCK_DISTANCE_DP = 64f
        const val HIT_TOLERANCE_DP = 12f
        const val MAX_POPUP_DP = 420f
        const val FLASH_HOLD_MS = 2500L
        const val HIDE_FRAME_MS = 48L
    }
}
