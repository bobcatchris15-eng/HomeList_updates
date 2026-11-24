package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CalendarEventEntity
import com.example.data.CalendarSource
import com.example.data.ChoreCategory
import com.example.data.ChoreEntity
import com.example.data.DateStreakUtils
import com.example.data.MemberEntity
import com.example.data.MemberRole
import com.example.data.PlaceTransition
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.RotationCadence
import com.example.data.SavedPlaceEntity
import com.example.data.TimeOfDay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreEditorSheet(
    editingChore: ChoreEntity?,
    members: List<MemberEntity>,
    onDismiss: () -> Unit,
    onSave: (ChoreEntity) -> Unit,
    onDelete: ((ChoreEntity) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(editingChore) { mutableStateOf(editingChore?.title.orEmpty()) }
    var notes by remember(editingChore) { mutableStateOf(editingChore?.notes.orEmpty()) }
    var category by remember(editingChore) {
        mutableStateOf(ChoreCategory.fromString(editingChore?.category))
    }
    var assignedId by remember(editingChore) { mutableStateOf(editingChore?.assignedToMemberId) }
    var useRotation by remember(editingChore) { mutableStateOf(editingChore?.isRotating() == true) }
    var rotationCadence by remember(editingChore) {
        mutableStateOf(
            if (editingChore?.isRotating() == true) editingChore.cadence()
            else RotationCadence.WEEKLY
        )
    }
    var rotationMembers by remember(editingChore, members) {
        mutableStateOf(
            editingChore?.rotationMemberIds()?.takeIf { it.isNotEmpty() }
                ?: members.filter { MemberRole.fromString(it.role) == MemberRole.CHILD }
                    .map { it.id }
                    .takeIf { it.size >= 2 }
                ?: members.map { it.id }.take(2)
        )
    }
    val rotationAnchor = remember(editingChore) {
        editingChore?.rotationAnchorDateKey?.ifBlank { DateStreakUtils.todayKey() } ?: DateStreakUtils.todayKey()
    }
    var timeOfDay by remember(editingChore) {
        mutableStateOf(TimeOfDay.fromString(editingChore?.timeOfDay))
    }
    var selectedDays by remember(editingChore) {
        mutableStateOf(editingChore?.scheduledDays()?.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) } ?: setOf(1, 2, 3, 4, 5, 6, 7))
    }
    var remindersEnabled by remember(editingChore) { mutableStateOf(editingChore?.remindersEnabled ?: false) }
    var reminderHour by remember(editingChore) {
        mutableIntStateOf(if ((editingChore?.reminderHour ?: -1) in 0..23) editingChore!!.reminderHour else 16)
    }
    var reminderMinute by remember(editingChore) { mutableIntStateOf(editingChore?.reminderMinute ?: 0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingChore == null) "New Household Chore" else "Edit Chore",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (editingChore != null && onDelete != null) {
                    TextButton(onClick = {
                        onDelete(editingChore)
                        onDismiss()
                    }) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Chore title") },
                placeholder = { Text("e.g., Empty dishwasher, Feed dog") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chore_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Assignment", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useRotation,
                    onClick = { useRotation = false },
                    label = { Text("Fixed Person") },
                    modifier = Modifier.testTag("chore_assign_fixed")
                )
                FilterChip(
                    selected = useRotation,
                    onClick = { useRotation = true },
                    leadingIcon = {
                        Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Rotating Turns") },
                    modifier = Modifier.testTag("chore_assign_rotate")
                )
            }

            if (!useRotation) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = assignedId == null,
                        onClick = { assignedId = null },
                        label = { Text("Anyone") }
                    )
                    members.forEach { member ->
                        FilterChip(
                            selected = assignedId == member.id,
                            onClick = { assignedId = member.id },
                            label = { Text(member.displayName) }
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RotationCadence.WEEKLY, RotationCadence.DAILY).forEach { cadence ->
                        FilterChip(
                            selected = rotationCadence == cadence,
                            onClick = { rotationCadence = cadence },
                            label = { Text(cadence.label) },
                            modifier = Modifier.testTag("rotation_cadence_${cadence.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Tap members in turn order (pick at least 2)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { member ->
                        val orderIdx = rotationMembers.indexOf(member.id)
                        val selected = orderIdx >= 0
                        FilterChip(
                            selected = selected,
                            onClick = {
                                rotationMembers = if (selected) {
                                    rotationMembers.filterNot { it == member.id }
                                } else {
                                    rotationMembers + member.id
                                }
                            },
                            label = {
                                Text(if (selected) "${orderIdx + 1}. ${member.displayName}" else member.displayName)
                            },
                            modifier = Modifier.testTag("rotation_member_${member.id}")
                        )
                    }
                }

                if (rotationMembers.size >= 2) {
                    val previewChore = ChoreEntity(
                        id = "preview",
                        title = title,
                        rotationMemberIdsCsv = rotationMembers.joinToString(","),
                        rotationCadence = rotationCadence.name,
                        rotationAnchorDateKey = rotationAnchor,
                        daysOfWeekCsv = selectedDays.sorted().joinToString(",")
                    )
                    val today = DateStreakUtils.todayKey()
                    val membersById = members.associateBy { it.id }
                    val todayPerson = previewChore.effectiveAssigneeForDate(today)?.let { membersById[it]?.displayName }
                    val nextPerson = previewChore.nextAssigneeAfterDate(today)?.let { membersById[it]?.displayName }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Autorenew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Current turn: ${todayPerson ?: "—"} • Up next: ${nextPerson ?: "—"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Add or select at least 2 family members to rotate turns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoreCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text("Time of day", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeOfDay.entries.forEach { tod ->
                    FilterChip(
                        selected = timeOfDay == tod,
                        onClick = { timeOfDay = tod },
                        label = { Text(tod.label) }
                    )
                }
            }

            Text("Repeat days", style = MaterialTheme.typography.labelLarge)
            DayOfWeekSelectorRow(selectedDays = selectedDays, onChange = { selectedDays = it })

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scheduled reminder alert", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
            }

            if (remindersEnabled) {
                CompactTimePickerRow(
                    hour = reminderHour,
                    minute = reminderMinute,
                    onChange = { h, m ->
                        reminderHour = h
                        reminderMinute = m
                    }
                )
            }

            val rotationValid = !useRotation || rotationMembers.size >= 2
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val isRotActive = useRotation && rotationMembers.size >= 2
                    val entity = ChoreEntity(
                        id = editingChore?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        notes = notes.trim(),
                        category = category.name,
                        assignedToMemberId = if (isRotActive) rotationMembers.firstOrNull() else assignedId,
                        rotationMemberIdsCsv = if (isRotActive) rotationMembers.joinToString(",") else "",
                        rotationCadence = if (isRotActive) rotationCadence.name else RotationCadence.NONE.name,
                        rotationAnchorDateKey = if (isRotActive) rotationAnchor else "",
                        daysOfWeekCsv = selectedDays.sorted().joinToString(","),
                        timeOfDay = timeOfDay.name,
                        reminderHour = if (remindersEnabled) reminderHour else -1,
                        reminderMinute = if (remindersEnabled) reminderMinute else 0,
                        remindersEnabled = remindersEnabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(entity)
                    onDismiss()
                },
                enabled = title.isNotBlank() && rotationValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_chore_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (editingChore == null) "Add Chore" else "Save Changes")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorSheet(
    members: List<MemberEntity>,
    places: List<SavedPlaceEntity>,
    defaultAssigneeId: String?,
    initialTitle: String = "",
    initialTriggerType: ReminderTriggerType = ReminderTriggerType.TIME,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var assignedId by remember { mutableStateOf(defaultAssigneeId) }
    var triggerType by remember(initialTriggerType) { mutableStateOf(initialTriggerType) }
    var hour by remember { mutableIntStateOf(16) }
    var minute by remember { mutableIntStateOf(0) }
    var repeatDays by remember { mutableStateOf(setOf<Int>()) }
    var selectedPlaceId by remember { mutableStateOf(places.firstOrNull()?.id) }
    var transition by remember { mutableStateOf(PlaceTransition.ARRIVE) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Smart Reminder", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What should we remember?") },
                placeholder = { Text("e.g., Unpack lunchbox, Grab milk") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Details (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Trigger by", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ReminderTriggerType.TIME,
                    ReminderTriggerType.LOCATION,
                    ReminderTriggerType.TIME_AND_LOCATION
                ).forEach { type ->
                    FilterChip(
                        selected = triggerType == type,
                        onClick = { triggerType = type },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("trigger_type_${type.name.lowercase()}")
                    )
                }
            }

            if (triggerType == ReminderTriggerType.TIME || triggerType == ReminderTriggerType.TIME_AND_LOCATION) {
                Text(
                    text = if (triggerType == ReminderTriggerType.TIME_AND_LOCATION) {
                        "Active starting at time"
                    } else {
                        "Reminder time"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                CompactTimePickerRow(
                    hour = hour,
                    minute = minute,
                    onChange = { h, m ->
                        hour = h
                        minute = m
                    }
                )
                Text("Repeat days (leave empty for today/one-off)", style = MaterialTheme.typography.labelMedium)
                DayOfWeekSelectorRow(selectedDays = repeatDays, onChange = { repeatDays = it })
            }

            if (triggerType == ReminderTriggerType.LOCATION || triggerType == ReminderTriggerType.TIME_AND_LOCATION) {
                Text("When", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceTransition.entries.forEach { tr ->
                        FilterChip(
                            selected = transition == tr,
                            onClick = { transition = tr },
                            label = { Text(tr.label) }
                        )
                    }
                }

                Text("Saved Place", style = MaterialTheme.typography.labelLarge)
                if (places.isEmpty()) {
                    Text(
                        text = "Pin a family place first on the Reminders tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        places.forEach { place ->
                            FilterChip(
                                selected = selectedPlaceId == place.id,
                                onClick = { selectedPlaceId = place.id },
                                label = { Text(place.name) }
                            )
                        }
                    }
                }
            }

            Text("For", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = assignedId == null,
                    onClick = { assignedId = null },
                    label = { Text("Everyone") }
                )
                members.forEach { member ->
                    FilterChip(
                        selected = assignedId == member.id,
                        onClick = { assignedId = member.id },
                        label = { Text(member.displayName) }
                    )
                }
            }

            val needsPlace = triggerType == ReminderTriggerType.LOCATION || triggerType == ReminderTriggerType.TIME_AND_LOCATION
            Button(
                onClick = {
                    val entity = ReminderEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        notes = notes.trim(),
                        assignedToMemberId = assignedId,
                        triggerType = triggerType.name,
                        triggerDateKey = if (repeatDays.isEmpty()) DateStreakUtils.todayKey() else "",
                        triggerHour = if (triggerType != ReminderTriggerType.LOCATION) hour else -1,
                        triggerMinute = if (triggerType != ReminderTriggerType.LOCATION) minute else 0,
                        repeatDaysCsv = repeatDays.sorted().joinToString(","),
                        placeId = if (needsPlace) selectedPlaceId else null,
                        placeTransition = transition.name,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(entity)
                    onDismiss()
                },
                enabled = title.isNotBlank() && (!needsPlace || selectedPlaceId != null),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_reminder_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Reminder")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorSheet(
    pinnedCoords: Pair<Double, Double>?,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onPinCurrentGps: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (SavedPlaceEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(200f) }
    var isHomePlace by remember { mutableStateOf(false) }

    LaunchedEffect(pinnedCoords) {
        if (pinnedCoords != null) {
            latText = String.format(java.util.Locale.US, "%.5f", pinnedCoords.first)
            lonText = String.format(java.util.Locale.US, "%.5f", pinnedCoords.second)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pin a Family Place", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Place name") },
                placeholder = { Text("e.g., Home, School, Grocery Store") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("place_name_input")
            )

            OutlinedButton(
                onClick = {
                    if (!hasLocationPermission) {
                        onRequestLocationPermission()
                    } else {
                        onPinCurrentGps()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_gps_button")
            ) {
                Icon(Icons.Filled.MyLocation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Current GPS Location")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Column {
                Text("Trigger radius: ${radius.toInt()} meters", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 100f..500f,
                    steps = 7
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trigger on Home Wi-Fi reconnect", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Fires 'Arrive at' reminders automatically when spotting family phones on home Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isHomePlace, onCheckedChange = { isHomePlace = it })
            }

            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: 0.0
                    val lon = lonText.toDoubleOrNull() ?: 0.0
                    onSave(
                        SavedPlaceEntity(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            latitude = lat,
                            longitude = lon,
                            radiusMeters = radius.toInt(),
                            isHomePlace = isHomePlace,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_place_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Family Place")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    initialDateKey: String,
    googleCalendarConnected: Boolean,
    onDismiss: () -> Unit,
    onSave: (CalendarEventEntity, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val weekSlots = remember { DateStreakUtils.weekSlots(0) + DateStreakUtils.weekSlots(1) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dateKey by remember(initialDateKey) { mutableStateOf(initialDateKey) }
    var allDay by remember { mutableStateOf(false) }
    var startHour by remember { mutableIntStateOf(17) }
    var startMinute by remember { mutableIntStateOf(0) }
    var colorKey by remember { mutableStateOf("terracotta") }
    var alsoExportToGoogle by remember { mutableStateOf(googleCalendarConnected) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Family Event", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event title") },
                placeholder = { Text("e.g., Soccer practice, Dentist, Family dinner") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_title_input")
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Day", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                weekSlots.forEach { slot ->
                    FilterChip(
                        selected = dateKey == slot.dateKey,
                        onClick = { dateKey = slot.dateKey },
                        label = { Text("${slot.shortLabel} ${slot.monthShort} ${slot.dayNumber}") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("All-day event", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            if (!allDay) {
                CompactTimePickerRow(
                    hour = startHour,
                    minute = startMinute,
                    onChange = { h, m ->
                        startHour = h
                        startMinute = m
                    }
                )
            }

            ColorPickerRow(selectedKey = colorKey, onSelect = { colorKey = it })

            if (googleCalendarConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Also add to linked Google Calendar", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = alsoExportToGoogle, onCheckedChange = { alsoExportToGoogle = it })
                }
            }

            Button(
                onClick = {
                    val event = CalendarEventEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        notes = notes.trim(),
                        dateKey = dateKey,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = (startHour + 1) % 24,
                        endMinute = startMinute,
                        allDay = allDay,
                        location = location.trim(),
                        colorKey = colorKey,
                        source = CalendarSource.HOMELIST.name,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(event, alsoExportToGoogle)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_event_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Event")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DayOfWeekSelectorRow(
    selectedDays: Set<Int>,
    onChange: (Set<Int>) -> Unit
) {
    val labels = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { (dayNum, label) ->
            val active = dayNum in selectedDays
            FilterChip(
                selected = active,
                onClick = {
                    val updated = if (active) selectedDays - dayNum else selectedDays + dayNum
                    onChange(updated)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun CompactTimePickerRow(
    hour: Int,
    minute: Int,
    onChange: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onChange((hour + 23) % 24, minute) }) { Text("-1h") }
        Text(
            text = DateStreakUtils.formatTime12h(hour, minute),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        OutlinedButton(onClick = { onChange((hour + 1) % 24, minute) }) { Text("+1h") }
        OutlinedButton(onClick = {
            val nextMin = (minute + 15) % 60
            onChange(hour, nextMin)
        }) { Text("+15m") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePushReminderSheet(
    members: List<MemberEntity>,
    onDismiss: () -> Unit,
    onSend: (message: String, targetMemberId: String?, targetLabel: String, isUrgent: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val childMembers = remember(members) {
        members.filter { MemberRole.fromString(it.role) == MemberRole.CHILD }
    }
    val orderedRecipients = remember(members) {
        members.sortedBy { if (MemberRole.fromString(it.role) == MemberRole.CHILD) 0 else 1 }
    }

    var selectedMemberId by remember(orderedRecipients) {
        mutableStateOf(childMembers.firstOrNull()?.id)
    }
    var message by remember { mutableStateOf("") }
    var isUrgent by remember { mutableStateOf(false) }

    val quickPresets = remember {
        listOf(
            "Time for dinner!",
            "Start homework",
            "Come downstairs",
            "Bedtime in 15 min",
            "Brush teeth",
            "Pack backpack"
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("Push Live Reminder", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Sends an instant alert over your family mesh right now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Send to", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMemberId == null,
                    onClick = { selectedMemberId = null },
                    label = { Text("Everyone") },
                    modifier = Modifier.testTag("live_push_target_all")
                )
                orderedRecipients.forEach { member ->
                    val roleTag = if (MemberRole.fromString(member.role) == MemberRole.CHILD) "Child" else "Parent"
                    FilterChip(
                        selected = selectedMemberId == member.id,
                        onClick = { selectedMemberId = member.id },
                        label = { Text("${member.displayName} ($roleTag)") },
                        modifier = Modifier.testTag("live_push_target_${member.id}")
                    )
                }
            }

            Text("Quick taps", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPresets.forEach { preset ->
                    FilterChip(
                        selected = message.equals(preset, ignoreCase = true),
                        onClick = { message = preset },
                        label = { Text(preset) }
                    )
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Reminder message") },
                placeholder = { Text("e.g., Grab your cleats, leaving in 5 mins!") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_push_message_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Urgent alert", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Highlights the banner with an urgent badge on their phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isUrgent,
                    onCheckedChange = { isUrgent = it },
                    modifier = Modifier.testTag("live_push_urgent_switch")
                )
            }

            val targetLabel = orderedRecipients.firstOrNull { it.id == selectedMemberId }?.displayName ?: "Everyone"

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onSend(message.trim(), selectedMemberId, targetLabel, isUrgent)
                    onDismiss()
                },
                enabled = message.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("send_live_push_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Push Live Now")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

