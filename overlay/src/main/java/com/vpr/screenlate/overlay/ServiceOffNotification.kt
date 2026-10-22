package com.vpr.screenlate.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Tells the user that the accessibility service stopped and the bubble is gone; a tap opens the service's settings.
 * Shown a few seconds after a stop the service has not come back from ([StopFollowUp]), also one the user made, since
 * the app cannot tell who turned the service off. A banner without sound; cancelled when the service runs again.
 * Without the notification permission it is left out.
 */
internal object ServiceOffNotification {
    private const val CHANNEL_ID = "service_off"
    private const val NOTIFICATION_ID = 3

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** False when notifications are not allowed. */
    @SuppressLint("MissingPermission") // Checked by allowed(); lint does not follow the check into another function.
    fun show(context: Context): Boolean {
        if (!allowed(context)) return false
        val manager = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.overlay_service_off_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AccessibilitySettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.getString(R.string.overlay_service_off_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_bubble)
            .setContentTitle(context.getString(R.string.overlay_service_off_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the call.
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/**
 * Opens the service's accessibility settings from the notification: a notification cannot fall back to the general
 * accessibility page when the phone has no page for the single service, so this activity does it and closes.
 */
class AccessibilitySettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayServiceStatus.openAccessibilitySettings(this)
        finish()
    }
}
