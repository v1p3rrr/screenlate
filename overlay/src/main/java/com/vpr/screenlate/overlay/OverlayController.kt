package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.widget.Toast
import android.widget.PopupMenu
import android.view.ContextThemeWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.net.toUri
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrUpdate
import com.vpr.screenlate.core.ocr.ScreenBands
import com.vpr.screenlate.core.ocr.withMissingFrom
import com.vpr.screenlate.core.ocr.LensPausedException
import com.vpr.screenlate.core.ocr.OfflineException
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.overlay.anki.NoteContext
import com.vpr.screenlate.overlay.anki.PopupNotes
import com.vpr.screenlate.overlay.capture.AccessibilityText
import com.vpr.screenlate.overlay.capture.CaptureException
import com.vpr.screenlate.overlay.capture.CapturedScreen
import com.vpr.screenlate.overlay.capture.ScreenCapturer
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import com.vpr.screenlate.overlay.ui.BubbleView
import com.vpr.screenlate.overlay.ui.CropEditor
import com.vpr.screenlate.overlay.ui.LayerView
import com.vpr.screenlate.overlay.ui.OverlayWindows
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.common.redactUrl

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
    private val pageAppearance: PageAppearance,
    private val anki: AnkiServices,
    private val scope: CoroutineScope,
) {
    /** Anki and audio dependencies, grouped to keep the constructor short. */
    class AnkiServices(
        val ankiDroid: AnkiDroid,
        val notes: AnkiNotes,
        val audio: AudioFinder,
        val audioSettings: AudioSettingsRepository,
        val player: AudioPlayer,
    )

    private enum class State { DOCKED, DRAGGING, FLOATING }

    /**
     * What the popup shows for one lookup; kept to re-render on theme or OCR status changes. [start] is where the
     * looked-up text begins on screen, which is before the aimed character inside a Latin word.
     */
    private data class LookupView(
        val text: String,
        val matched: Int,
        val results: List<LookupResult>,
        val message: String?,
        val start: TextPosition? = null,
    )

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private var bubbleSize = (OverlaySettings.DEFAULT_BUBBLE_DP * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrator = service.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }

    private val bubbleView = BubbleView(service)
    private val bubbleParams = OverlayWindows.bubbleParams(bubbleSize)
    private val layerView = LayerView(service)
    private val layerParams = OverlayWindows.layerParams()
    private val popup = PopupController(service, windowManager, PopupCallbacks())
    private val capturer = ScreenCapturer(service, service.mainExecutor)
    private val accessibilityText = AccessibilityText(service)
    // Only Japanese is supported for now; this becomes a setting with more languages.
    private val language = Language.JAPANESE

    private val popupNotes = PopupNotes(
        context = service,
        scope = scope,
        page = popup.page,
        anki = anki.ankiDroid,
        notes = anki.notes,
        audio = anki.audio,
        audioSettings = anki.audioSettings,
        player = anki.player,
        lookup = lookup,
        language = language,
        noteContext = ::noteContext,
        cropEditor = CropEditor(service, windowManager),
        onAnkiOpened = { dock() },
        onOpenAnkiSettings = {
            service.packageManager.getLaunchIntentForPackage(service.packageName)
                ?.putExtra(OverlayIntents.EXTRA_OPEN, OverlayIntents.OPEN_ANKI_SETTINGS)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?.let { runCatching { service.startActivity(it) } }
            dock()
        },
    )

    private var settings = OverlaySettings()
    private var lastScreen: Box? = null
    private var scanLength = LookupSettings.DEFAULT_SCAN_LENGTH
    private var themeMode = ThemeMode.SYSTEM
    private var state = State.DOCKED
    private var attached = false

    private var scanJob: Job? = null
    private var layout: TextLayout? = null
    private var ocrFinal = false
    private var ocrEngine: OcrEngineType? = null
    private var ocrOffline = false
    private var lensError: Throwable? = null
    private var hit: TextPosition? = null
    private var aim: Pair<Float, Float>? = null
    private var pendingSingleTap: Runnable? = null
    private var foregroundPackage: String? = null
    private var lookupJob: Job? = null
    private var shownLookup: LookupView? = null

    /** Expression and reading of the word the last vibration was for. */
    private var hapticWord: Pair<String, String>? = null
    private var screenshot: CapturedScreen? = null
    private var bandTimer: Job? = null
    private val bandJobs = mutableListOf<Job>()
    private val requestedBands = mutableSetOf<Int>()

    private val json = Json

    fun start() {
        lastScreen = screenBounds()
        bubbleView.setOnTouchListener(BubbleTouchListener())
        bubbleView.glyphLanguage = language.support.languageTag
        bubbleView.glyph = language.support.glyph
        scope.launch { overlaySettings.settings.collect(::applySettings) }
        scope.launch { ocr.warmUp() }
        scope.launch { lookup.settingsUpdates.collect { scanLength = it.scanLength } }
        scope.launch { pageAppearance.json(language).collect { popup.page.setAppearance(it) } }
        scope.launch {
            appSettings.themeMode.collect {
                themeMode = it
                refreshPopup()
            }
        }
    }

    /**
     * Memory runs low: while the bubble is docked nothing needs the on-device model, so it goes, which makes the
     * system less likely to stop the service. The app's own screens going to the background (UI_HIDDEN) do not count.
     */
    @Suppress("DEPRECATION") // The RUNNING_* levels are still delivered to running services.
    fun onTrimMemory(level: Int) {
        val low = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        Log.i(TAG, "Memory trim level $level, bubble ${state.name.lowercase()}")
        if (low && state == State.DOCKED) ocr.releaseOnDevice()
    }

    fun stop() {
        scanJob?.cancel()
        lookupJob?.cancel()
        popupNotes.release()
        mainHandler.removeCallbacksAndMessages(null)
        detachWindows()
        popup.release()
    }

    /**
     * Rotation or a resized window closes the popup and docks the bubble (the dock keeps its side and relative
     * height); other changes such as the system theme only redraw the popup.
     */
    fun onConfigurationChanged() {
        val screen = screenBounds()
        val resized = screen != lastScreen
        lastScreen = screen
        if (!attached) return
        if (resized) dock() else refreshPopup()
    }

    /** The app in the foreground changed; the bubble hides in apps the user excluded. */
    fun onForegroundApp(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        applySettings(settings)
    }

    private fun shouldShowBubble(): Boolean = settings.bubbleVisible && foregroundPackage !in settings.hiddenPackages

    // region Settings and windows

    private fun applySettings(new: OverlaySettings) {
        val previous = settings
        settings = new
        val show = shouldShowBubble()
        val size = (new.bubbleSizeDp * density).roundToInt()
        if (size != bubbleSize) resizeBubble(size)
        if (show && !attached) attachWindows()
        if (!show && attached) {
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

    /** Window order from bottom to top: highlight layer, popup, bubble; the bubble must never go under the popup. */
    private fun attachWindows() {
        windowManager.addView(layerView, layerParams)
        popup.attach()
        placeDocked()
        windowManager.addView(bubbleView, bubbleParams)
        attached = true
    }

    private fun detachWindows() {
        if (!attached) return
        windowManager.removeView(bubbleView)
        popup.detach()
        windowManager.removeView(layerView)
        attached = false
    }

    private fun resizeBubble(size: Int) {
        val (cx, cy) = bubbleCenter()
        bubbleSize = size
        bubbleParams.width = size
        bubbleParams.height = size
        if (state == State.DOCKED) placeDocked() else moveBubbleTo(cx, cy)
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
        val visibleOffset = bubbleSize * BubbleView.DOCK_VISIBLE_FRACTION - bubbleSize / 2f
        val x = if (settings.dockSide == DockSide.RIGHT) screen.right - visibleOffset else screen.left + visibleOffset
        val usable = usableBounds()
        val y = (settings.dockY * screen.height).coerceIn(usable.top + bubbleSize, usable.bottom - bubbleSize)
        bubbleView.dockSide = settings.dockSide
        bubbleView.docked = true
        moveBubbleTo(x, y)
    }

    /**
     * Closes the popup only, as in Poe: the bubble stays where it is and keeps the OCR result, so aiming again looks up
     * at once, the same word included.
     */
    private fun closePopup() {
        lookupJob?.cancel()
        lookupJob = null
        popup.hide()
        popupNotes.onClosed()
        layerView.setWordBoxes(emptyList())
        shownLookup = null
        hapticWord = null
        hit = null
    }

    /**
     * Returns the bubble to the dock, closing the popup and dropping the OCR result.
     *
     * @param atCurrentHeight dock at the bubble's current height (when dragged into the dock) instead of the saved one.
     */
    private fun dock(side: DockSide = settings.dockSide, atCurrentHeight: Boolean = false) {
        if (state != State.DOCKED) Log.d(TAG, "Bubble docked")
        state = State.DOCKED
        resetScan()
        popup.hide()
        popupNotes.onClosed()
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
        if (fromDock) Log.d(TAG, "Bubble pulled out")
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

    /** Docks only when the finger is lifted at the very edge, so words next to the edge stay reachable. */
    private fun endDrag(fingerX: Float) {
        val screen = screenBounds()
        val dockZone = DOCK_ZONE_DP * density
        when {
            fingerX >= screen.right - dockZone -> dock(DockSide.RIGHT, atCurrentHeight = true)
            fingerX <= screen.left + dockZone -> dock(DockSide.LEFT, atCurrentHeight = true)
            else -> {
                state = State.FLOATING
                popupNotes.onAimSettled()
            }
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

    /** Holding the floating bubble: copy the paragraph under the aim, or everything recognized, as whole text. */
    private fun showCopyMenu() {
        val layout = layout ?: return
        val (x, y) = aim ?: aimPoint()
        val aimed = hit ?: layout.hitTest(x, y, HIT_TOLERANCE_DP * density)
        val paragraph = aimed?.let { layout.paragraphText(it).first.trim() }.orEmpty()
        val all = layout.paragraphs
            .map { characters -> characters.joinToString("") { it.text }.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = System.lineSeparator())
        if (paragraph.isEmpty() && all.isEmpty()) return
        haptic()
        val themed = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault_DayNight)
        PopupMenu(themed, bubbleView).apply {
            if (paragraph.isNotEmpty()) menu.add(service.getString(R.string.overlay_copy_paragraph)).setOnMenuItemClickListener {
                copyText(paragraph)
                true
            }
            if (all.isNotEmpty()) menu.add(service.getString(R.string.overlay_copy_all)).setOnMenuItemClickListener {
                copyText(all)
                true
            }
            show()
        }
    }

    private fun copyText(text: String) {
        Log.d(TAG, "Copied ${text.length} characters from the bubble menu")
        PageState.copy(service, text)
        // Android 13 and later show what was copied themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(service, R.string.overlay_copied, Toast.LENGTH_SHORT).show()
        }
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
        private var held = false
        private val hold = Runnable {
            held = true
            showCopyMenu()
        }

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
                    held = false
                    if (state == State.FLOATING) {
                        mainHandler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && !held && hypot(dx, dy) > touchSlop) {
                        mainHandler.removeCallbacks(hold)
                        moved = true
                        if (state == State.DOCKED) {
                            val awayFromEdge = if (settings.dockSide == DockSide.RIGHT) dx < 0 else dx > 0
                            // Pulling out is the main gesture: only a clearly vertical move moves the dock instead.
                            val alongEdge = abs(dy) > abs(dx) * ALONG_DOCK_RATIO || !awayFromEdge
                            if (alongEdge) alongDock = true else startDrag(fromDock = true)
                        } else {
                            startDrag(fromDock = false)
                        }
                    }
                    // Moving along the edge repositions the dock; pulling away from the edge undocks, even mid-gesture.
                    val pulledAway = distanceFromDockEdge(event.rawX) - distanceFromDockEdge(downX)
                    if (moved && alongDock && pulledAway > UNDOCK_DISTANCE_DP * density) {
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
                    held -> Unit
                    !moved -> if (event.actionMasked == MotionEvent.ACTION_UP) {
                        mainHandler.removeCallbacks(hold)
                        view.performClick()
                        onTap()
                    }
                    alongDock -> {
                        val (_, cy) = bubbleCenter()
                        scope.launch { overlaySettings.setDock(settings.dockSide, cy / screenBounds().height) }
                    }
                    else -> endDrag(event.rawX)
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
        bandTimer?.cancel()
        bandJobs.forEach { it.cancel() }
        bandJobs.clear()
        requestedBands.clear()
        layout = null
        ocrFinal = false
        ocrEngine = null
        ocrOffline = false
        lensError = null
        hit = null
        lookupJob?.cancel()
        lookupJob = null
        shownLookup = null
        hapticWord = null
        screenshot?.bitmap?.recycle()
        screenshot = null
        bubbleView.loading = false
        pendingSingleTap?.let(mainHandler::removeCallbacks)
        pendingSingleTap = null
    }

    private fun startScan(flashLines: Boolean) {
        Log.d(TAG, "Scan started (${if (flashLines) "tap" else "pull-out"}, text source ${settings.textSource})")
        resetScan()
        loadDictionaryStyles()
        bubbleView.loading = true
        scanJob = scope.launch {
            val started = SystemClock.elapsedRealtime()
            // App text is exact and works in windows that forbid screenshots. It needs no screenshot, so it is read
            // while the screen is captured and shown as soon as it is there. OCR still runs to add the text the app
            // does not expose, such as text in images.
            val appTextRead = async { if (settings.textSource == TextSource.APP_TEXT) readAppText() else null }
            var captureError: CaptureException? = null
            val captured = try {
                capture()
            } catch (e: CaptureException) {
                captureError = e
                null
            }
            // Once recognition has shown a page (which holds the app text), the app text alone must not replace it.
            var recognized = false
            launch {
                val appText = appTextRead.await() ?: return@launch
                if (!recognized) onPage(appText, final = captured == null, lensError = null, flashLines = flashLines)
            }
            if (captured == null) {
                val appText = appTextRead.await()
                bubbleView.loading = false
                if (appText == null) {
                    showMessage(
                        service.getString(
                            if (captureError is CaptureException.SecureWindow) R.string.overlay_error_secure else R.string.overlay_error_capture,
                        ),
                    )
                }
                return@launch
            }
            try {
                ocr.recognize(captured.bitmap, language, focus = { scanFocus(captured) })
                    .catch { error ->
                        if (error is CancellationException) throw error
                        bubbleView.loading = false
                        val appText = appTextRead.await()
                        if (appText != null) {
                            recognized = true
                            onPage(appText, final = true, lensError = error, flashLines = false)
                        } else {
                            showMessage(service.getString(R.string.overlay_error_ocr))
                        }
                    }
                    .collect { update ->
                        logUpdate(update, SystemClock.elapsedRealtime() - started)
                        val appText = appTextRead.await()
                        recognized = true
                        val page = update.page.offset(captured.left.toFloat(), captured.top.toFloat())
                        onPage(
                            page = appText?.withMissingFrom(page) ?: page,
                            final = update is OcrUpdate.Final,
                            lensError = (update as? OcrUpdate.Final)?.lensError,
                            // Around app text, the lines OCR adds (text in images) flash as well.
                            flashLines = flashLines,
                        )
                    }
            } catch (e: CancellationException) {
                captured.bitmap.recycle()
                throw e
            }
            // Kept for {screenshot} and small-text bands until the next scan or docking.
            screenshot = captured
            if (settings.smallText == SmallTextMode.ALWAYS) {
                ScreenBands.of(captured.bitmap.width, captured.bitmap.height).indices.forEach { refineBand(captured, it) }
            }
        }
    }

    /** The aim's row in [captured], so the on-device draft reads the text there first. */
    private fun scanFocus(captured: CapturedScreen): Float? {
        if (state == State.DOCKED) return null
        val (_, y) = aim ?: aimPoint()
        return y - captured.top
    }

    private fun logUpdate(update: OcrUpdate, millis: Long) {
        val kind = if (update is OcrUpdate.Final) "final" else "draft"
        val error = (update as? OcrUpdate.Final)?.lensError?.let { ", Lens: ${it::class.simpleName}" }.orEmpty()
        Log.i(TAG, "OCR $kind from ${update.page.engine}: ${update.page.paragraphs.size} paragraphs in $millis ms$error")
    }

    /** On-demand small text: the aim rests where nothing was recognized, so its band goes to Lens once. */
    private fun scheduleBandAt(y: Float) {
        if (settings.smallText != SmallTextMode.ON_DEMAND || !ocrFinal) return
        val shot = screenshot ?: return
        bandTimer?.cancel()
        bandTimer = scope.launch {
            delay(BAND_DELAY_MS)
            val bands = ScreenBands.of(shot.bitmap.width, shot.bitmap.height)
            refineBand(shot, ScreenBands.nearest(bands, y - shot.top))
        }
    }

    /** Recognizes one band of [shot] again and adds the lines found where the page had no text. */
    private fun refineBand(shot: CapturedScreen, index: Int) {
        if (!requestedBands.add(index)) return
        val band = ScreenBands.of(shot.bitmap.width, shot.bitmap.height)[index]
        // Cropped here, on the main thread, where resetScan recycles the screenshot.
        val crop = Bitmap.createBitmap(
            shot.bitmap,
            band.left.toInt(),
            band.top.toInt(),
            band.width.toInt(),
            band.height.toInt(),
        )
        bandJobs += scope.launch {
            val found = try {
                ocr.recognizeRegion(crop, language)
            } finally {
                crop.recycle()
            }
            val current = layout ?: return@launch
            val added = found?.offset(shot.left.toFloat(), shot.top + band.top) ?: return@launch
            val merged = current.page.withMissingFrom(added)
            Log.d(TAG, "Band $index added ${merged.paragraphs.size - current.page.paragraphs.size} paragraphs")
            if (merged === current.page) return@launch
            layout = TextLayout(merged)
            hit = null
            if (state != State.DOCKED) aim?.let { (x, y) -> onAim(x, y) }
        }
    }

    private suspend fun readAppText(): OcrPage? {
        val screen = screenBounds()
        return withContext(Dispatchers.Default) {
            runCatching { accessibilityText.read(screen.width.toInt(), screen.height.toInt()) }
                .onFailure { Log.w(TAG, "Reading app text failed", it.redacted()) }
                .getOrNull()
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

    /** A new text layout in screen coordinates: the OCR draft or the final result. */
    private fun onPage(page: OcrPage, final: Boolean, lensError: Throwable?, flashLines: Boolean) {
        val newLayout = TextLayout(page)
        layout = newLayout
        ocrEngine = page.engine
        ocrFinal = final
        ocrOffline = lensError is OfflineException
        if (final) this.lensError = lensError
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
        val position = layout.hitTest(x, y, HIT_TOLERANCE_DP * density)
        if (position == null) {
            scheduleBandAt(y)
            return
        }
        bandTimer?.cancel()
        if (position == hit) return
        hit = position
        showLookup(layout, position)
    }

    private fun showLookup(layout: TextLayout, aimed: TextPosition) {
        val position = wordStart(layout, aimed)
        val text = layout.textFrom(position, scanLength)
        val previous = lookupJob
        lookupJob = scope.launch {
            previous?.cancelAndJoin()
            val shown = shownLookup
            val results = if (shown != null && shown.text == text && popup.isShowing) {
                shown.results
            } else {
                lookupResults(text)
            }
            // Nothing found: no popup, as with Yomitan's auto-hide. Only a missing dictionary is worth a message.
            if (results.isEmpty() && lookup.hasTermDictionaries()) {
                layerView.setWordBoxes(emptyList())
                shownLookup = null
                hapticWord = null
                popup.hide()
                popupNotes.onResultsHidden()
                return@launch
            }
            val matched = results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0
            val boxes = layout.boxesFor(position, matched.coerceAtLeast(1))
            layerView.setWordBoxes(if (settings.highlightWord) boxes else emptyList())
            val anchor = Box.unionOf(boxes) ?: return@launch
            val message = if (results.isEmpty()) noResultsMessage() else null
            val view = LookupView(text, matched, results, message, start = position)
            shownLookup = view
            val word = results.firstOrNull()?.term?.let { it.expression to it.reading }
            if (word != null && word != hapticWord) haptic()
            hapticWord = word
            popupNotes.refreshActions()
            val state = popupStateOffMain(view)
            popup.show(
                state,
                anchor,
                layout.characterAt(position).vertical,
                bubbleBox(),
                usableBounds(),
                MAX_POPUP_DP * density,
            )
            popupNotes.onResultsShown(results.firstOrNull()?.term?.let { it.expression to it.reading })
        }
    }

    private suspend fun lookupResults(text: String, primaryReading: String? = null): List<LookupResult> = try {
        val started = System.currentTimeMillis()
        lookup.lookup(text, language, primaryReading = primaryReading).also {
            Log.d(TAG, "Lookup found ${it.size} entries in ${System.currentTimeMillis() - started} ms")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lookup failed", e.redacted())
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
            popup.push(popupStateOffMain(LookupView(query, matched, results, message)))
            popupNotes.onResultsShown(results.firstOrNull()?.term?.let { it.expression to it.reading })
        }
    }

    private fun showMessage(message: String) {
        val (x, y) = aim ?: aimPoint()
        val anchor = Box.fromCenter(x, y, 1f, 1f)
        val view = LookupView("", 0, emptyList(), message)
        shownLookup = null
        popup.show(popupState(view), anchor, false, bubbleBox(), usableBounds(), MAX_POPUP_DP * density)
    }

    /** Where the lookup aimed at [aimed] starts: the first character of a word the language reads as a whole. */
    private fun wordStart(layout: TextLayout, aimed: TextPosition): TextPosition {
        val characters = layout.paragraphs[aimed.paragraphIndex]
        val before = characters.subList(max(0, aimed.offset - WORD_LOOKBACK), aimed.offset).joinToString("") { it.text }
        val back = language.support.wordStartOffset(before, characters[aimed.offset].text)
        return if (back == 0) aimed else aimed.copy(offset = aimed.offset - back)
    }

    /** Pushes OCR status and theme changes into the popup without a new lookup. */
    private fun refreshPopup() {
        val view = shownLookup ?: return
        if (popup.isShowing) popup.update(popupState(view))
    }

    /** Sentence and screenshot for a note; waits for the final OCR result first, as Lens may still refine the text. */
    private suspend fun noteContext(): NoteContext {
        scanJob?.join()
        val layout = layout
        val position = shownLookup?.start ?: hit
        val sentence = if (layout != null && position != null) {
            val (paragraph, index) = layout.paragraphText(position)
            val length = layout.textFrom(position, shownLookup?.matched?.coerceAtLeast(1) ?: 1).length
            Sentence.extract(paragraph, index, length, language)
        } else {
            null
        }
        val focus = if (layout != null && position != null) {
            val padding = FOCUS_PADDING_DP * density
            Box.unionOf(layout.readingParagraphs[position.paragraphIndex].lines.map { it.box })
                ?.let { RectF(it.left - padding, it.top - padding, it.right + padding, it.bottom + padding) }
        } else {
            null
        }
        val shot = screenshot
        return NoteContext(
            sentence = sentence,
            screenshot = shot?.bitmap,
            screenshotLeft = shot?.left?.toFloat() ?: 0f,
            screenshotTop = shot?.top?.toFloat() ?: 0f,
            focus = focus,
            documentTitle = foregroundPackage?.let(::appLabel).orEmpty(),
        )
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val packageManager = service.packageManager
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

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
            popup.page.setStyles(json.encodeToJsonElement(ListSerializer(DictionaryStyle.serializer()), styles))
            val tagNotes = runCatching { lookup.tagNotes() }
                .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Loading tag descriptions failed", it) }
                .getOrDefault(emptyList())
            popup.page.setTagNotes(json.encodeToJsonElement(ListSerializer(DictionaryTagNotes.serializer()), tagNotes))
        }
    }

    /** [popupState] for large result sets: the OCR status is read here, the JSON is written on a worker thread. */
    private suspend fun popupStateOffMain(view: LookupView): String {
        val dark = isDarkTheme()
        val pending = scanJob?.isActive == true && !ocrFinal
        val engine = engineLabel()
        val hideSource = !settings.showSourceText
        val ocrError = ocrErrorText()
        return withContext(Dispatchers.Default) {
            PageState.build(service, dark, view.text, view.matched, view.results, view.message, pending, engine, hideSource, ocrError)
        }
    }

    private fun engineLabel(): String = engineLabel(aimedEngine())

    /** Where the word under the aim came from: text added around app text keeps its own engine. */
    private fun aimedEngine(): OcrEngineType? {
        val paragraphEngine = hit?.let { position -> layout?.readingParagraphs?.getOrNull(position.paragraphIndex)?.engine }
        return paragraphEngine ?: ocrEngine
    }

    private fun engineLabel(ocrEngine: OcrEngineType?): String = when {
        ocrEngine == OcrEngineType.LENS -> service.getString(R.string.overlay_engine_lens)
        ocrEngine == OcrEngineType.ACCESSIBILITY -> service.getString(R.string.overlay_engine_app_text)
        ocrEngine == OcrEngineType.ML_KIT && ocrFinal && ocrOffline -> service.getString(R.string.overlay_engine_offline)
        ocrEngine == OcrEngineType.ML_KIT && ocrFinal -> service.getString(R.string.overlay_engine_device)
        ocrEngine == OcrEngineType.ML_KIT -> service.getString(R.string.overlay_engine_draft)
        else -> ""
    }

    private fun popupState(view: LookupView): String {
        val engineLabel = engineLabel()
        return PageState.build(
            context = service,
            dark = isDarkTheme(),
            text = view.text,
            matched = view.matched,
            results = view.results,
            message = view.message,
            pending = scanJob?.isActive == true && !ocrFinal,
            engine = engineLabel,
            hideSource = !settings.showSourceText,
            ocrError = ocrErrorText(),
        )
    }

    /** Why cloud recognition failed for this scan, for the ⚠ next to the engine label; empty when it did not. */
    private fun ocrErrorText(): String {
        val error = lensError ?: return ""
        // A word read from the app's own text does not depend on recognition.
        if (aimedEngine() == OcrEngineType.ACCESSIBILITY) return ""
        val reason = when (error) {
            is OfflineException -> service.getString(R.string.overlay_ocr_error_offline)
            is LensPausedException -> service.getString(R.string.overlay_ocr_error_paused)
            is TimeoutCancellationException, is SocketTimeoutException -> service.getString(R.string.overlay_ocr_error_timeout)
            is LensHttpException -> when {
                error.code == HTTP_TOO_MANY_REQUESTS -> service.getString(R.string.overlay_ocr_error_too_many)
                error.code == HTTP_FORBIDDEN -> service.getString(R.string.overlay_ocr_error_refused)
                error.code >= HTTP_SERVER_ERROR -> service.getString(R.string.overlay_ocr_error_server, error.code)
                else -> service.getString(R.string.overlay_ocr_error_http, error.code)
            }
            is IOException -> service.getString(R.string.overlay_ocr_error_network)
            else -> service.getString(R.string.overlay_ocr_error_other)
        }
        return "$reason ${service.getString(R.string.overlay_ocr_error_fallback)}"
    }

    private fun isDarkTheme(): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * A short click from the vibrator. View haptic feedback, and plain vibrations this short, follow the system's
     * touch feedback switch, which many users turn off; the media usage keeps the click that the user enabled here.
     */
    private fun haptic() {
        val vibrator = vibrator?.takeIf { settings.haptics } ?: return
        val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(click, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            vibrator.vibrate(click)
        }
    }

    // endregion

    private inner class PopupCallbacks : LookupPage.Callbacks {
        override fun onClose() = closePopup()

        override fun onLookup(query: String, primaryReading: String?) = lookupLink(query, primaryReading)

        override fun onOpenUrl(url: String) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { service.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open ${redactUrl(url)}", it.redacted()) }
            dock()
        }

        override fun onCopy(text: String) = PageState.copy(service, text)

        override fun onKanji(character: String) {
            scope.launch {
                val result = runCatching { lookup.kanji(character, language) }.getOrElse { KanjiResult(character) }
                popup.push(PageState.kanji(service, isDarkTheme(), result, service.getString(R.string.overlay_no_kanji)))
            }
        }

        override fun media(dictionary: String, path: String): ByteArray? =
            runBlocking { lookup.media(dictionary, path) }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val TAG = "OverlayController"
        const val AIM_GAP_DP = 20f
        const val DOCK_ZONE_DP = 12f
        /** How far a finger moving the dock must then pull away from the edge to take the bubble out. */
        const val UNDOCK_DISTANCE_DP = 24f

        /** A move from the dock this much more vertical than horizontal (about 60°) moves the dock. */
        const val ALONG_DOCK_RATIO = 1.7f
        const val HIT_TOLERANCE_DP = 12f

        /** Characters before the aim that may belong to the aimed word. */
        const val WORD_LOOKBACK = 32
        const val MAX_POPUP_DP = 420f
        const val FLASH_HOLD_MS = 2500L
        const val HIDE_FRAME_MS = 48L
        const val FOCUS_PADDING_DP = 16f
        const val BAND_DELAY_MS = 300L
    }
}
