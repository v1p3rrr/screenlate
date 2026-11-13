package com.vpr.screenlate.overlay.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Invisible activity that displays the system MediaProjection permission dialog.
 */
class MediaProjectionConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                MediaProjectionHolder.pendingConsent?.complete(resultCode to data)
            } else {
                MediaProjectionHolder.pendingConsent?.complete(null)
            }
            MediaProjectionHolder.pendingConsent = null
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (MediaProjectionHolder.pendingConsent?.isActive == true) {
            MediaProjectionHolder.pendingConsent?.complete(null)
            MediaProjectionHolder.pendingConsent = null
        }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
