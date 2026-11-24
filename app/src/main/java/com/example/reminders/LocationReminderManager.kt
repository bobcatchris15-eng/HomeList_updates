package com.example.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.PlaceTransition
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.SavedPlaceEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume

data class PlaceProximityInfo(
    val placeId: String,
    val distanceMeters: Float? = null,
    val isInside: Boolean = false,
    val viaHomeWifi: Boolean = false
)

class LocationReminderManager(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geofencingClient = LocationServices.getGeofencingClient(appContext)

    private val previousInsideState = mutableMapOf<String, Boolean>()
    private val _proximityMap = MutableStateFlow<Map<String, PlaceProximityInfo>>(emptyMap())
    val proximityMap: StateFlow<Map<String, PlaceProximityInfo>> = _proximityMap.asStateFlow()

    fun hasForegroundLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        if (!hasForegroundLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            try {
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc.latitude to loc.longitude)
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last ->
                                    cont.resume(last?.let { it.latitude to it.longitude })
                                }
                                .addOnFailureListener { cont.resume(null) }
                        }
                    }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    /**
     * Checks current GPS location against saved family places, updates proximity chips,
     * and returns reminders whose Arrive/Leave (+ optional Time window) conditions just triggered.
     */
    suspend fun evaluateLocationReminders(
        places: List<SavedPlaceEntity>,
        reminders: List<ReminderEntity>,
        selfMemberId: String
    ): List<ReminderEntity> {
        val coords = getCurrentLocation() ?: return emptyList()
        val (lat, lon) = coords
        val now = System.currentTimeMillis()
        val triggered = mutableListOf<ReminderEntity>()
        val updatedMap = mutableMapOf<String, PlaceProximityInfo>()

        for (place in places) {
            if (place.isDeleted || (place.latitude == 0.0 && place.longitude == 0.0)) continue
            val results = FloatArray(1)
            Location.distanceBetween(lat, lon, place.latitude, place.longitude, results)
            val dist = results[0]
            val inside = dist <= place.radiusMeters
            val prev = previousInsideState[place.id]
            previousInsideState[place.id] = inside

            updatedMap[place.id] = PlaceProximityInfo(
                placeId = place.id,
                distanceMeters = dist,
                isInside = inside
            )

            val justArrived = inside && (prev == false || prev == null)
            val justLeft = !inside && prev == true

            val matching = reminders.filter { rem ->
                !rem.isDeleted &&
                    !rem.isCompleted &&
                    rem.placeId == place.id &&
                    (rem.assignedToMemberId == null || rem.assignedToMemberId == selfMemberId) &&
                    (now - rem.lastFiredAt) > COOLDOWN_MS
            }
            for (rem in matching) {
                val type = ReminderTriggerType.fromString(rem.triggerType)
                if (type != ReminderTriggerType.LOCATION && type != ReminderTriggerType.TIME_AND_LOCATION) {
                    continue
                }
                if (type == ReminderTriggerType.TIME_AND_LOCATION && !isWithinTimeWindow(rem)) {
                    continue
                }
                val trans = PlaceTransition.fromString(rem.placeTransition)
                val shouldFire = (trans == PlaceTransition.ARRIVE && justArrived) ||
                    (trans == PlaceTransition.LEAVE && justLeft)
                if (shouldFire) {
                    triggered.add(rem)
                    ReminderNotificationHelper.postNotification(
                        context = appContext,
                        notificationId = rem.id.hashCode(),
                        title = rem.title,
                        body = "${trans.label} ${place.name}" +
                            if (rem.notes.isNotBlank()) " • ${rem.notes}" else "",
                        channelId = ReminderNotificationHelper.CHANNEL_REMINDERS
                    )
                }
            }
        }
        _proximityMap.value = updatedMap
        return triggered
    }

    /**
     * Zero-GPS-battery Home arrival trigger when the phone reconnects to a HomeList Wi-Fi peer.
     */
    fun evaluateHomeWifiArrival(
        places: List<SavedPlaceEntity>,
        reminders: List<ReminderEntity>,
        selfMemberId: String
    ): List<ReminderEntity> {
        val homePlaces = places.filter { !it.isDeleted && it.isHomePlace }
        if (homePlaces.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val triggered = mutableListOf<ReminderEntity>()

        val currentMap = _proximityMap.value.toMutableMap()
        homePlaces.forEach { home ->
            currentMap[home.id] = PlaceProximityInfo(
                placeId = home.id,
                distanceMeters = currentMap[home.id]?.distanceMeters,
                isInside = true,
                viaHomeWifi = true
            )
            reminders.filter { rem ->
                !rem.isDeleted &&
                    !rem.isCompleted &&
                    rem.placeId == home.id &&
                    PlaceTransition.fromString(rem.placeTransition) == PlaceTransition.ARRIVE &&
                    (rem.assignedToMemberId == null || rem.assignedToMemberId == selfMemberId) &&
                    (now - rem.lastFiredAt) > COOLDOWN_MS
            }.forEach { rem ->
                val type = ReminderTriggerType.fromString(rem.triggerType)
                if (type == ReminderTriggerType.TIME_AND_LOCATION && !isWithinTimeWindow(rem)) {
                    return@forEach
                }
                triggered.add(rem)
                ReminderNotificationHelper.postNotification(
                    context = appContext,
                    notificationId = rem.id.hashCode(),
                    title = rem.title,
                    body = "Arrived at ${home.name} (Home Wi-Fi)" +
                        if (rem.notes.isNotBlank()) " • ${rem.notes}" else "",
                    channelId = ReminderNotificationHelper.CHANNEL_REMINDERS
                )
            }
        }
        _proximityMap.value = currentMap
        return triggered
    }

    fun syncGeofences(places: List<SavedPlaceEntity>, reminders: List<ReminderEntity>, selfMemberId: String) {
        if (!hasForegroundLocationPermission()) return
        val placesById = places.filter { !it.isDeleted && (it.latitude != 0.0 || it.longitude != 0.0) }
            .associateBy { it.id }

        val geofences = reminders.mapNotNull { rem ->
            val type = ReminderTriggerType.fromString(rem.triggerType)
            if (rem.isDeleted || rem.isCompleted) return@mapNotNull null
            if (type != ReminderTriggerType.LOCATION && type != ReminderTriggerType.TIME_AND_LOCATION) return@mapNotNull null
            if (rem.assignedToMemberId != null && rem.assignedToMemberId != selfMemberId) return@mapNotNull null
            val place = placesById[rem.placeId] ?: return@mapNotNull null
            val transFlag = if (PlaceTransition.fromString(rem.placeTransition) == PlaceTransition.LEAVE) {
                Geofence.GEOFENCE_TRANSITION_EXIT
            } else {
                Geofence.GEOFENCE_TRANSITION_ENTER
            }
            Geofence.Builder()
                .setRequestId("${rem.id}|${place.name.take(24)}|${rem.title.take(40)}")
                .setCircularRegion(place.latitude, place.longitude, place.radiusMeters.coerceIn(100, 1000).toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transFlag)
                .build()
        }.take(25)

        val pendingIntent = geofencePendingIntent()
        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            if (geofences.isEmpty()) return@addOnCompleteListener
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            try {
                geofencingClient.addGeofences(request, pendingIntent)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Geofence registration skipped (foreground only): ${e.message}")
                    }
            } catch (_: SecurityException) {
            }
        }
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, 9104, intent, flags)
    }

    private fun isWithinTimeWindow(reminder: ReminderEntity): Boolean {
        if (reminder.triggerHour !in 0..23) return true
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val targetMinutes = reminder.triggerHour * 60 + reminder.triggerMinute
        // Active from the configured time onward for that day
        return nowMinutes >= targetMinutes
    }

    companion object {
        private const val TAG = "LocationReminderMgr"
        private const val COOLDOWN_MS = 30 * 60_000L
    }
}
