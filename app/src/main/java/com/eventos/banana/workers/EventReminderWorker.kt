package com.eventos.banana.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.app.NotificationManagerCompat
import com.eventos.banana.R

class EventReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val eventTitle = inputData.getString("eventTitle") ?: "Evento"
        val eventId = inputData.getString("eventId") ?: return androidx.work.ListenableWorker.Result.failure()

        sendNotification(eventTitle, eventId)
        return androidx.work.ListenableWorker.Result.success()
    }

    private fun sendNotification(title: String, eventId: String) {
        val context = applicationContext
        val channelId = "event_reminders"

        // Create Channel (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Eventos"
            val descriptionText = "Avisos 1 hora antes del evento"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create Intent (Open Details)
        // Note: Deep linking depends of configuration. 
        // For simplicity, we just launch the app main activity or pending intent.
        // Ideally: use NavDeepLinkBuilder
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Replace with app icon if available
            .setContentTitle("¡Tu evento comienza pronto!")
            .setContentText("En 1 hora comienza: $title. ¡Prepárate! 🍌")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            // Need permission check for Android 13+
            // Assuming permission is granted or dealt with in UI
             with(NotificationManagerCompat.from(context)) {
                notify(eventId.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
            e.printStackTrace()
        }
    }
}
