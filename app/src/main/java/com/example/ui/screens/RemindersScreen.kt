package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.SavedPlaceEntity
import com.example.ui.HomeListUiState
import com.example.ui.components.ReminderItemCard
import kotlin.math.roundToInt

private enum class ReminderFilter(val label: String) {
    ACTIVE("Active"),
    LOCATION("At a Place"),
    COMPLETED("Done")
}

@Composable
fun RemindersScreen(
    state: HomeListUiState,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenAddReminder: (triggerType: ReminderTriggerType, placeId: String?) -> Unit,
    onOpenAddPlace: () -> Unit,
    onToggleReminder: (ReminderEntity) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit,
    onDeletePlace: (SavedPlaceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(ReminderFilter.ACTIVE) }
    val selfId = state.config?.selfMemberId.orEmpty()
    val membersById = remember(state.members) { state.members.associateBy { it.id } }
    val placesById = remember(state.places) { state.places.associateBy { it.id } }

    val standaloneReminders = remember(state.reminders, state.isParent, selfId) {
        state.reminders.filter {
            ReminderTriggerType.fromString(it.triggerType) != ReminderTriggerType.NUDGE &&
                (state.isParent || it.assignedToMemberId == null || it.assignedToMemberId == selfId)
        }
    }

    val filtered = remember(standaloneReminders, selectedFilter) {
        when (selectedFilter) {
            ReminderFilter.ACTIVE -> standaloneReminders.filter { !it.isCompleted }
            ReminderFilter.LOCATION -> standaloneReminders.filter {
                !it.isCompleted && (
                    ReminderTriggerType.fromString(it.triggerType) == ReminderTriggerType.LOCATION ||
                        ReminderTriggerType.fromString(it.triggerType) == ReminderTriggerType.TIME_AND_LOCATION
                    )
            }
            ReminderFilter.COMPLETED -> standaloneReminders.filter { it.isCompleted }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Contextual Permission Banner if notifications or location aren't enabled yet
        if (!hasNotificationPermission || !hasLocationPermission) {
            item(key = "permissions_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enable Reminder Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Allow notifications for alerts and location for reminders when arriving at or leaving places like School or Grocery Store.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = onRequestNotificationPermission,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("grant_notifications_button")
                                ) {
                                    Icon(Icons.Outlined.NotificationsActive, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Allow Alerts")
                                }
                            }
                            if (!hasLocationPermission) {
                                OutlinedButton(
                                    onClick = onRequestLocationPermission,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("grant_location_button")
                                ) {
                                    Icon(Icons.Filled.MyLocation, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Allow Location")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Family Places Strip — tap any place to create a place reminder immediately
        item(key = "saved_places_section") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Family Places", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Tap a place to set an Arrive / Leave reminder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            TextButton(
                                onClick = onRefreshLocation,
                                modifier = Modifier.testTag("check_location_now_button")
                            ) {
                                Text("Check")
                            }
                            TextButton(
                                onClick = onOpenAddPlace,
                                modifier = Modifier.testTag("add_place_button")
                            ) {
                                Icon(Icons.Filled.AddLocationAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Place")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.places.forEach { place ->
                            val prox = state.placeProximity[place.id]
                            val hasCoords = place.latitude != 0.0 || place.longitude != 0.0
                            val statusLabel = when {
                                prox?.viaHomeWifi == true -> "Home Wi-Fi • Tap to remind"
                                prox?.isInside == true -> "Here now • Tap to remind"
                                prox?.distanceMeters != null -> "${prox.distanceMeters.roundToInt()}m away"
                                place.isHomePlace && !hasCoords -> "Triggers on Home Wi-Fi"
                                hasCoords -> "GPS ready • Tap to remind"
                                else -> "Tap to remind"
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (prox?.isInside == true) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onOpenAddReminder(ReminderTriggerType.LOCATION, place.id)
                                    }
                                    .testTag("place_chip_${place.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (place.isHomePlace) Icons.Outlined.Home else Icons.Outlined.LocationOn,
                                        contentDescription = place.name,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(place.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!place.isHomePlace && state.isParent) {
                                        TextButton(onClick = { onDeletePlace(place) }) {
                                            Text("×")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips + Add Reminder Button
        item(key = "reminder_filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("reminder_filter_${filter.name.lowercase()}")
                        )
                    }
                }
                TextButton(
                    onClick = {
                        val defaultType = if (selectedFilter == ReminderFilter.LOCATION) {
                            ReminderTriggerType.LOCATION
                        } else {
                            ReminderTriggerType.TIME
                        }
                        onOpenAddReminder(defaultType, null)
                    },
                    modifier = Modifier.testTag("quick_add_reminder_button")
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("New")
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty_reminders") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (selectedFilter == ReminderFilter.LOCATION) {
                                "No place reminders yet"
                            } else {
                                "No reminders here yet"
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = if (selectedFilter == ReminderFilter.LOCATION) {
                                "Example: \"Unpack lunchbox\" when arriving at Home, or \"Grab milk\" when arriving at Grocery Store."
                            } else {
                                "Set reminders for a specific time, when arriving at or leaving a place, or both."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val defaultType = if (selectedFilter == ReminderFilter.LOCATION) {
                                    ReminderTriggerType.LOCATION
                                } else {
                                    ReminderTriggerType.TIME
                                }
                                onOpenAddReminder(defaultType, null)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("empty_add_reminder_button")
                        ) {
                            Text(
                                if (selectedFilter == ReminderFilter.LOCATION) "New Place Reminder"
                                else "Create Reminder"
                            )
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { reminder ->
                ReminderItemCard(
                    reminder = reminder,
                    assignee = reminder.assignedToMemberId?.let { membersById[it] },
                    place = reminder.placeId?.let { placesById[it] },
                    onToggleComplete = { onToggleReminder(reminder) },
                    onDelete = { onDeleteReminder(reminder) }
                )
            }
        }
    }
}
