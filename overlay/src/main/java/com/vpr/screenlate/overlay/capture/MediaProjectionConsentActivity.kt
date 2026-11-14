package com.vpr.screenlate.overlay.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Hosts the system screen-capture consent dialog. */
class MediaProjectionConsentActivity : Activity() {
    private val close: () -> Unit = {
        @Suppress("DEPRECATION")
        finishActivity(REQUEST_CODE)
        finish()
    }
    private val requestId: String?
        get() = intent.getStringExtra(EXTRA_REQUEST_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = requestId
        if (id == null || !MediaProjectionHolder.consent.attach(id, close)) {
            close()
            return
        }
        if (savedInstanceState == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            requestId?.let { MediaProjectionHolder.consent.complete(it, data.takeIf { resultCode == RESULT_OK }) }
            finish()
        }
    }

    override fun onDestroy() {
        requestId?.let { id ->
            MediaProjectionHolder.consent.detach(id, close)
            if (isFinishing) MediaProjectionHolder.consent.complete(id, null)
        }
        super.onDestroy()
    }

    internal companion object {
        private const val REQUEST_CODE = 1001
        private const val EXTRA_REQUEST_ID = "capture_request_id"

        fun intent(context: Context, requestId: String): Intent =
            Intent(context, MediaProjectionConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}
