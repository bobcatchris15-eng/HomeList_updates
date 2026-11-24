package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) return

        val transition = geofencingEvent.geofenceTransition
        val label = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Arrived at"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Left"
            else -> return
        }
        val triggered = geofencingEvent.triggeringGeofences.orEmpty()
        for (gf in triggered) {
            val parts = gf.requestId.split("|")
            val placeName = parts.getOrNull(1)?.ifBlank { "saved place" } ?: "saved place"
            val reminderTitle = parts.getOrNull(2)?.ifBlank { "Location reminder" } ?: "Location reminder"
            ReminderNotificationHelper.postNotification(
                context = context,
                notificationId = gf.requestId.hashCode(),
                title = reminderTitle,
                body = "$label $placeName",
                channelId = ReminderNotificationHelper.CHANNEL_REMINDERS
            )
        }
    }
}
