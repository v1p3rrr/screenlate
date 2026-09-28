package com.vpr.screenlate.background

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/** The system settings that decide whether the bubble keeps running in the background. */
internal object BackgroundSettings {
    /** Whether the system leaves the app out of battery optimization, so it does not stop the bubble to save power. */
    fun isBatteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != false

    /** The system's own dialog that asks to leave the app out of battery optimization; the user decides there. */
    @SuppressLint("BatteryLife") // The bubble must keep running in the background, which is what this exemption is for.
    fun batteryDialog(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    /** The list of apps and their battery optimization. */
    fun batteryList(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** The phone maker's startup settings, or null when the phone has none of the known ones. */
    fun startupScreen(context: Context): Intent? =
        StartupScreens.find { opens(context.packageManager, it) }
            ?.let { Intent().setComponent(ComponentName(it.packageName, it.className)) }

    /** Opens the first of [intents] the system accepts; a maker may remove or rename a screen. */
    fun open(context: Context, vararg intents: Intent?) {
        for (intent in intents.filterNotNull()) {
            val opened = runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "Could not open ${intent.component?.className ?: intent.action}: ${it.javaClass.simpleName}") }
                .isSuccess
            if (opened) return
        }
    }

    @Suppress("DEPRECATION") // The flags overload is API 33+.
    private fun opens(packageManager: PackageManager, screen: StartupScreen): Boolean =
        runCatching { packageManager.getActivityInfo(ComponentName(screen.packageName, screen.className), 0) }
            .getOrNull()?.let { it.exported && it.enabled } == true

    private const val TAG = "BackgroundSettings"
}
