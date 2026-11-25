package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChoreEntity
import com.example.data.DateStreakUtils
import com.example.data.MealSlot
import com.example.data.MemberEntity
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.TimeOfDay
import com.example.ui.HomeListUiState
import com.example.ui.components.AllDoneBotanicalCard
import com.example.ui.components.ChoreChecklistCard
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.SevenDayStreakDots
import com.example.ui.components.StreakBadgePill
import com.example.ui.components.WarmProgressRing
import com.example.updates.AppUpdateChecker

@Composable
fun TodayScreen(
    state: HomeListUiState,
    onToggleChore: (String) -> Unit,
    onSendNudge: (ChoreEntity, MemberEntity?) -> Unit,
    onEditChore: (ChoreEntity) -> Unit,
    onCompleteNudge: (ReminderEntity) -> Unit,
    onDismissNudge: (ReminderEntity) -> Unit,
    onOpenAddChore: () -> Unit,
    onOpenLivePush: () -> Unit,
    onNavigateShopping: () -> Unit,
    onNavigateMeals: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateCalendar: () -> Unit,
    onDismissUpdateBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayKey = remember { DateStreakUtils.todayKey() }
    val completedSet = remember(state.completions, todayKey) {
        state.completions.filter { it.isCompleted && it.dateKey == todayKey }.map { it.choreId }.toSet()
    }
    val completedDatesByChore = remember(state.completions) {
        state.completions
            .filter { it.isCompleted }
            .groupBy({ it.choreId }, { it.dateKey })
            .mapValues { it.value.toSet() }
    }
    val membersById = remember(state.members) { state.members.associateBy { it.id } }

    var selectedMemberFilterId by remember { mutableStateOf<String?>(null) }

    val selfId = state.config?.selfMemberId.orEmpty()
    val todayAllChores = remember(state.chores, completedDatesByChore, todayKey) {
        state.chores.filter {
            it.isScheduledForDate(todayKey, completedDatesByChore[it.id].orEmpty(), todayKey)
        }
    }

    val visibleChores = remember(todayAllChores, state.isParent, selfId, selectedMemberFilterId, todayKey) {
        when {
            !state.isParent -> todayAllChores.filter {
                val assigneeId = it.effectiveAssigneeForDate(todayKey)
                assigneeId == null || assigneeId == selfId
            }
            selectedMemberFilterId != null -> todayAllChores.filter {
                val assigneeId = it.effectiveAssigneeForDate(todayKey)
                assigneeId == selectedMemberFilterId || assigneeId == null
            }
            else -> todayAllChores
        }
    }

    val groupedChores = remember(visibleChores) {
        visibleChores
            .groupBy { TimeOfDay.fromString(it.timeOfDay) }
            .toSortedMap(compareBy { it.sortOrder })
    }

    val activeNudges = remember(state.reminders, selfId) {
        state.reminders.filter {
            !it.isCompleted &&
                ReminderTriggerType.fromString(it.triggerType) == ReminderTriggerType.NUDGE &&
                (it.assignedToMemberId == null || it.assignedToMemberId == selfId) &&
                it.createdByMemberId != selfId
        }.sortedWith(compareByDescending<ReminderEntity> { it.isUrgent }.thenByDescending { it.updatedAt })
    }

    val recentSentPushes = remember(state.reminders, selfId, state.isParent) {
        if (!state.isParent) emptyList()
        else state.reminders.filter {
            ReminderTriggerType.fromString(it.triggerType) == ReminderTriggerType.NUDGE &&
                it.createdByMemberId == selfId
        }.sortedByDescending { it.updatedAt }.take(3)
    }

    val todayMealOptions = remember(state.dinnerOptions, todayKey) {
        state.dinnerOptions.filter { it.dateKey == todayKey }
    }
    val tonightDinnerOptions = remember(todayMealOptions) {
        todayMealOptions.filter { it.slot() == MealSlot.DINNER }.ifEmpty { todayMealOptions }
    }
    val tonightWinner = remember(tonightDinnerOptions) {
        tonightDinnerOptions.firstOrNull { it.isWinner }
    }
    val tonightLeader = remember(tonightDinnerOptions) {
        tonightDinnerOptions.maxByOrNull { it.voteCount() }
    }
    val groceriesRemaining = remember(state.groceryItems) {
        state.groceryItems.count { !it.isChecked }
    }

    val todayEvents = remember(state.events, todayKey) {
        state.events.filter { it.dateKey == todayKey }
    }

    val activeStandaloneReminders = remember(state.reminders, selfId, state.isParent) {
        state.reminders.filter {
            !it.isCompleted &&
                ReminderTriggerType.fromString(it.triggerType) != ReminderTriggerType.NUDGE &&
                (state.isParent || it.assignedToMemberId == null || it.assignedToMemberId == selfId)
        }.take(3)
    }

    val doneCount = visibleChores.count { it.id in completedSet }
    val totalCount = visibleChores.size
    val progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
    val selfStats = state.memberStats[selfId]

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Available App Update Banner (from GitHub Releases or newer Family Mesh peer)
        val update = state.availableUpdate
        if (update != null) {
            item(key = "app_update_banner") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("app_update_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = "Update available",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Column {
                                    Text(
                                        text = "HomeList v${update.versionName} Available",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = update.releaseNotes.ifBlank { "New update via ${update.sourceLabel}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onDismissUpdateBanner(update.versionName) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss update notice",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Button(
                            onClick = { AppUpdateChecker.openUpdateUrl(context, update.downloadUrl) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("open_update_banner_button")
                        ) {
                            Text("Get Update")
                        }
                    }
                }
            }
        }

        // Incoming Parent Nudges & Live Push Reminders Banner
        items(activeNudges, key = { "nudge_${it.id}" }) { nudge ->
            val isStandalonePush = nudge.linkedChoreId.isNullOrBlank()
            val containerColor = if (nudge.isUrgent) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = if (nudge.isUrgent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            val borderColor = if (nudge.isUrgent) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            }

            AnimatedVisibility(visible = true) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = containerColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.testTag("nudge_banner_${nudge.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (nudge.isUrgent) Icons.Filled.PriorityHigh else Icons.Filled.NotificationsActive,
                                    contentDescription = "Parent reminder",
                                    tint = if (nudge.isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (nudge.isUrgent) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.error
                                            ) {
                                                Text(
                                                    text = "URGENT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isStandalonePush) {
                                                "Live reminder from ${nudge.createdByName.ifBlank { "Parent" }}"
                                            } else {
                                                "Chore reminder from ${nudge.createdByName.ifBlank { "Parent" }}"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = contentColor
                                        )
                                    }
                                    Text(
                                        text = nudge.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = contentColor
                                    )
                                }
                            }
                            IconButton(onClick = { onDismissNudge(nudge) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss reminder",
                                    tint = contentColor
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onCompleteNudge(nudge) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("complete_nudge_${nudge.id}")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isStandalonePush) "Got It" else "Mark Done")
                            }
                        }
                    }
                }
            }
        }

        // Hero Greeting & Progress Summary Card
        item(key = "hero_summary") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${DateStreakUtils.greetingForHour()}, ${state.selfMember?.displayName ?: "Family"}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = DateStreakUtils.formatFullToday(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (!state.isParent && selfStats != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StreakBadgePill(streakDays = selfStats.currentStreak)
                                SevenDayStreakDots(completedDays = selfStats.lastSevenDayCompleted)
                            }
                        } else {
                            val topStreak = state.memberStats.values.maxOfOrNull { it.currentStreak } ?: 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StreakBadgePill(streakDays = topStreak)
                                Text(
                                    text = "${state.members.size} member${if (state.members.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    WarmProgressRing(
                        progress = progress,
                        completed = doneCount,
                        total = totalCount
                    )
                }
            }
        }

        // Parent Quick Action: Push Live Reminder to Child + Delivery/Seen Status
        if (state.isParent) {
            item(key = "parent_live_push_card") {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Campaign,
                                    contentDescription = "Push live reminder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Push Live Reminder",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Alert a child's phone immediately over the mesh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onOpenLivePush,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("today_push_live_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Send")
                            }
                        }

                        if (recentSentPushes.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                recentSentPushes.forEach { sent ->
                                    val recipientName = sent.assignedToMemberId?.let { membersById[it]?.displayName } ?: "Everyone"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "To $recipientName: ${sent.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (sent.isCompleted) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = if (sent.isCompleted) "Seen ✓" else "Sent",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (sent.isCompleted) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tonight's Dinner & Shopping Quick Glance Row
        item(key = "tonight_dinner_glance") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateMeals)
                        .testTag("today_dinner_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restaurant,
                                    contentDescription = "Tonight's dinner",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = when {
                                        tonightWinner != null -> "${tonightWinner.slot().label}: ${tonightWinner.mealName}"
                                        tonightLeader != null -> "Meal Vote: ${tonightLeader.mealName}"
                                        else -> "What's on the menu today?"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        tonightWinner != null -> "Planned"
                                        tonightLeader != null -> "${tonightLeader.voteCount()} vote${if (tonightLeader.voteCount() == 1) "" else "s"} • ${tonightDinnerOptions.size} idea${if (tonightDinnerOptions.size == 1) "" else "s"}"
                                        else -> "Tap to request or vote on meals"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Meals",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (groceriesRemaining > 0) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateShopping)
                            .testTag("today_shopping_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ShoppingCart,
                                        contentDescription = "Shopping list",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "$groceriesRemaining item${if (groceriesRemaining == 1) "" else "s"} on Shopping List",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = state.groceryItems
                                            .filter { !it.isChecked }
                                            .take(3)
                                            .joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Shopping List",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Parent Member Filter & Streak Strip
        if (state.isParent && state.members.isNotEmpty()) {
            item(key = "parent_member_strip") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedMemberFilterId == null,
                        onClick = { selectedMemberFilterId = null },
                        label = { Text("Everyone") },
                        modifier = Modifier.testTag("filter_member_all")
                    )
                    state.members.forEach { member ->
                        val stats = state.memberStats[member.id]
                        FilterChip(
                            selected = selectedMemberFilterId == member.id,
                            onClick = {
                                selectedMemberFilterId =
                                    if (selectedMemberFilterId == member.id) null else member.id
                            },
                            leadingIcon = {
                                MemberAvatarBadge(
                                    name = member.displayName,
                                    colorKey = member.avatarColorKey,
                                    size = 22.dp
                                )
                            },
                            label = {
                                Text(
                                    text = "${member.displayName} ${stats?.todayCompleted ?: 0}/${stats?.todayTotal ?: 0}"
                                )
                            },
                            modifier = Modifier.testTag("filter_member_${member.id}")
                        )
                    }
                }
            }
        }

        // Today's Events & Active Reminders Quick Glance
        if (todayEvents.isNotEmpty() || activeStandaloneReminders.isNotEmpty()) {
            item(key = "today_events_reminders") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's Schedule & Reminders",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(onClick = onNavigateReminders) { Text("Reminders") }
                                TextButton(onClick = onNavigateCalendar) { Text("Calendar") }
                            }
                        }

                        todayEvents.take(3).forEach { ev ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Event,
                                    contentDescription = "Event",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (ev.allDay) "All day" else DateStreakUtils.formatTime12h(ev.startHour, ev.startMinute)} • ${ev.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        activeStandaloneReminders.forEach { rem ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = "Reminder",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = rem.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty or All-Done Botanical Celebration
        if (visibleChores.isEmpty()) {
            item(key = "empty_chores") {
                AllDoneBotanicalCard(
                    title = if (state.isParent) "Ready to build your routine" else "No chores scheduled today",
                    subtitle = if (state.isParent) {
                        "Tap the + button below to add daily chores, time/place reminders, or family events."
                    } else {
                        "Enjoy your free day! New chores from parents will appear here automatically."
                    }
                )
                if (state.isParent) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAddChore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("empty_add_chore_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Add Your First Chore")
                    }
                }
            }
        } else {
            if (doneCount == totalCount && totalCount > 0) {
                item(key = "all_done_celebration") {
                    AllDoneBotanicalCard(
                        title = "All chores done for today!",
                        subtitle = if (!state.isParent && selfStats != null) {
                            "Awesome momentum — ${selfStats.currentStreak} day streak!"
                        } else {
                            "Every scheduled household chore is checked off."
                        }
                    )
                }
            }

            groupedChores.forEach { (timeWindow, choresInGroup) ->
                item(key = "header_${timeWindow.name}") {
                    Text(
                        text = timeWindow.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
                items(choresInGroup, key = { it.id }) { chore ->
                    val assignee = chore.effectiveAssigneeForDate(todayKey)?.let { membersById[it] }
                    val nextAssignee = chore.nextAssigneeAfterDate(todayKey)?.let { membersById[it] }
                    ChoreChecklistCard(
                        chore = chore,
                        isCompleted = chore.id in completedSet,
                        assignee = assignee,
                        nextAssignee = nextAssignee,
                        isParent = state.isParent,
                        onToggleComplete = { onToggleChore(chore.id) },
                        onSendNudge = if (state.isParent) {
                            { onSendNudge(chore, assignee) }
                        } else null,
                        onEdit = if (state.isParent) {
                            { onEditChore(chore) }
                        } else null
                    )
                }
            }
        }
    }
}
