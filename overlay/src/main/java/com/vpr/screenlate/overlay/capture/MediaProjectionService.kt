package com.vpr.screenlate.overlay.capture

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
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import com.vpr.screenlate.overlay.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service required on Android 10 (API 29) to host MediaProjection.
 * Kept alive while the screen capture or sentence audio recording session is active.
 */
class MediaProjectionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RECORDING) {
            stopRecordingAction?.invoke()
            return START_NOT_STICKY
        }
        val isRecording = intent?.getBooleanExtra(EXTRA_IS_RECORDING, recordingActive) ?: recordingActive
        recordingActive = isRecording
        val notification = createNotification(isRecording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val start = startedDeferred
        if (start == null && !isRecording && !MediaProjectionHolder.hasActiveProjection) {
            stopSelf(startId)
        } else {
            start?.complete(true)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(isRecording: Boolean): Notification {
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
        val titleRes = if (isRecording) {
            R.string.overlay_recording_notification_title
        } else {
            R.string.overlay_capture_notification_title
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_bubble)
            .setContentTitle(getString(titleRes))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)

        if (isRecording) {
            val stopIntent = Intent(this, MediaProjectionService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingStop = PendingIntent.getService(this, REQUEST_STOP, stopIntent, pendingFlags)
            builder.addAction(
                R.drawable.ic_tile_bubble,
                getString(R.string.overlay_recording_notification_stop),
                pendingStop,
            )
        }

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "screenlate_capture"
        private const val NOTIFICATION_ID = 101
        private const val REQUEST_STOP = 102
        private const val START_TIMEOUT_MS = 5000L
        const val ACTION_STOP_RECORDING = "com.vpr.screenlate.action.STOP_RECORDING"
        const val EXTRA_IS_RECORDING = "is_recording"

        private var startedDeferred: CompletableDeferred<Boolean>? = null
        private var recordingActive = false
        var stopRecordingAction: (() -> Unit)? = null

        @MainThread
        suspend fun start(context: Context, isRecording: Boolean = false): Boolean {
            recordingActive = isRecording
            val deferred = CompletableDeferred<Boolean>()
            startedDeferred = deferred
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                putExtra(EXTRA_IS_RECORDING, isRecording)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                withTimeoutOrNull(START_TIMEOUT_MS) { deferred.await() } == true
            } finally {
                if (startedDeferred === deferred) startedDeferred = null
            }
        }

        @MainThread
        fun updateRecordingState(context: Context, isRecording: Boolean) {
            recordingActive = isRecording
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                putExtra(EXTRA_IS_RECORDING, isRecording)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @MainThread
        fun stop(context: Context) {
            recordingActive = false
            startedDeferred?.complete(false)
            startedDeferred = null
            context.stopService(Intent(context, MediaProjectionService::class.java))
        }
    }
}
