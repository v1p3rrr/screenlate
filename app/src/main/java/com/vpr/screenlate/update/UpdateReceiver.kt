package com.vpr.screenlate.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives the status of an update's install session. */
@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: AppUpdates

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        updates.onInstallStatus(status, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
    }

    companion object {
        const val ACTION = "com.vpr.screenlate.UPDATE_INSTALL_STATUS"
    }
}
