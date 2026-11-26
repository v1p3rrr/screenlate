package com.vpr.screenlate.overlay.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Minimal real accessibility host for the isolated legacy screen-capture fixture. */
class LegacyCaptureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var active: LegacyCaptureAccessibilityService? = null
            private set
    }
}
