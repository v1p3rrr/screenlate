package com.vpr.screenlate.overlay

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Helpers for checking and enabling [ScreenlateAccessibilityService]. */
object OverlayServiceStatus {

    private val _running = MutableStateFlow(false)

    /**
     * Whether the service is connected right now. The service and the rest of the app share one process, so a false
     * here while [isEnabled] is true means the process was killed and the system did not bind the service again:
     * the bubble is gone and only the user can bring it back, since writing the enabled services needs a system
     * permission.
     */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun isEnabled(context: Context): Boolean {
        val expected = component(context)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /**
     * Opens Screenlate's own page in the accessibility settings, which holds the switch that turns the service on and
     * off. Stock Android lets only system apps open that page, so most phones get the list of services instead, with
     * Screenlate's row highlighted where the settings app supports it.
     */
    fun openAccessibilitySettings(context: Context) {
        val key = component(context).flattenToString()
        val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, key)
        try {
            context.startActivity(details)
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "No accessibility details page", e)
            context.startActivity(accessibilitySettingsIntent().highlighting(key))
        } catch (e: SecurityException) {
            // The page needs OPEN_ACCESSIBILITY_DETAILS_SETTINGS, which only system apps and installers hold.
            Log.i(TAG, "Accessibility details page refused", e)
            context.startActivity(accessibilitySettingsIntent().highlighting(key))
        }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun component(context: Context) = ComponentName(context, ScreenlateAccessibilityService::class.java)

    /** The settings app's own extras that scroll to a preference and highlight it; other apps ignore them. */
    private fun Intent.highlighting(key: String): Intent = putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })

    /** The page of one accessibility service, which the platform has but does not list in the SDK. */
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    private const val TAG = "ScreenlateService"
}
