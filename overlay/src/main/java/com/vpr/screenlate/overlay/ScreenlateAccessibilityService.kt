package com.vpr.screenlate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.ocr.CompositeOcr
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

    private val scope = MainScope()
    private var controller: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller = OverlayController(this, ocr, overlaySettings, appSettings, scope).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controller?.stop()
        controller = null
        scope.cancel()
        super.onDestroy()
    }
}
