package com.vpr.screenlate.overlay

import android.app.Activity
import android.os.Bundle

/**
 * Opens the app when the Quick Settings tile is held; without an activity for that action the system opens the app's
 * page in the phone's settings instead. Brings the app to the front as the launcher does, then closes.
 */
class BubbleTileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayIntents.openApp(this)
        finish()
    }
}
