package com.vpr.screenlate.overlay.ui

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
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

    fun statusBarHeight(context: Context, windowManager: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.maximumWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        }
        return resourceDimension(context.resources, "status_bar_height")
    }

    fun navigationBarHeight(context: Context, windowManager: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.maximumWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        }
        return resourceDimension(context.resources, "navigation_bar_height")
    }

    internal fun resourceDimension(resources: android.content.res.Resources?, name: String): Int {
        val resId = resources?.getIdentifier(name, "dimen", "android") ?: 0
        return if (resId > 0) resources?.getDimensionPixelSize(resId) ?: 0 else 0
    }
}
