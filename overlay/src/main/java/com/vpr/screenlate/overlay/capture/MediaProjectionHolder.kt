package com.vpr.screenlate.overlay.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.vpr.screenlate.core.common.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Manages the lifetime of [MediaProjection] on the main thread. */
internal object MediaProjectionHolder {
    private const val TAG = "MediaProjection"
    private const val CONSENT_TIMEOUT_MS = 30_000L
    private val mutex = Mutex()
    val consent = CaptureConsent<Intent>()
    private var projection: MediaProjection? = null
    private var generation = 0L

    val hasActiveProjection: Boolean get() = projection != null

    var onSystemStopped: (() -> Unit)? = null

    suspend fun getOrCreate(context: Context, isRecording: Boolean = false): MediaProjection? = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val currentGeneration = generation
            val app = context.applicationContext
            val existing = projection
            val request = CapturePermissionRequest(captureAudio = isRecording, requestProjection = existing == null)
            val audioGranted = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val step = request.nextStep(Build.VERSION.SDK_INT, audioGranted)
            if (step == CapturePermissionStep.UNSUPPORTED) return@withLock null
            val data = if (step != CapturePermissionStep.COMPLETE) {
                try {
                    consent.request(CONSENT_TIMEOUT_MS) { id ->
                        app.startActivity(MediaProjectionConsentActivity.intent(app, id, request))
                    }
                } catch (_: android.content.ActivityNotFoundException) {
                    throw CaptureException.Failed(-1)
                } ?: return@withLock null
            } else null
            if (generation != currentGeneration) return@withLock null

            // A permission-only result is not a projection token. Reuse only the still-live session.
            if (existing != null) {
                if (projection !== existing) return@withLock null
                if (isRecording) MediaProjectionService.updateRecordingState(app, isRecording = true)
                return@withLock existing
            }
            if (data == null) return@withLock null
            try {
                if (!MediaProjectionService.start(app, isRecording)) throw CaptureException.Failed(-1)
                if (generation != currentGeneration) return@withLock null
                val manager = app.getSystemService(MediaProjectionManager::class.java)
                val session = manager.getMediaProjection(android.app.Activity.RESULT_OK, data)
                    ?: throw CaptureException.Failed(-1)
                session.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (projection !== session) return
                        Log.i(TAG, "MediaProjection stopped by system")
                        generation++
                        projection = null
                        MediaProjectionService.stop(app)
                        consent.cancel()
                        onSystemStopped?.invoke()
                    }
                }, Handler(Looper.getMainLooper()))
                projection = session
                session
            } catch (_: SecurityException) {
                throw CaptureException.Failed(-1)
            } catch (_: IllegalStateException) {
                throw CaptureException.Failed(-1)
            } finally {
                if (projection == null) MediaProjectionService.stop(app)
            }
        }
    }

    @MainThread
    fun stop(context: Context) {
        generation++
        val session = projection
        projection = null
        try {
            session?.stop()
        } finally {
            MediaProjectionService.stop(context)
            consent.cancel()
        }
    }
}
