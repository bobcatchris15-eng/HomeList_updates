package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.DateStreakUtils
import com.example.data.MealSlot
import com.example.ui.HomeListUiState
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.MeshStatusPill
import com.example.ui.components.StreakBadgePill
import com.example.ui.components.WarmProgressRing
import kotlinx.coroutines.delay

@Composable
fun WallDisplayScreen(
    state: HomeListUiState,
    onSyncMeshNow: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var keepAwake by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    BackHandler(onBack = onExit)

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    DisposableEffect(keepAwake) {
        val window = (context as? Activity)?.window
        if (keepAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val (clockDigits, amPm) = remember(nowMs) { DateStreakUtils.formatClockTime(nowMs) }
    val todayKey = remember(nowMs) { DateStreakUtils.todayKey() }
    val weekSlots = remember(todayKey) { DateStreakUtils.weekSlots(0) }
    val completedIds = remember(state.completions) {
        state.completions.filter { it.isCompleted }.map { it.id }.toSet()
    }
    val completedDatesByChore = remember(state.completions) {
        state.completions
            .filter { it.isCompleted }
            .groupBy({ it.choreId }, { it.dateKey })
            .mapValues { it.value.toSet() }
    }
    val todayChores = remember(state.chores, completedDatesByChore, todayKey) {
        state.chores.filter {
            it.isScheduledForDate(todayKey, completedDatesByChore[it.id].orEmpty(), todayKey)
        }
    }
    val overallDone = todayChores.count { "${it.id}_${todayKey}" in completedIds }
    val overallProgress = if (todayChores.isEmpty()) 0f else overallDone.toFloat() / todayChores.size.toFloat()
    val weekKeys = remember(weekSlots) { weekSlots.map { it.dateKey }.toSet() }
    val weekEvents = remember(state.events, weekKeys) {
        state.events.filter { it.dateKey in weekKeys }.take(6)
    }
    val todayMeals = remember(state.dinnerOptions, todayKey) {
        state.dinnerOptions.filter { it.dateKey == todayKey }
    }
    val tonightOptions = remember(todayMeals) {
        todayMeals.filter { it.slot() == MealSlot.DINNER }.ifEmpty { todayMeals }
    }
    val tonightWinner = remember(tonightOptions) { tonightOptions.firstOrNull { it.isWinner } }
    val tonightLeader = remember(tonightOptions) { tonightOptions.maxByOrNull { it.voteCount() } }
    val groceryToBuyCount = remember(state.groceryItems) { state.groceryItems.count { !it.isChecked } }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("wall_display_root"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Editorial Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.config?.householdName ?: "HomeList",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = DateStreakUtils.formatFullToday(nowMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = clockDigits,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = amPm,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    MeshStatusPill(
                        status = state.meshStatus,
                        peerCount = state.peers.size,
                        onClick = onSyncMeshNow
                    )
                    IconButton(
                        onClick = { keepAwake = !keepAwake },
                        modifier = Modifier.testTag("wall_keep_awake_button")
                    ) {
                        Icon(
                            imageVector = if (keepAwake) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = "Toggle keep screen awake",
                            tint = if (keepAwake) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.testTag("wall_exit_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit Wall Display")
                    }
                }
            }

            // Summary Row: Household Ring + Member Streaks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Today", style = MaterialTheme.typography.titleSmall)
                        WarmProgressRing(
                            progress = overallProgress,
                            completed = overallDone,
                            total = todayChores.size,
                            size = 96.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Restaurant,
                                        contentDescription = "Tonight's dinner",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (tonightWinner != null) "Tonight's Dinner" else "Dinner Vote",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = tonightWinner?.mealName
                                        ?: tonightLeader?.let { "${it.mealName} (${it.voteCount()})" }
                                        ?: "Open for votes",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (groceryToBuyCount > 0) {
                                    Text(
                                        text = "$groceryToBuyCount on shopping list",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Weekly Family Board", style = MaterialTheme.typography.titleMedium)

                        // Header Row (Member + Mon..Sun + Streak)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Member",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.6f)
                            )
                            weekSlots.forEach { slot ->
                                Text(
                                    text = slot.shortLabel.take(2),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (slot.isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (slot.isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.6f)
                                )
                            }
                            Text(
                                text = "Streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        state.members.forEach { member ->
                            val stats = state.memberStats[member.id]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1.6f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MemberAvatarBadge(
                                        name = member.displayName,
                                        colorKey = member.avatarColorKey,
                                        size = 28.dp
                                    )
                                    Text(
                                        text = member.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                weekSlots.forEach { slot ->
                                    val sched = state.chores.filter {
                                        it.isScheduledForDate(slot.dateKey, completedDatesByChore[it.id].orEmpty(), todayKey) &&
                                            it.effectiveAssigneeForDate(slot.dateKey) == member.id
                                    }
                                    val doneCount = sched.count { "${it.id}_${slot.dateKey}" in completedIds }
                                    val dotColor = when {
                                        sched.isEmpty() -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        doneCount >= sched.size -> MaterialTheme.colorScheme.secondary
                                        doneCount > 0 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    }
                                    Box(
                                        modifier = Modifier.weight(0.6f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    StreakBadgePill(streakDays = stats?.currentStreak ?: 0)
                                }
                            }
                        }
                    }
                }
            }

            // Upcoming Events Strip on Wall Display
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("This Week's Family Calendar", style = MaterialTheme.typography.titleMedium)
                    if (weekEvents.isEmpty()) {
                        Text(
                            text = "No scheduled calendar events this week.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        weekEvents.forEach { ev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${ev.dateKey} • ${if (ev.allDay) "All day" else DateStreakUtils.formatTime12h(ev.startHour, ev.startMinute)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
