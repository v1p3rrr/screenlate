package com.vpr.screenlate.core.common.theme

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Resolves the platform palette's mid-tone primary for UI outside Compose. */
fun AppPaletteConfig.withWallpaperColor(context: Context): AppPaletteConfig =
    withWallpaperColor(context.wallpaperAccent())

/** Resolves the palette config with the user's wallpaper accent color, or keeps the existing config. */
fun AppPaletteConfig.withWallpaperColor(accent: Int?): AppPaletteConfig =
    if (useSystemWallpaper && accent != null) copy(preset = null, customSeedColor = accent) else this

/** The system reports updated color resources as a configuration change. */
fun wallpaperAccents(context: Context): Flow<Int?> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return flowOf(null)
    val app = context.applicationContext
    return callbackFlow {
        val callback = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                trySend(app.wallpaperAccent())
            }

            @Deprecated("Deprecated in Android")
            override fun onLowMemory() = Unit
        }
        app.registerComponentCallbacks(callback)
        trySend(app.wallpaperAccent())
        awaitClose { app.unregisterComponentCallbacks(callback) }
    }.distinctUntilChanged()
}

private fun Context.wallpaperAccent(): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getColor(android.R.color.system_accent1_500) else null
