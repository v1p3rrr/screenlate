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
    private val request: CapturePermissionRequest
        get() = CapturePermissionRequest(
            captureAudio = intent.getBooleanExtra(EXTRA_CAPTURE_AUDIO, false),
            requestProjection = intent.getBooleanExtra(EXTRA_REQUEST_PROJECTION, true),
        )

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
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when (request.nextStep(Build.VERSION.SDK_INT, granted)) {
            CapturePermissionStep.AUDIO_PERMISSION -> requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            CapturePermissionStep.PROJECTION_CONSENT -> requestMediaProjection()
            CapturePermissionStep.COMPLETE -> {
                requestId?.let { MediaProjectionHolder.consent.complete(it, Intent()) }
                finish()
            }
            CapturePermissionStep.UNSUPPORTED -> {
                requestId?.let { MediaProjectionHolder.consent.complete(it, null) }
                finish()
            }
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
                checkPermissionsAndRequest()
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
        private const val EXTRA_CAPTURE_AUDIO = "capture_audio"
        private const val EXTRA_REQUEST_PROJECTION = "request_projection"

        fun intent(context: Context, requestId: String, request: CapturePermissionRequest = CapturePermissionRequest()): Intent =
            Intent(context, MediaProjectionConsentActivity::class.java)
                // A cancelled host may still be closing when the next request starts. Never reuse its task.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_CAPTURE_AUDIO, request.captureAudio)
                .putExtra(EXTRA_REQUEST_PROJECTION, request.requestProjection)
    }
}
