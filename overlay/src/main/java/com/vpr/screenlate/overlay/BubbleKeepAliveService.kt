package com.vpr.screenlate.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Holds a quiet foreground notification for as long as the "keep the service running" setting is on.
 *
 * Phones that stop background apps on their own leave an app with such a notification alone far more often, which is
 * as much as an app can do: the system alone decides whether a stopped accessibility service is bound again.
 */
class BubbleKeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Android 14 wants a type with every foreground service; older versions have neither the type nor the need.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (e: RuntimeException) {
            // The system refuses a foreground service in some states; the bubble works without it.
            Log.w(TAG, "Could not keep the service in the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!OverlayServiceStatus.isEnabled(this)) {
            // The system restarted this service after the process was killed, and the accessibility switch has been
            // turned off since. Stopped only after startForeground, which a foreground start must always reach.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.overlay_keep_alive_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_bubble)
            .setContentTitle(getString(R.string.overlay_keep_alive_title))
            .setContentText(getString(R.string.overlay_keep_alive_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "ScreenlateKeepAlive"
        private const val CHANNEL_ID = "bubble_keep_alive"
        private const val NOTIFICATION_ID = 2

        /** Starts or stops the notification; a start the system refuses is logged and left alone. */
        fun keepAlive(context: Context, enabled: Boolean) {
            val intent = Intent(context, BubbleKeepAliveService::class.java)
            if (!enabled) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not start the keep-alive service", e)
            }
        }
    }
}
