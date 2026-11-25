package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChoreCategory
import com.example.data.ChoreEntity
import com.example.data.DateStreakUtils
import com.example.data.DeviceType
import com.example.data.MemberEntity
import com.example.data.PlaceTransition
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.SavedPlaceEntity
import com.example.mesh.MeshRuntimeStatus
import com.example.ui.theme.memberColorForKey

fun iconForCategory(category: String): ImageVector = when (ChoreCategory.fromString(category)) {
    ChoreCategory.KITCHEN -> Icons.Outlined.Kitchen
    ChoreCategory.BEDROOM -> Icons.Outlined.Bed
    ChoreCategory.PETS -> Icons.Outlined.Pets
    ChoreCategory.OUTDOOR -> Icons.Outlined.Park
    ChoreCategory.LAUNDRY -> Icons.Outlined.LocalLaundryService
    ChoreCategory.SCHOOL -> Icons.Outlined.School
    ChoreCategory.ERRAND -> Icons.Outlined.ShoppingBag
    ChoreCategory.TIDY -> Icons.Outlined.CleaningServices
}

fun iconForDeviceType(deviceType: String): ImageVector = when (DeviceType.fromString(deviceType)) {
    DeviceType.PHONE -> Icons.Outlined.Smartphone
    DeviceType.TABLET -> Icons.Outlined.TabletAndroid
    DeviceType.WINDOWS -> Icons.Outlined.Computer
    DeviceType.PI_DISPLAY -> Icons.Outlined.Tv
}

@Composable
fun WarmProgressRing(
    progress: Float,
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    strokeWidth: Dp = 9.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550),
        label = "ring_progress"
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = if (total > 0 && completed >= total) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (total == 0) "0" else "$completed/$total",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MemberAvatarBadge(
    name: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val bg = memberColorForKey(colorKey)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.18f))
            .border(1.5.dp, bg.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleSmall,
            color = bg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StreakBadgePill(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = "Daily streak",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${streakDays}d streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun SevenDayStreakDots(
    completedDays: List<Boolean>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        completedDays.forEach { done ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
            )
        }
    }
}

@Composable
fun MeshStatusPill(
    status: MeshRuntimeStatus,
    peerCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = (status.serverRunning && status.activeWifiPeers > 0) || status.hubConnected
    val container = if (isLive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (isLive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        status.isSyncing -> "Syncing..."
        status.activeWifiPeers > 0 && status.hubConnected -> "Wi-Fi + Hub"
        status.activeWifiPeers > 0 -> "Wi-Fi • ${status.activeWifiPeers} peer${if (status.activeWifiPeers == 1) "" else "s"}"
        status.hubConnected -> "Home Hub"
        status.serverRunning -> "Wi-Fi • ${peerCount} node${if (peerCount == 1) "" else "s"}"
        else -> "Local offline"
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .testTag("mesh_status_pill"),
        shape = RoundedCornerShape(999.dp),
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (status.activeWifiPeers > 0) Icons.Outlined.Wifi else Icons.Outlined.Devices,
                contentDescription = "Mesh status",
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChoreChecklistCard(
    chore: ChoreEntity,
    isCompleted: Boolean,
    assignee: MemberEntity?,
    isParent: Boolean,
    onToggleComplete: () -> Unit,
    nextAssignee: MemberEntity? = null,
    onSendNudge: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check_scale"
    )
    val checkFill by animateColorAsState(
        targetValue = if (isCompleted) MaterialTheme.colorScheme.secondary else Color.Transparent,
        animationSpec = tween(220),
        label = "check_fill"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(220),
        label = "card_bg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chore_card_${chore.id}"),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleComplete)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile 48dp touch target check circle
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .scale(checkScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(checkFill)
                        .border(
                            width = 2.dp,
                            color = if (isCompleted) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = iconForCategory(chore.category),
                        contentDescription = chore.category,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = buildString {
                            append(assignee?.displayName ?: "Anyone")
                            append(" • ")
                            if (chore.isRecurring) {
                                append(DateStreakUtils.formatDaysSummary(chore.daysOfWeekCsv))
                            } else {
                                val today = DateStreakUtils.todayKey()
                                when {
                                    chore.dueDateKey.isBlank() -> append("One-off")
                                    chore.dueDateKey == today -> append("One-off • Today")
                                    chore.dueDateKey < today -> append("One-off • Overdue")
                                    else -> append("One-off • ${chore.dueDateKey}")
                                }
                            }
                            if (chore.remindersEnabled && chore.reminderHour in 0..23) {
                                append(" • ")
                                append(DateStreakUtils.formatTime12h(chore.reminderHour, chore.reminderMinute))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (chore.isRotating()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = "Rotating chore",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = buildString {
                                append(chore.cadence().shortLabel)
                                if (nextAssignee != null) {
                                    append(" • Next: ${nextAssignee.displayName}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isParent && !isCompleted && onSendNudge != null) {
                IconButton(
                    onClick = onSendNudge,
                    modifier = Modifier.testTag("nudge_chore_${chore.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Send reminder nudge",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isParent && onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_chore_${chore.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit chore",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: ReminderEntity,
    assignee: MemberEntity?,
    place: SavedPlaceEntity?,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val triggerType = ReminderTriggerType.fromString(reminder.triggerType)
    val transition = PlaceTransition.fromString(reminder.placeTransition)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (reminder.isCompleted) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleComplete)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (reminder.isCompleted) MaterialTheme.colorScheme.secondary
                            else Color.Transparent
                        )
                        .border(
                            2.dp,
                            if (reminder.isCompleted) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.tertiary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (reminder.isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (reminder.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (reminder.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (triggerType == ReminderTriggerType.TIME || triggerType == ReminderTriggerType.TIME_AND_LOCATION) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = "Time trigger",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                val timeText = buildString {
                                    if (reminder.triggerHour in 0..23) {
                                        append(DateStreakUtils.formatTime12h(reminder.triggerHour, reminder.triggerMinute))
                                    } else {
                                        append("Scheduled")
                                    }
                                    if (reminder.repeatDaysCsv.isNotBlank()) {
                                        append(" • ")
                                        append(DateStreakUtils.formatDaysSummary(reminder.repeatDaysCsv))
                                    } else if (reminder.triggerDateKey.isNotBlank()) {
                                        append(" • ")
                                        append(reminder.triggerDateKey)
                                    }
                                }
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (place != null && (triggerType == ReminderTriggerType.LOCATION || triggerType == ReminderTriggerType.TIME_AND_LOCATION)) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = "Location trigger",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${transition.label} ${place.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (assignee != null) {
                        Text(
                            text = assignee.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_reminder_${reminder.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete reminder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AllDoneBotanicalCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_all_done_botanical_1795485488768),
                contentDescription = "All tasks complete botanical illustration",
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
