package com.vpr.screenlate.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Opens the app when the Quick Settings tile is held; without an activity for that action the system opens the app's
 * page in the phone's settings instead. Brings the app to the front as the launcher does, then closes.
 */
class BubbleTileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.let { intent -> runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open the app", it) } }
        finish()
    }

    private companion object {
        const val TAG = "BubbleTile"
    }
}
