package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.overlay.fonts.PageAppearance
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

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

    private val scope = MainScope()
    private var controller: OverlayController? = null
    private var languageResources: AppLanguageResources? = null

    // The bubble, popup and crop editor use the app's language, which the system applies to activities only.
    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        val language = languageResources ?: AppLanguageResources(base).also { languageResources = it }
        return language.resources(super.getResources())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        controller = OverlayController(
            service = this,
            ocr = ocr,
            overlaySettings = overlaySettings,
            appSettings = appSettings,
            lookup = dictionaryLookup,
            pageAppearance = pageAppearance,
            anki = OverlayController.AnkiServices(ankiDroid, ankiNotes, audioFinder, audioSettings, audioPlayer),
            scope = scope,
        ).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Window focus moves shortly after the event; read it once things settle.
        handler.removeCallbacks(foregroundCheck)
        handler.postDelayed(foregroundCheck, FOREGROUND_CHECK_DELAY_MS)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val foregroundCheck = Runnable {
        focusedAppPackage()?.let { controller?.onForegroundApp(it) }
    }

    /** Package of the focused application window; system UI, keyboards and our overlays are not applications. */
    private fun focusedAppPackage(): String? = windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
        ?.root
        ?.packageName
        ?.toString()

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Log.i(TAG, "Service stopped")
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
