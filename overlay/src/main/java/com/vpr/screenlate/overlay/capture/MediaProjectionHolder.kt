package com.vpr.screenlate.overlay.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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

    suspend fun getOrCreate(context: Context): MediaProjection? = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            projection?.let { return@withLock it }
            val currentGeneration = generation
            val app = context.applicationContext
            val data = try {
                consent.request(CONSENT_TIMEOUT_MS) { id ->
                    app.startActivity(MediaProjectionConsentActivity.intent(app, id))
                }
            } catch (_: android.content.ActivityNotFoundException) {
                throw CaptureException.Failed(-1)
            } ?: return@withLock null
            if (generation != currentGeneration) return@withLock null

            try {
                if (!MediaProjectionService.start(app)) throw CaptureException.Failed(-1)
                if (generation != currentGeneration) return@withLock null
                val manager = app.getSystemService(MediaProjectionManager::class.java)
                val session = manager.getMediaProjection(android.app.Activity.RESULT_OK, data)
                    ?: throw CaptureException.Failed(-1)
                session.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (projection !== session) return
                        Log.i(TAG, "MediaProjection stopped by system")
                        projection = null
                        MediaProjectionService.stop(app)
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
        consent.cancel()
        val session = projection
        projection = null
        session?.stop()
        MediaProjectionService.stop(context)
    }
}
