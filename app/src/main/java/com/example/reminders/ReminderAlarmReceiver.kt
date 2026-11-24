package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderNotificationHelper.EXTRA_TITLE) ?: "HomeList Reminder"
        val body = intent.getStringExtra(ReminderNotificationHelper.EXTRA_BODY) ?: "You have a scheduled task."
        val channel = intent.getStringExtra(ReminderNotificationHelper.EXTRA_CHANNEL)
            ?: ReminderNotificationHelper.CHANNEL_REMINDERS
        val id = intent.getIntExtra(ReminderNotificationHelper.EXTRA_NOTIFICATION_ID, title.hashCode())

        ReminderNotificationHelper.postNotification(
            context = context,
            notificationId = id,
            title = title,
            body = body,
            channelId = channel
        )
    }
}
