package com.example.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChoreEntity
import com.example.data.DateStreakUtils
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import java.util.Calendar

object ReminderNotificationHelper {
    const val CHANNEL_REMINDERS = "homelist_reminders"
    const val CHANNEL_NUDGES = "homelist_nudges"

    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_CHANNEL = "extra_channel"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notification_channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_desc)
        }

        val nudgesChannel = NotificationChannel(
            CHANNEL_NUDGES,
            context.getString(R.string.notification_channel_nudges_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_nudges_desc)
        }

        manager.createNotificationChannel(remindersChannel)
        manager.createNotificationChannel(nudgesChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        channelId: String = CHANNEL_REMINDERS
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Ignored when runtime permission was revoked
        }
    }

    fun scheduleTimeReminder(context: Context, reminder: ReminderEntity) {
        if (reminder.isDeleted || reminder.isCompleted) {
            cancelScheduled(context, reminder.id.hashCode())
            return
        }
        val triggerType = ReminderTriggerType.fromString(reminder.triggerType)
        if (triggerType != ReminderTriggerType.TIME && triggerType != ReminderTriggerType.TIME_AND_LOCATION) {
            return
        }
        if (reminder.triggerHour !in 0..23) return

        val nextMillis = computeNextTriggerMillis(
            dateKey = reminder.triggerDateKey,
            repeatDaysCsv = reminder.repeatDaysCsv,
            hour = reminder.triggerHour,
            minute = reminder.triggerMinute
        ) ?: return

        val body = reminder.notes.ifBlank {
            if (triggerType == ReminderTriggerType.TIME_AND_LOCATION) {
                "Time + Location reminder ready"
            } else {
                "Scheduled HomeList reminder"
            }
        }
        scheduleAlarm(
            context = context,
            requestCode = reminder.id.hashCode(),
            triggerAtMillis = nextMillis,
            title = reminder.title,
            body = body,
            channelId = CHANNEL_REMINDERS
        )
    }

    fun scheduleChoreReminder(context: Context, chore: ChoreEntity) {
        val code = ("chore_${chore.id}").hashCode()
        if (chore.isDeleted || !chore.remindersEnabled || chore.reminderHour !in 0..23) {
            cancelScheduled(context, code)
            return
        }
        val nextMillis = computeNextTriggerMillis(
            dateKey = "",
            repeatDaysCsv = chore.daysOfWeekCsv,
            hour = chore.reminderHour,
            minute = chore.reminderMinute
        ) ?: return

        scheduleAlarm(
            context = context,
            requestCode = code,
            triggerAtMillis = nextMillis,
            title = "Chore reminder: ${chore.title}",
            body = chore.notes.ifBlank { "Tap to check it off and keep your streak going!" },
            channelId = CHANNEL_REMINDERS
        )
    }

    fun cancelScheduled(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        title: String,
        body: String,
        channelId: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_CHANNEL, channelId)
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Uses Play-compliant inexact wake alarm (no SCHEDULE_EXACT_ALARM needed)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun computeNextTriggerMillis(
        dateKey: String,
        repeatDaysCsv: String,
        hour: Int,
        minute: Int
    ): Long? {
        val now = System.currentTimeMillis()
        val repeatDays = repeatDaysCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

        if (repeatDays.isEmpty() && dateKey.isNotBlank()) {
            val cal = DateStreakUtils.parseDateKey(dateKey) ?: return null
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            return if (cal.timeInMillis > now) cal.timeInMillis else null
        }

        val cal = Calendar.getInstance()
        for (dayOffset in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val iso = DateStreakUtils.calendarToIsoDay(candidate.get(Calendar.DAY_OF_WEEK))
            if (candidate.timeInMillis > now && (repeatDays.isEmpty() || iso in repeatDays)) {
                return candidate.timeInMillis
            }
        }
        return null
    }
}
