package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.diagnostics.Diag
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.overlay.install.LanguageInstallation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the floating bubble and popup overlays.
 *
 * Accessibility is used because it allows overlay windows without SYSTEM_ALERT_WINDOW and
 * screenshots without a MediaProjection consent prompt.
 */
@AndroidEntryPoint
class ScreenlateAccessibilityService : AccessibilityService() {

    @Inject lateinit var ocr: CompositeOcr

    @Inject lateinit var overlaySettings: OverlaySettingsRepository

    @Inject lateinit var appSettings: AppSettingsRepository

    @Inject lateinit var dictionaryLookup: DictionaryLookup

    @Inject lateinit var ankiDroid: AnkiDroid

    @Inject lateinit var ankiNotes: AnkiNotes

    @Inject lateinit var audioFinder: AudioFinder

    @Inject lateinit var audioSettings: AudioSettingsRepository

    @Inject lateinit var audioPlayer: AudioPlayer

    @Inject lateinit var pageAppearance: PageAppearance

    @Inject lateinit var sentenceTranslation: SentenceTranslation

    @Inject lateinit var languageProfiles: LanguageProfiles
    @Inject lateinit var languageInstallation: LanguageInstallation

    private val scope = MainScope()
    private var controller: OverlayController? = null
    private var languageResources: AppLanguageResources? = null

    // The bubble, popup and crop editor use the app's language, which the system applies to activities only.
    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        val language = languageResources ?: AppLanguageResources(base).also { languageResources = it }
        return language.resources(super.getResources())
    }

    // TEMPORARY DIAGNOSTICS BUILD: the service's lifecycle (ai/notes/diagnostics.md).
    override fun onCreate() {
        super.onCreate()
        Diag.event("service", "state" to "created", "process_up_ms" to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        Diag.state("service", "created")
    }

    // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
    override fun onUnbind(intent: Intent?): Boolean {
        Diag.event("service", "state" to "unbind")
        Diag.state("service", "unbound")
        return super.onUnbind(intent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        Diag.event("service", "state" to "connected", "process_up_ms" to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        Diag.state("service", "connected")
        OverlayServiceStatus.setRunning(true)
        scope.launch {
            overlaySettings.settings.map { it.keepAlive }.distinctUntilChanged().collect {
                BubbleKeepAliveService.keepAlive(this@ScreenlateAccessibilityService, it)
            }
        }
        controller = OverlayController(
            service = this,
            ocr = ocr,
            overlaySettings = overlaySettings,
            appSettings = appSettings,
            lookup = dictionaryLookup,
            pageAppearance = pageAppearance,
            anki = OverlayController.AnkiServices(ankiDroid, ankiNotes, audioFinder, audioSettings, audioPlayer),
            translation = sentenceTranslation,
            profiles = languageProfiles,
            installation = languageInstallation,
            scope = scope,
        ).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        Diag.event(
            "service_config",
            "orientation" to newConfig.orientation,
            "w_dp" to newConfig.screenWidthDp,
            "h_dp" to newConfig.screenHeightDp,
            "night" to (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES),
        )
        controller?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            controller?.onWindowsChanged()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Window focus moves shortly after the event; read it once things settle.
        handler.removeCallbacks(foregroundCheck)
        handler.postDelayed(foregroundCheck, FOREGROUND_CHECK_DELAY_MS)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val foregroundCheck = Runnable {
        foregroundAppPackage()?.let { controller?.onForegroundApp(it) }
    }

    /** Package of the app in the foreground; see [ForegroundWindow.pick]. */
    private fun foregroundAppPackage(): String? = ForegroundWindow
        .pick(windows.map { ForegroundWindow.Window(it, it.type, it.isFocused, it.layer, it.isInPictureInPictureMode) })
        ?.root
        ?.packageName
        ?.toString()

    override fun onInterrupt() = Diag.event("service", "state" to "interrupt") // TEMPORARY DIAGNOSTICS BUILD

    override fun onDestroy() {
        Log.i(TAG, "Service stopped")
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        Diag.event("service", "state" to "destroyed", "process_up_ms" to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        Diag.state("service", "destroyed")
        OverlayServiceStatus.setRunning(false)
        BubbleKeepAliveService.keepAlive(this, false)
        handler.removeCallbacksAndMessages(null)
        controller?.stop()
        controller = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ScreenlateService"
        const val FOREGROUND_CHECK_DELAY_MS = 250L
    }
}
