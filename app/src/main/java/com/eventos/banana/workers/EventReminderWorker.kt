package com.eventos.banana.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.eventos.banana.R
import com.eventos.banana.util.NotificationHelper

class EventReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val eventTitle = inputData.getString("eventTitle")
            ?: applicationContext.getString(R.string.reminder_fallback_title)
        val eventId = inputData.getString("eventId")
            ?: return androidx.work.ListenableWorker.Result.failure()

        NotificationHelper.sendLocalNotification(
            context = applicationContext,
            channelId = NotificationHelper.CHANNEL_REMINDERS,
            notificationId = eventId.hashCode(),
            title = applicationContext.getString(R.string.reminder_title),
            body = applicationContext.getString(R.string.reminder_body, eventTitle)
        )

        return androidx.work.ListenableWorker.Result.success()
    }
}
