package com.vpr.screenlate.overlay.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.vpr.screenlate.core.common.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the lifetime of [MediaProjection] for screen capture on Android 7.0–10 (API 24–29).
 */
object MediaProjectionHolder {
    private const val TAG = "MediaProjection"
    private val mutex = Mutex()
    private var projection: MediaProjection? = null
    private var pendingConsent: CompletableDeferred<Pair<Int, Intent>?>? = null

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val deferred = pendingConsent
        pendingConsent = null
        if (resultCode == Activity.RESULT_OK && data != null) {
            deferred?.complete(resultCode to data)
        } else {
            deferred?.complete(null)
        }
    }

    fun onConsentCancelled() {
        val deferred = pendingConsent
        if (deferred?.isActive == true) {
            deferred.complete(null)
            pendingConsent = null
        }
    }

    /**
     * Obtains or reuses an active [MediaProjection] token.
     * Launches [MediaProjectionConsentActivity] if consent has not been granted.
     */
    suspend fun getOrCreate(context: Context): MediaProjection? = mutex.withLock {
        projection?.let { return it }

        Log.i(TAG, "Requesting MediaProjection consent via MediaProjectionConsentActivity")
        val result = requestConsent(context)
        Log.i(TAG, "MediaProjection consent result: $result")
        if (result == null) return null
        val (resultCode, data) = result

        // On API 29, a mediaProjection foreground service MUST be active before calling getMediaProjection.
        MediaProjectionService.start(context)

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = manager.getMediaProjection(resultCode, data) ?: return null

        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    projection = null
                    MediaProjectionService.stop(context)
                }
            },
            Handler(Looper.getMainLooper()),
        )
        projection = mp
        mp
    }

    private suspend fun requestConsent(context: Context): Pair<Int, Intent>? {
        val deferred = CompletableDeferred<Pair<Int, Intent>?>()
        pendingConsent = deferred
        val intent = Intent(context, MediaProjectionConsentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return deferred.await()
    }

    fun stop(context: Context) {
        projection?.stop()
        projection = null
        MediaProjectionService.stop(context)
    }
}
