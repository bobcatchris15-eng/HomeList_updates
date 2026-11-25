package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calendar.DeviceCalendarInfo
import com.example.data.CalendarEventEntity
import com.example.data.CalendarSource
import com.example.data.DateStreakUtils
import com.example.ui.HomeListUiState
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.StreakBadgePill
import com.example.ui.theme.memberColorForKey

@Composable
fun CalendarScreen(
    state: HomeListUiState,
    hasCalendarPermission: Boolean,
    onRequestCalendarPermission: () -> Unit,
    onLoadDeviceCalendars: () -> Unit,
    onSelectGoogleCalendar: (DeviceCalendarInfo) -> Unit,
    onRefreshGoogleCalendar: () -> Unit,
    onImportIcsUrl: (String) -> Unit,
    onOpenAddEvent: (String) -> Unit,
    onDeleteEvent: (CalendarEventEntity) -> Unit,
    onBuildExportIntent: (CalendarEventEntity) -> Intent,
    onOpenWallDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekSlots = remember(weekOffset) { DateStreakUtils.weekSlots(weekOffset) }
    var selectedDateKey by remember(weekOffset) {
        mutableStateOf(weekSlots.firstOrNull { it.isToday }?.dateKey ?: weekSlots.first().dateKey)
    }
    var showCalendarPickerDialog by remember { mutableStateOf(false) }
    var showIcsDialog by remember { mutableStateOf(false) }

    val todayKey = remember { DateStreakUtils.todayKey() }
    val completedLookup = remember(state.completions) {
        state.completions.filter { it.isCompleted }.map { it.id }.toSet()
    }
    val completedDatesByChore = remember(state.completions) {
        state.completions
            .filter { it.isCompleted }
            .groupBy({ it.choreId }, { it.dateKey })
            .mapValues { it.value.toSet() }
    }
    val selectedSlot = weekSlots.firstOrNull { it.dateKey == selectedDateKey } ?: weekSlots.first()
    val dayEvents = remember(state.events, selectedDateKey) {
        state.events.filter { it.dateKey == selectedDateKey }
    }
    val dayChores = remember(state.chores, selectedSlot, completedDatesByChore, todayKey) {
        state.chores.filter {
            it.isScheduledForDate(selectedSlot.dateKey, completedDatesByChore[it.id].orEmpty(), todayKey)
        }
    }
    val membersById = remember(state.members) { state.members.associateBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Week Strip Header Card
        item(key = "week_strip") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { weekOffset-- },
                            modifier = Modifier.testTag("calendar_prev_week")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${weekSlots.first().monthShort} ${weekSlots.first().dayNumber} – ${weekSlots.last().monthShort} ${weekSlots.last().dayNumber}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (weekOffset != 0) {
                                TextButton(onClick = { weekOffset = 0 }) {
                                    Text("Back to this week")
                                }
                            }
                        }
                        IconButton(
                            onClick = { weekOffset++ },
                            modifier = Modifier.testTag("calendar_next_week")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekSlots.forEach { slot ->
                            val isSelected = slot.dateKey == selectedDateKey
                            val scheduledCount = state.chores.count {
                                it.isScheduledForDate(slot.dateKey, completedDatesByChore[it.id].orEmpty(), todayKey)
                            }
                            val doneCount = state.chores.count {
                                it.isScheduledForDate(slot.dateKey, completedDatesByChore[it.id].orEmpty(), todayKey) &&
                                    "${it.id}_${slot.dateKey}" in completedLookup
                            }
                            val hasEvents = state.events.any { it.dateKey == slot.dateKey }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            slot.isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                                        }
                                    )
                                    .clickable { selectedDateKey = slot.dateKey }
                                    .padding(vertical = 8.dp)
                                    .testTag("calendar_day_${slot.dateKey}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = slot.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = slot.dayNumber,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (scheduledCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        doneCount >= scheduledCount -> MaterialTheme.colorScheme.secondary
                                                        doneCount > 0 -> MaterialTheme.colorScheme.tertiary
                                                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                                        else -> MaterialTheme.colorScheme.outlineVariant
                                                    }
                                                )
                                        )
                                    }
                                    if (hasEvents) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Google Calendar Sync & Optional Wall Display Bar
        item(key = "calendar_sync_bar") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Calendar Sync", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = state.config?.googleCalendarName?.takeIf { it.isNotBlank() }?.let {
                                    "Linked to $it • shared across family mesh"
                                } ?: "Pull Google Calendar events onto your phone and share them over the family mesh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.isCalendarSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (!hasCalendarPermission) {
                                    onRequestCalendarPermission()
                                } else if ((state.config?.googleCalendarId ?: -1L) > 0L) {
                                    onRefreshGoogleCalendar()
                                } else {
                                    onLoadDeviceCalendars()
                                    showCalendarPickerDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("sync_google_calendar_button")
                        ) {
                            Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if ((state.config?.googleCalendarId ?: -1L) > 0L) "Sync Now" else "Connect Google"
                            )
                        }
                        OutlinedButton(
                            onClick = { showIcsDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("import_ics_button")
                        ) {
                            Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(".ics URL")
                        }
                        if (hasCalendarPermission && (state.config?.googleCalendarId ?: -1L) > 0L) {
                            TextButton(onClick = {
                                onLoadDeviceCalendars()
                                showCalendarPickerDialog = true
                            }) {
                                Text("Switch")
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Events
        item(key = "events_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Events • ${selectedSlot.shortLabel}, ${selectedSlot.monthShort} ${selectedSlot.dayNumber}",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = { onOpenAddEvent(selectedDateKey) },
                    modifier = Modifier.testTag("add_event_button")
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Event")
                }
            }
        }

        if (dayEvents.isEmpty()) {
            item(key = "no_events_day") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No family or Google Calendar events on this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(dayEvents, key = { "ev_${it.id}" }) { event ->
                val accent = memberColorForKey(event.colorKey)
                val source = CalendarSource.fromString(event.source)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(accent)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (event.allDay) append("All day")
                                    else {
                                        append(DateStreakUtils.formatTime12h(event.startHour, event.startMinute))
                                        append(" – ")
                                        append(DateStreakUtils.formatTime12h(event.endHour, event.endMinute))
                                    }
                                    append(" • ")
                                    append(source.label)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (event.location.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.LocationOn,
                                        contentDescription = "Event location",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = event.location,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                try {
                                    context.startActivity(onBuildExportIntent(event))
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = "Open in Google Calendar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.isParent || source == CalendarSource.HOMELIST) {
                            IconButton(onClick = { onDeleteEvent(event) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete event",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Chores Snapshot
        item(key = "day_chores_summary") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val done = dayChores.count { "${it.id}_${selectedDateKey}" in completedLookup }
                    Text(
                        text = "Chores on ${selectedSlot.shortLabel} ($done/${dayChores.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (dayChores.isEmpty()) {
                        Text(
                            text = "No chores scheduled on ${selectedSlot.shortLabel}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        dayChores.forEach { chore ->
                            val isDone = "${chore.id}_${selectedDateKey}" in completedLookup
                            val assignee = chore.effectiveAssigneeForDate(selectedDateKey)
                                ?.let { membersById[it]?.displayName } ?: "Anyone"
                            val suffix = when {
                                !chore.isRecurring -> " • One-off"
                                chore.isRotating() -> " ↻"
                                else -> ""
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (isDone) "✓" else "○"} ${chore.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDone) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$assignee$suffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Family Progress & Daily Streaks Leaderboard
        item(key = "member_streaks_board") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Family Streaks & Progress", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = onOpenWallDisplay,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("launch_wall_display_button")
                        ) {
                            Icon(Icons.Outlined.Tv, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wall Board")
                        }
                    }

                    state.members.forEach { member ->
                        val stats = state.memberStats[member.id]
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MemberAvatarBadge(
                                        name = member.displayName,
                                        colorKey = member.avatarColorKey,
                                        size = 32.dp
                                    )
                                    Column {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Today: ${stats?.todayCompleted ?: 0}/${stats?.todayTotal ?: 0} • Best: ${stats?.longestStreak ?: 0}d",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StreakBadgePill(streakDays = stats?.currentStreak ?: 0)
                            }
                            LinearProgressIndicator(
                                progress = { stats?.todayProgress ?: 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(99.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCalendarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarPickerDialog = false },
            title = { Text("Choose Google Calendar") },
            text = {
                if (state.deviceCalendars.isEmpty()) {
                    Text("No calendars found yet. Ensure a Google account calendar is synced on this phone.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.deviceCalendars.forEach { cal ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectGoogleCalendar(cal)
                                        showCalendarPickerDialog = false
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(cal.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (cal.accountName.isNotBlank()) {
                                        Text(
                                            cal.accountName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarPickerDialog = false }) { Text("Close") }
            }
        )
    }

    if (showIcsDialog) {
        var urlInput by remember { mutableStateOf(state.config?.icsFeedUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showIcsDialog = false },
            title = { Text("Import Google Calendar .ics URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a Google Calendar secret iCal address (.ics) to pull events over HTTPS without extra permissions.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("https://calendar.google.com/.../basic.ics") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportIcsUrl(urlInput)
                        showIcsDialog = false
                    },
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIcsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
