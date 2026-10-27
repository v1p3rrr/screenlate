package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocuments
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

    @Inject lateinit var lookupSources: LookupSources
    @Inject lateinit var photos: PhotoDocuments

    private val scope = MainScope()
    private var controller: OverlayController? = null
    private var languageResources: AppLanguageResources? = null
    private val systemPackages = HashMap<String, Boolean>()
    private val stopDiagnostics by lazy { ServiceStopDiagnostics(packageName, ::isSystemPackage) }

    // The bubble, popup and crop editor use the app's language, which the system applies to activities only.
    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        val language = languageResources ?: AppLanguageResources(base).also { languageResources = it }
        return language.resources(super.getResources())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        OverlayServiceStatus.setRunning(true)
        ServiceOffNotification.cancel(this)
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
            lookupSources = lookupSources,
            photos = photos,
            stopDiagnostics = stopDiagnostics,
            scope = scope,
        ).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            noteWindowsChange(event)
            controller?.onWindowsChanged()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        stopDiagnostics.windowStateChanged(
            SystemClock.elapsedRealtime(),
            event.packageName?.toString(),
            event.className?.toString(),
        )
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

    /** Windows that open and close are kept for the log of a stop; moved and resized ones are not. */
    private fun noteWindowsChange(event: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()
        val changes = event.windowChanges
        if (changes and AccessibilityEvent.WINDOWS_CHANGE_ADDED != 0) {
            val window = windows.firstOrNull { it.id == event.windowId }
            stopDiagnostics.windowAdded(now, event.windowId, window?.type ?: -1, window?.root?.packageName?.toString())
        }
        if (changes and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) stopDiagnostics.windowRemoved(now, event.windowId)
    }

    private fun isSystemPackage(name: String): Boolean = systemPackages.getOrPut(name) {
        try {
            val flags = packageManager.getApplicationInfo(name, 0).flags
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            // Apps with a launcher icon are visible through the app's <queries>; most hidden ones are the system's own.
            hasSystemPackagePrefix(name)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // Still on the list: the system unbound the service; off it: someone turned the switch off.
        Log.i(TAG, stopDiagnostics.report(SystemClock.elapsedRealtime(), OverlayServiceStatus.isEnabled(this)))
        val connectionsAtStop = OverlayServiceStatus.connections
        // The service itself, not the application context: its resources keep the app's language for the text.
        val service = this
        Handler(Looper.getMainLooper()).postDelayed(
            {
                val enabled = if (OverlayServiceStatus.isEnabled(service)) "yes" else "no"
                val running = if (OverlayServiceStatus.running.value) "yes" else "no"
                Log.i(TAG, "${RECHECK_MS / 1000} s after the stop: on the enabled list: $enabled, running: $running")
                val followUp = StopFollowUp.after(
                    connectionsAtStop,
                    OverlayServiceStatus.connections,
                    ServiceOffNotification.allowed(service),
                )
                when (followUp) {
                    StopFollowUp.NOTIFY -> if (!ServiceOffNotification.show(service)) logNoNotification()
                    StopFollowUp.LOG_ONLY -> logNoNotification()
                    StopFollowUp.BACK -> Unit
                }
            },
            RECHECK_MS,
        )
        OverlayServiceStatus.setRunning(false)
        BubbleKeepAliveService.keepAlive(this, false)
        handler.removeCallbacksAndMessages(null)
        controller?.stop()
        controller = null
        scope.cancel()
        super.onDestroy()
    }

    private fun logNoNotification() = Log.i(TAG, "No notification about the stop: notifications are off")

    private companion object {
        const val TAG = "ScreenlateService"
        const val FOREGROUND_CHECK_DELAY_MS = 250L
        const val RECHECK_MS = 3_000L
    }
}
