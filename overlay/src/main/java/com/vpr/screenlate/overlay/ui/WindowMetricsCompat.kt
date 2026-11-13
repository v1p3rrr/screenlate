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
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    fun navigationBarHeight(context: Context, windowManager: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.maximumWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        }
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
}
