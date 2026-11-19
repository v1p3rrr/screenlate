package com.vpr.screenlate.overlay.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

/** Hosts the system screen-capture consent and audio permission dialogs. */
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
            checkPermissionsAndRequest()
        }
    }

    private fun checkPermissionsAndRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: run {
            close()
            return
        }
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            @Suppress("DEPRECATION")
            manager.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestMediaProjection()
            } else {
                requestId?.let { MediaProjectionHolder.consent.complete(it, null) }
                finish()
            }
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
        private const val PERMISSION_REQUEST_CODE = 1002
        private const val EXTRA_REQUEST_ID = "capture_request_id"

        fun intent(context: Context, requestId: String): Intent =
            Intent(context, MediaProjectionConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}
