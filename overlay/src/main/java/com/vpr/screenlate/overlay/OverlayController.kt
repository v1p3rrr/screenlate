package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
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
import com.vpr.screenlate.core.common.redactUrl
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.common.settings.isDark
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.LensPausedException
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrOptions
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrUpdate
import com.vpr.screenlate.core.ocr.OfflineException
import com.vpr.screenlate.core.ocr.ScreenBands
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import com.vpr.screenlate.core.ocr.withMissingFrom
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.overlay.anki.NoteContext
import com.vpr.screenlate.overlay.anki.NoteSource
import com.vpr.screenlate.overlay.anki.PopupNotes
import com.vpr.screenlate.overlay.capture.AccessibilityText
import com.vpr.screenlate.overlay.capture.CaptureException
import com.vpr.screenlate.overlay.capture.CapturedScreen
import com.vpr.screenlate.overlay.capture.ScreenCapturer
import com.vpr.screenlate.overlay.capture.SharedScreenshot
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import com.vpr.screenlate.overlay.ui.BubbleMenu
import com.vpr.screenlate.overlay.ui.BubbleView
import com.vpr.screenlate.overlay.ui.CropEditor
import com.vpr.screenlate.overlay.ui.CropFocus
import com.vpr.screenlate.overlay.ui.LayerView
import com.vpr.screenlate.overlay.ui.OverlayWindows
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import com.vpr.screenlate.overlay.web.noResultsText
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
        /** The layout [start] belongs to; a newer scan result may have replaced the current one since. */
        val layout: TextLayout? = null,
        /** The first character's kanji entry, shown when no word was found. */
        val kanji: KanjiResult? = null,
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
    private val bubbleMenu = BubbleMenu(service, windowManager)
    // The screenshot callback copies the image into a bitmap, which should not hold up the main thread.
    private val capturer = ScreenCapturer(service, Dispatchers.Default.asExecutor())
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
        noteSource = ::noteSource,
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
    private var eInk = false
    private var state = State.DOCKED
    private var attached = false

    private var scanJob: Job? = null

    /** Counts scans; a note compares it to tell whether the scan it was started in is still open. */
    private var scanId = 0

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
    private var screenshot: SharedScreenshot? = null
    private var onDeviceLoaded = false
    private var bandTimer: Job? = null
    private val bandJobs = mutableListOf<Job>()
    private val requestedBands = mutableSetOf<Int>()

    private val json = Json

    /** What the page was given last; the lookup keeps the same lists until the dictionaries change. */
    private var pageStyles: List<DictionaryStyle>? = null
    private var pageTagNotes: List<DictionaryTagNotes>? = null

    fun start() {
        lastScreen = screenBounds()
        bubbleView.setOnTouchListener(BubbleTouchListener())
        bubbleView.glyphLanguage = language.support.languageTag
        bubbleView.glyph = language.support.glyph
        scope.launch { overlaySettings.settings.collect(::applySettings) }
        scope.launch { lookup.settingsUpdates.collect { scanLength = it.scanLength } }
        scope.launch { pageAppearance.json(language).collect { popup.page.setAppearance(it) } }
        scope.launch {
            appSettings.themeMode.collect {
                themeMode = it
                refreshPopup()
            }
        }
        scope.launch {
            appSettings.eInk.collect {
                eInk = it
                bubbleView.eInk = it
                layerView.eInk = it
                refreshPopup()
            }
        }
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

    /**
     * The app in the foreground changed; the bubble hides in apps the user excluded. The bubble's menu closes: it takes
     * no focus, so Home and Back do not close it, and its items belong to the screen it was opened on.
     */
    fun onForegroundApp(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        bubbleMenu.dismiss()
        applySettings(settings)
    }

    private fun shouldShowBubble(): Boolean = settings.bubbleVisible && foregroundPackage !in settings.hiddenPackages

    // region Settings and windows

    private fun applySettings(new: OverlaySettings) {
        val previous = settings
        settings = new
        loadOnDevice(new.textSource != TextSource.APP_TEXT_ONLY && new.ocrEngines != OcrEngines.CLOUD)
        val show = shouldShowBubble()
        val size = (new.bubbleSizeDp * density).roundToInt()
        if (size != bubbleSize) resizeBubble(size)
        if (show && !attached) attachWindows()
        if (!show && attached) {
            closeScan()
            detachWindows()
            state = State.DOCKED
        }
        if (!attached) return
        if (state == State.DOCKED && (previous.dockSide != new.dockSide || previous.dockY != new.dockY || !previous.bubbleVisible)) {
            placeDocked()
        }
        updateAimVisuals()
    }

    /** Loads the on-device model ahead of the first scan, or frees it while the device does not recognize. */
    private fun loadOnDevice(needed: Boolean) {
        if (needed == onDeviceLoaded) return
        onDeviceLoaded = needed
        if (needed) scope.launch { ocr.warmUp(language) } else ocr.releaseOnDevice()
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
        bubbleMenu.dismiss()
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
     * Closes the popup only: the bubble stays where it is and keeps the OCR result, so aiming again looks up
     * at once, the same word included.
     */
    private fun closePopup() {
        lookupJob?.cancel()
        lookupJob = null
        popup.hide()
        popupNotes.onResultsHidden()
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
        closeScan()
        haptic()
        val yFraction = if (atCurrentHeight) bubbleCenter().second / screenBounds().height else settings.dockY
        if (side != settings.dockSide || yFraction != settings.dockY) {
            settings = settings.copy(dockSide = side, dockY = yFraction)
            scope.launch { overlaySettings.setDock(side, yFraction) }
        }
        placeDocked()
    }

    /**
     * Drops the scan and everything shown for it: popup, highlights and an open crop editor. A note already being added
     * still finishes.
     */
    private fun closeScan() {
        bubbleMenu.dismiss()
        resetScan()
        popup.hide()
        popupNotes.onClosed()
        layerView.clearAll()
        bubbleView.showCenterDot = false
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

    /**
     * Docks when the bubble's center has crossed the edge or the finger is lifted at the very edge, so words next to the
     * edge stay reachable. The center counts too because the finger may hold the bubble off-center, and curved screens
     * often report no touches at the edge itself.
     */
    private fun endDrag(fingerX: Float) {
        val screen = screenBounds()
        val dockZone = DOCK_ZONE_DP * density
        val centerX = bubbleCenter().first
        when {
            fingerX >= screen.right - dockZone || centerX >= screen.right -> dock(DockSide.RIGHT, atCurrentHeight = true)
            fingerX <= screen.left + dockZone || centerX <= screen.left -> dock(DockSide.LEFT, atCurrentHeight = true)
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

    /**
     * Holding the floating bubble: copy the paragraph under the aim or everything recognized, as whole text, or open
     * the app.
     */
    private fun showBubbleMenu() {
        val layout = layout
        val paragraph = if (layout == null) "" else {
            val (x, y) = aim ?: aimPoint()
            val aimed = layout.hitTest(x, y, HIT_TOLERANCE_DP * density)
            val shown = shownLookup?.takeIf { popup.isShowing }?.let { view ->
                val start = view.start ?: return@let null
                (view.layout ?: return@let null) to start
            }
            CopyMenuText.paragraph(layout, aimed, shown)
        }
        val all = layout?.let { CopyMenuText.all(it, System.lineSeparator()) }.orEmpty()
        haptic()
        val items = buildList {
            if (paragraph.isNotEmpty()) add(BubbleMenu.Item(service.getString(R.string.overlay_copy_paragraph)) { copyText(paragraph) })
            if (all.isNotEmpty()) add(BubbleMenu.Item(service.getString(R.string.overlay_copy_all)) { copyText(all) })
            add(BubbleMenu.Item(service.getString(R.string.overlay_menu_open_app), ::openApp))
        }
        bubbleMenu.show(items, bubbleBox(), usableBounds())
    }

    /** Brings the app to the front as the launcher does; the bubble docks so it does not hang over the app. */
    private fun openApp() {
        service.packageManager.getLaunchIntentForPackage(service.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.let { intent -> runCatching { service.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open the app", it) } }
        dock()
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
            showBubbleMenu()
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // A cancelled touch must not open the menu later.
                    mainHandler.removeCallbacks(hold)
                    when {
                        held -> Unit
                        !moved -> if (event.actionMasked == MotionEvent.ACTION_UP) {
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
            }
            return true
        }
    }

    // endregion

    // region Scanning and lookup

    private fun resetScan() {
        scanId++
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
        // A note still using the screenshot keeps it until it is done.
        screenshot?.release()
        screenshot = null
        bubbleView.loading = false
        pendingSingleTap?.let(mainHandler::removeCallbacks)
        pendingSingleTap = null
    }

    private fun startScan(flashLines: Boolean) {
        Log.d(TAG, "Scan started (${if (flashLines) "tap" else "pull-out"}, text source ${settings.textSource})")
        // The old popup's word belongs to the old screen; ➕ there would take its sentence from the new scan.
        closePopup()
        resetScan()
        popup.page.prepare()
        loadDictionaryStyles()
        bubbleView.loading = true
        scanJob = scope.launch {
            if (settings.textSource == TextSource.APP_TEXT_ONLY) {
                readAppTextOnly(flashLines)
                return@launch
            }
            val started = SystemClock.elapsedRealtime()
            // App text is exact and works in windows that forbid screenshots. It needs no screenshot, so it is read
            // while the screen is captured and shown as soon as it is there. OCR still runs to add the text the app
            // does not expose, such as text in images.
            val appTextRead = async { if (settings.textSource != TextSource.SCREEN) readAppText() else null }
            var captureError: CaptureException? = null
            val captured = try {
                capture()
            } catch (e: CaptureException) {
                captureError = e
                null
            }
            // Neither source waits for the other: whichever arrives is shown, merged with what the other has so far.
            var appText: OcrPage? = null
            var recognized: Recognized? = null
            fun show(flash: Boolean) {
                val ocrPage = recognized?.page
                val page = if (ocrPage == null) appText ?: return else appText?.withMissingFrom(ocrPage) ?: ocrPage
                onPage(
                    page = page,
                    final = recognized?.final ?: (captured == null),
                    lensError = recognized?.lensError,
                    flashLines = flash,
                )
            }
            launch {
                appText = appTextRead.await() ?: return@launch
                show(flashLines)
            }
            if (captured == null) {
                val read = appTextRead.await()
                if (read == null) {
                    showScanError(
                        if (captureError is CaptureException.SecureWindow) R.string.overlay_error_secure else R.string.overlay_error_capture,
                    )
                }
                return@launch
            }
            // Kept for {screenshot} and small-text bands until the next scan or docking; recognition holds it as well.
            val shot = SharedScreenshot(captured).also { screenshot = it }
            shot.retain()
            try {
                ocr.recognize(captured.bitmap, language, ocrOptions(), focus = { scanFocus(captured) })
                    .catch { error ->
                        if (error is CancellationException) throw error
                        bubbleView.loading = false
                        recognized = Recognized(page = null, final = true, lensError = error)
                        if (appTextRead.await() != null) {
                            show(flash = false)
                        } else {
                            // Offline fails the scan only while the device does not recognize; a cloud-only scan says why
                            // the cloud failed.
                            showScanError(
                                when {
                                    error is OfflineException -> service.getString(R.string.overlay_error_offline)
                                    settings.ocrEngines == OcrEngines.CLOUD -> cloudErrorReason(error)
                                    else -> service.getString(R.string.overlay_error_ocr)
                                },
                            )
                        }
                    }
                    .collect { update ->
                        logUpdate(update, SystemClock.elapsedRealtime() - started)
                        recognized = Recognized(
                            page = update.page.offset(captured.left.toFloat(), captured.top.toFloat()),
                            final = update is OcrUpdate.Final,
                            lensError = (update as? OcrUpdate.Final)?.lensError,
                        )
                        // Around app text, the lines OCR adds (text in images) flash as well.
                        show(flashLines)
                    }
            } finally {
                shot.release()
            }
            if (!ocrFinal) {
                // Recognition failed without a final page: nothing more will come, and what is shown stays.
                ocrFinal = true
                bubbleView.loading = false
                refreshPopup()
            }
            if (smallText() == SmallTextMode.ALWAYS) {
                ScreenBands.of(captured.bitmap.width, captured.bitmap.height).indices.forEach { refineBand(shot, it) }
            }
        }
    }

    /** No screenshot and no recognition: only the app's own text, for weak devices and e-ink readers. */
    private suspend fun readAppTextOnly(flashLines: Boolean) {
        val page = readAppText()
        bubbleView.loading = false
        if (page == null || page.paragraphs.isEmpty()) {
            showScanError(R.string.overlay_no_app_text)
            return
        }
        onPage(page = page, final = true, lensError = null, flashLines = flashLines)
    }

    private fun ocrOptions() = OcrOptions(settings.ocrEngines, deferWholeImage = settings.ocrSaving)

    private fun smallText() = ocrBoostMode(settings.smallText, settings.ocrEngines, settings.textSource)

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
        if (smallText() != SmallTextMode.ON_DEMAND || !ocrFinal) return
        val shot = screenshot ?: return
        bandTimer?.cancel()
        // Cancelled with the scan (resetScan), so the screenshot is still the scan's.
        bandTimer = scope.launch {
            delay(BAND_DELAY_MS)
            val bands = ScreenBands.of(shot.bitmap.width, shot.bitmap.height)
            refineBand(shot, ScreenBands.nearest(bands, y - shot.screen.top))
        }
    }

    /** Recognizes one band of [shot] again and adds the lines found where the page had no text. */
    private fun refineBand(shot: SharedScreenshot, index: Int) {
        if (!requestedBands.add(index) || !shot.retain()) return
        val screen = shot.screen
        val band = ScreenBands.of(screen.bitmap.width, screen.bitmap.height)[index]
        // Started at once, so the band gives the screenshot back even when it is cancelled before it runs.
        bandJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val found = try {
                // The crop is made and freed inside the block, which withContext waits for even when cancelled.
                withContext(Dispatchers.Default) {
                    val crop = Bitmap.createBitmap(
                        screen.bitmap,
                        band.left.toInt(),
                        band.top.toInt(),
                        band.width.toInt(),
                        band.height.toInt(),
                    )
                    try {
                        ocr.recognizeRegion(crop, language)
                    } finally {
                        // createBitmap returns the screenshot itself when the band is the whole of it.
                        if (crop !== screen.bitmap) crop.recycle()
                    }
                }
            } finally {
                shot.release()
            }
            val current = layout ?: return@launch
            val added = found?.offset(screen.left.toFloat(), screen.top + band.top) ?: return@launch
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
        return capturer.capture(cx.roundToInt(), cy.roundToInt()) { displayCapture ->
            setOverlaysAlpha(0f)
            delay(HIDE_FRAME_MS)
            try {
                displayCapture()
            } finally {
                setOverlaysAlpha(1f)
            }
        }
    }

    /** The popup too: a note's screenshot in app-text-only mode is taken while it is shown. */
    private fun setOverlaysAlpha(alpha: Float) {
        bubbleView.alpha = if (alpha == 0f) 0f else bubbleView.restingAlpha()
        layerView.alpha = alpha
        popup.alpha = alpha
    }

    /** A new text layout in screen coordinates: the OCR draft or the final result. */
    private fun onPage(
        page: OcrPage,
        final: Boolean,
        lensError: Throwable?,
        flashLines: Boolean,
    ) {
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
        // The final text has no word under the aim: the word shown from the draft stays, without the spinner and with ➕.
        // Its note takes the word and the sentence from the draft, as the note always holds the word the popup shows.
        if (hit == null && ocrFinal) refreshPopup()
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
            val cached = shownLookup?.takeIf { it.text == text && popup.isShowing }
            val results = cached?.results ?: lookupResults(text)
            val kanji = if (cached != null) cached.kanji else if (results.isEmpty()) characterEntry(text) else null
            // Nothing found: no popup, as with Yomitan's auto-hide. Only a missing dictionary is worth a message.
            if (results.isEmpty() && kanji == null && lookup.hasTermDictionaries(language)) {
                layerView.setWordBoxes(emptyList())
                shownLookup = null
                hapticWord = null
                popup.hide()
                popupNotes.onResultsHidden()
                return@launch
            }
            val matched = PageState.matchedLength(results, kanji)
            val boxes = layout.boxesFor(position, matched.coerceAtLeast(1))
            layerView.setWordBoxes(if (settings.highlightWord) boxes else emptyList())
            val anchor = Box.unionOf(boxes) ?: return@launch
            val message = if (results.isEmpty() && kanji == null) noResultsMessage() else null
            val view = LookupView(text, matched, results, message, start = position, layout = layout, kanji = kanji)
            shownLookup = view
            val word = results.firstOrNull()?.term?.let { it.expression to it.reading } ?: kanji?.let { it.character to "" }
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

    /** @param link [text] is a dictionary link's term, looked up whole rather than as text at the aim point. */
    private suspend fun lookupResults(
        text: String,
        link: Boolean = false,
        primaryReading: String? = null,
    ): List<LookupResult> = try {
        val started = System.currentTimeMillis()
        val results = if (link) {
            lookup.lookupQuery(text, language, primaryReading)
        } else {
            lookup.lookup(text, language, primaryReading = primaryReading)
        }
        results.also {
            Log.d(TAG, "Lookup found ${it.size} entries in ${System.currentTimeMillis() - started} ms")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lookup failed", e.redacted())
        emptyList()
    }

    /** The kanji entry shown when no word starts at [text]; null when there is none or the lookup failed. */
    private suspend fun characterEntry(text: String): KanjiResult? = try {
        lookup.characterEntry(text, language)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Kanji lookup failed", e.redacted())
        null
    }

    private suspend fun noResultsMessage(): String = service.getString(noResultsText(lookup.noTermDictionary(language)))

    /** Looks up a link target from inside the popup and shows it on top of the current view. */
    private fun lookupLink(query: String, primaryReading: String?) {
        val shown = shownLookup
        scope.launch {
            val results = lookupResults(query, link = true, primaryReading = primaryReading)
            val kanji = if (results.isEmpty()) characterEntry(query) else null
            val matched = PageState.matchedLength(results, kanji)
            val message = if (results.isEmpty() && kanji == null) noResultsMessage() else null
            val state = popupStateOffMain(LookupView(query, matched, results, message, kanji = kanji))
            // A lookup of another word replaced the popup meanwhile; the link belonged to the old one.
            if (shownLookup !== shown) return@launch
            popup.push(state)
            popupNotes.onResultsShown(results.firstOrNull()?.term?.let { it.expression to it.reading })
        }
    }

    /** A scan that ended without text: nothing more will come, so the popup shows no spinner. */
    private fun showScanError(message: Int) = showScanError(service.getString(message))

    private fun showScanError(message: String) {
        ocrFinal = true
        bubbleView.loading = false
        showMessage(message)
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

    /**
     * What a note takes from the scan, fixed when its ➕ is pressed: the sentence around the word shown then, and the
     * screenshot on request. ➕ waits for the scan's final text ([noteWaitsForText]).
     */
    private fun noteSource(): NoteSource {
        // The shown word's position belongs to the layout it was found in, which a later OCR result may have replaced.
        val shown = shownLookup?.takeIf { it.layout != null }
        val layout = if (shown != null) shown.layout else layout
        val position = if (shown != null) shown.start else hit
        val sentence = if (layout != null && position != null) {
            val (paragraph, index) = layout.paragraphText(position)
            val length = layout.textFrom(position, shown?.matched?.coerceAtLeast(1) ?: 1).length
            Sentence.extract(paragraph, index, length, language)
        } else {
            null
        }
        val scan = scanId
        val documentTitle = foregroundPackage?.let(::appLabel).orEmpty()
        return NoteSource { withScreenshot ->
            val shot = if (withScreenshot) notePicture(scan) else null
            val focus = if (layout != null && position != null && shot != null) cropFocus(layout, position, shot.screen) else null
            NoteContext(sentence, shot, focus, documentTitle)
        }
    }

    /**
     * The scan's screenshot, retained for a note that releases it; in app-text-only mode it is taken now. Null once the
     * scan [scan] was closed, as the crop editor must not open after it.
     */
    private suspend fun notePicture(scan: Int): SharedScreenshot? {
        if (scan != scanId) return null
        // Without recognition no screenshot is taken during the scan.
        if (screenshot == null && settings.textSource == TextSource.APP_TEXT_ONLY) {
            val captured = try {
                capture()
            } catch (e: CaptureException) {
                Log.w(TAG, "No screenshot for the note", e)
                null
            }
            // The scan may have closed meanwhile, or another note taken a screenshot.
            if (captured != null) {
                if (scan == scanId && screenshot == null) screenshot = SharedScreenshot(captured) else captured.bitmap.recycle()
            }
        }
        return screenshot?.takeIf { scan == scanId && it.retain() }
    }

    /** The initial crop frame: the word's paragraph, in screen coordinates. */
    private fun cropFocus(layout: TextLayout, position: TextPosition, screen: CapturedScreen): RectF? {
        val image = Box(
            screen.left.toFloat(),
            screen.top.toFloat(),
            (screen.left + screen.bitmap.width).toFloat(),
            (screen.top + screen.bitmap.height).toFloat(),
        )
        val lines = layout.readingParagraphs[position.paragraphIndex].lines.map { it.box }
        return CropFocus.of(lines, FOCUS_PADDING_DP * density, image)?.let { RectF(it.left, it.top, it.right, it.bottom) }
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
            if (styles !== pageStyles) {
                pageStyles = styles
                popup.page.setStyles(json.encodeToJsonElement(ListSerializer(DictionaryStyle.serializer()), styles))
            }
            val tagNotes = runCatching { lookup.tagNotes() }
                .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Loading tag descriptions failed", it) }
                .getOrDefault(emptyList())
            if (tagNotes !== pageTagNotes) {
                pageTagNotes = tagNotes
                popup.page.setTagNotes(json.encodeToJsonElement(ListSerializer(DictionaryTagNotes.serializer()), tagNotes))
            }
        }
    }

    /** [popupState] for large result sets: the OCR status is read here, the JSON is written on a worker thread. */
    private suspend fun popupStateOffMain(view: LookupView): String {
        val theme = pageTheme()
        val pending = scanJob?.isActive == true && !ocrFinal
        val engine = engineLabel()
        val hideSource = !settings.showSourceText
        val ocrError = ocrErrorText()
        val noteWait = noteWaitsForText(pending, aimedEngine())
        return withContext(Dispatchers.Default) {
            PageState.build(
                service, theme, view.text, view.matched, view.results, view.message, pending, engine, hideSource, ocrError,
                noteWait, view.kanji,
            )
        }
    }

    private fun engineLabel(): String = engineLabel(aimedEngine())

    /** Where the word under the aim came from: text added around app text keeps its own engine. */
    private fun aimedEngine(): OcrEngineType? {
        val paragraphEngine = hit?.let { position -> layout?.readingParagraphs?.getOrNull(position.paragraphIndex)?.engine }
        return paragraphEngine ?: ocrEngine
    }

    private fun engineLabel(ocrEngine: OcrEngineType?): String =
        when (engineLabelOf(ocrEngine, ocrFinal, settings.ocrEngines, ocrOffline)) {
            EngineLabel.NONE -> ""
            EngineLabel.LENS -> service.getString(R.string.overlay_engine_lens)
            EngineLabel.APP_TEXT -> service.getString(R.string.overlay_engine_app_text)
            EngineLabel.DRAFT -> service.getString(R.string.overlay_engine_draft)
            EngineLabel.DEVICE -> service.getString(R.string.overlay_engine_device_only)
            EngineLabel.DEVICE_OFFLINE -> service.getString(R.string.overlay_engine_offline)
            EngineLabel.DEVICE_LENS_UNAVAILABLE -> service.getString(R.string.overlay_engine_device)
        }

    private fun popupState(view: LookupView): String {
        val engineLabel = engineLabel()
        val pending = scanJob?.isActive == true && !ocrFinal
        return PageState.build(
            context = service,
            theme = pageTheme(),
            text = view.text,
            matched = view.matched,
            results = view.results,
            message = view.message,
            pending = pending,
            engine = engineLabel,
            hideSource = !settings.showSourceText,
            ocrError = ocrErrorText(),
            noteWait = noteWaitsForText(pending, aimedEngine()),
            kanji = view.kanji,
        )
    }

    /** Why cloud recognition failed for this scan, for the ⚠ next to the engine label; empty when it did not. */
    private fun ocrErrorText(): String {
        val error = lensError ?: return ""
        if (settings.ocrEngines == OcrEngines.DEVICE) return ""
        // A word read from the app's own text does not depend on recognition.
        if (aimedEngine() == OcrEngineType.ACCESSIBILITY) return ""
        return "${cloudErrorReason(error)} ${service.getString(R.string.overlay_ocr_error_fallback)}"
    }

    private fun cloudErrorReason(error: Throwable): String = when (error) {
        is OfflineException -> service.getString(R.string.overlay_ocr_error_offline)
        is LensPausedException -> service.getString(R.string.overlay_ocr_error_paused)
        is SocketTimeoutException -> service.getString(R.string.overlay_ocr_error_timeout)
        is LensHttpException -> when {
            error.code == HTTP_TOO_MANY_REQUESTS -> service.getString(R.string.overlay_ocr_error_too_many)
            error.code == HTTP_FORBIDDEN -> service.getString(R.string.overlay_ocr_error_refused)
            error.code >= HTTP_SERVER_ERROR -> service.getString(R.string.overlay_ocr_error_server, error.code)
            else -> service.getString(R.string.overlay_ocr_error_http, error.code)
        }
        is IOException -> service.getString(R.string.overlay_ocr_error_network)
        else -> service.getString(R.string.overlay_ocr_error_other)
    }

    private fun pageTheme(): PageTheme = when {
        eInk -> PageTheme.E_INK
        isDarkTheme() -> PageTheme.DARK
        else -> PageTheme.LIGHT
    }

    private fun isDarkTheme(): Boolean =
        themeMode.isDark(service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

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

        override fun onCopy(text: String, html: String?) = PageState.copy(service, text, html)

        override fun onKanji(character: String) {
            val shown = shownLookup
            scope.launch {
                val result = try {
                    lookup.kanji(character, language)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KanjiResult(character)
                }
                if (shownLookup !== shown) return@launch
                popup.push(PageState.kanji(service, pageTheme(), result, service.getString(R.string.overlay_no_kanji)))
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
        const val FOCUS_PADDING_DP = 48f
        const val BAND_DELAY_MS = 300L
    }

    /** The latest recognition result of a scan in screen coordinates; [page] is null when recognition failed. */
    private class Recognized(
        val page: OcrPage?,
        val final: Boolean,
        val lensError: Throwable?,
    )
}
