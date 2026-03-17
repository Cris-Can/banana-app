package com.eventos.banana.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eventos.banana.R

/**
 * Centralized notification helper.
 * Manages channel creation, constants, and notification dispatch.
 */
object NotificationHelper {

    // ─── Channel IDs ───────────────────────────────
    const val CHANNEL_GENERAL = "banana_channel_01"
    const val CHANNEL_REMINDERS = "banana_reminders"
    const val CHANNEL_MESSAGES = "banana_messages"

    /**
     * Creates all notification channels. Safe to call multiple times.
     * Should be called once from Application.onCreate().
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🔔 General
        val general = NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.notif_channel_general),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_general_desc)
        }

        // ⏰ Reminders
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
        }

        // 💬 Messages
        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_messages_desc)
        }

        manager.createNotificationChannels(listOf(general, reminders, messages))
    }

    /**
     * Send a simple local notification.
     */
    fun sendLocalNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        icon: Int = android.R.drawable.ic_lock_idle_alarm,
        autoCancel: Boolean = true
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(autoCancel)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Permission denied for notification", e)
        }
    }
}
