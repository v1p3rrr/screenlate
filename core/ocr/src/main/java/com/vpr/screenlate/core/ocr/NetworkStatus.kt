package com.vpr.screenlate.core.ocr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Whether the app has a network with internet access. Android also answers "no network" when the system blocks
     * the app's traffic (per-app network switches, background data restrictions, Data Saver), so the log says why.
     */
    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) return true
        Log.i(TAG, "Offline: ${reason(manager, capabilities)}")
        return false
    }

    @Suppress("DEPRECATION") // NetworkInfo is the only place that says whether the app's traffic is blocked.
    private fun reason(manager: ConnectivityManager, capabilities: NetworkCapabilities?): String {
        val blocked = manager.activeNetworkInfo?.detailedState == NetworkInfo.DetailedState.BLOCKED
        val dataSaver = manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return when {
            blocked -> "the system blocks this app's traffic"
            capabilities == null && dataSaver -> "no network for this app, Data Saver is on"
            capabilities == null -> "no active network for this app"
            else -> "the active network has no internet access"
        }
    }

    private companion object {
        const val TAG = "NetworkStatus"
    }
}
