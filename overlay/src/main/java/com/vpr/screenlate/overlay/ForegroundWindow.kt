package com.vpr.screenlate.overlay

import android.view.accessibility.AccessibilityWindowInfo

/** Which accessibility window belongs to the app in the foreground. */
internal object ForegroundWindow {

    class Window<T>(
        val value: T,
        val type: Int,
        val focused: Boolean,
        val layer: Int,
        val pictureInPicture: Boolean,
    )

    /**
     * The focused application window; system UI, keyboards and overlays are not applications. While an accessibility
     * overlay holds focus (the bubble menu, also after Home), the topmost application window counts instead, leaving
     * out a picture-in-picture one, which floats over the app in use.
     */
    fun <T> pick(windows: List<Window<T>>): T? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        apps.firstOrNull { it.focused }?.let { return it.value }
        val overlayFocused = windows.any { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.focused }
        if (!overlayFocused) return null
        return apps.filterNot { it.pictureInPicture }.maxByOrNull { it.layer }?.value
    }
}
