package com.vpr.screenlate.overlay.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Screen dimensions and insets compatibility helper for API 24–37.
 */
object WindowMetricsCompat {
    fun screenBounds(windowManager: WindowManager): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.maximumWindowMetrics.bounds
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    fun systemBarInsets(context: Context, windowManager: WindowManager): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.maximumWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(insets.left, insets.top, insets.right, insets.bottom)
        }
        val resources = context.resources
        val top = resourceDimension(resources, "status_bar_height")
        val viewConfig = runCatching { ViewConfiguration.get(context) }.getOrNull()
        val hasHardwareKeys = viewConfig?.hasPermanentMenuKey() == true ||
            (KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK) && KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME))
        if (hasHardwareKeys) {
            return Rect(0, top, 0, 0)
        }
        val config = resources.configuration
        val isLandscape = config?.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isTablet = (config?.smallestScreenWidthDp ?: 0) >= 600
        return if (isLandscape && !isTablet) {
            val navWidth = resourceDimension(resources, "navigation_bar_width")
                .takeIf { it > 0 } ?: resourceDimension(resources, "navigation_bar_height")
            @Suppress("DEPRECATION")
            val rotation = runCatching { windowManager.defaultDisplay.rotation }.getOrDefault(android.view.Surface.ROTATION_0)
            if (rotation == android.view.Surface.ROTATION_270) {
                Rect(navWidth, top, 0, 0)
            } else {
                Rect(0, top, navWidth, 0)
            }
        } else {
            val navHeight = resourceDimension(resources, "navigation_bar_height")
            Rect(0, top, 0, navHeight)
        }
    }

    fun statusBarHeight(context: Context, windowManager: WindowManager): Int =
        systemBarInsets(context, windowManager).top

    fun navigationBarHeight(context: Context, windowManager: WindowManager): Int =
        systemBarInsets(context, windowManager).bottom

    internal fun resourceDimension(resources: Resources?, name: String): Int {
        val resId = resources?.getIdentifier(name, "dimen", "android") ?: 0
        return if (resId > 0) resources?.getDimensionPixelSize(resId) ?: 0 else 0
    }
}
