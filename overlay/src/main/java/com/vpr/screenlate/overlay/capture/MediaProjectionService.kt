package com.vpr.screenlate.overlay.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vpr.screenlate.overlay.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service required on Android 10 (API 29) to host MediaProjection.
 * Kept alive only while the screen capture session is active.
 */
class MediaProjectionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startedDeferred?.complete(Unit)
        startedDeferred = null
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        startedDeferred = null
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_capture_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_bubble)
            .setContentTitle(getString(R.string.overlay_capture_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screenlate_capture"
        private const val NOTIFICATION_ID = 101
        private var startedDeferred: CompletableDeferred<Unit>? = null

        suspend fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val deferred = CompletableDeferred<Unit>()
                startedDeferred = deferred
                val intent = Intent(context, MediaProjectionService::class.java)
                context.startForegroundService(intent)
                withTimeoutOrNull(2000L) { deferred.await() }
            } else {
                val intent = Intent(context, MediaProjectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            startedDeferred = null
            context.stopService(Intent(context, MediaProjectionService::class.java))
        }
    }
}
