package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateStreakUtils
import com.example.data.DinnerOptionEntity
import com.example.data.MealSlot
import com.example.ui.HomeListUiState

@Composable
fun MealsScreen(
    state: HomeListUiState,
    onProposeMeal: (dateKey: String, mealName: String, mealSlot: MealSlot, notes: String) -> Unit,
    onUpdateMeal: (DinnerOptionEntity) -> Unit,
    onToggleMealVote: (DinnerOptionEntity) -> Unit,
    onSetMealWinner: (DinnerOptionEntity) -> Unit,
    onDeleteMealOption: (DinnerOptionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekSlots = remember { DateStreakUtils.weekSlots(0) }
    var selectedDateKey by remember { mutableStateOf(DateStreakUtils.todayKey()) }
    var selectedSlotFilter by remember { mutableStateOf<MealSlot?>(null) }
    var showMealDialog by remember { mutableStateOf(false) }
    var editingOption by remember { mutableStateOf<DinnerOptionEntity?>(null) }

    val selfId = state.config?.selfMemberId.orEmpty()
    val membersById = remember(state.members) { state.members.associateBy { it.id } }
    val selectedDay = weekSlots.firstOrNull { it.dateKey == selectedDateKey } ?: weekSlots.first()

    val dayOptions = remember(state.dinnerOptions, selectedDateKey, selectedSlotFilter) {
        state.dinnerOptions
            .filter { it.dateKey == selectedDateKey }
            .filter { selectedSlotFilter == null || it.slot() == selectedSlotFilter }
            .sortedWith(
                compareByDescending<DinnerOptionEntity> { it.isWinner }
                    .thenBy { it.slot().sortOrder }
                    .thenByDescending { it.voteCount() }
            )
    }
    val winners = remember(dayOptions) { dayOptions.filter { it.isWinner } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 7-Day Selector Strip
        item(key = "meal_day_strip") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekSlots.forEach { slot ->
                        val isSelected = slot.dateKey == selectedDateKey
                        val hasWinner = state.dinnerOptions.any { it.dateKey == slot.dateKey && it.isWinner }
                        val optionCount = state.dinnerOptions.count { it.dateKey == slot.dateKey }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        slot.isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { selectedDateKey = slot.dateKey }
                                .padding(vertical = 8.dp)
                                .testTag("dinner_day_${slot.dateKey}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            hasWinner -> MaterialTheme.colorScheme.secondary
                                            optionCount > 0 -> MaterialTheme.colorScheme.tertiary
                                            else -> Color.Transparent
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Flexible Meal Slot Filter Strip (All, Dinner, Lunch, Breakfast, Any Meal)
        item(key = "meal_slot_filter") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSlotFilter == null,
                    onClick = { selectedSlotFilter = null },
                    label = { Text("All Meals") }
                )
                listOf(MealSlot.DINNER, MealSlot.LUNCH, MealSlot.BREAKFAST, MealSlot.ANY).forEach { slot ->
                    FilterChip(
                        selected = selectedSlotFilter == slot,
                        onClick = { selectedSlotFilter = if (selectedSlotFilter == slot) null else slot },
                        label = { Text(slot.label) }
                    )
                }
            }
        }

        // Locked-In Meal Banner(s) for the selected day
        if (winners.isNotEmpty()) {
            item(key = "meal_winners_banner") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PLANNED • ${selectedDay.shortLabel.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        winners.forEach { winner ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${winner.slot().label}: ${winner.mealName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (winner.notes.isNotBlank()) {
                                    Text(
                                        text = winner.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Header + Request Meal CTA
        item(key = "meal_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedDay.isToday) "Today's Meal Requests"
                        else "${selectedDay.shortLabel}, ${selectedDay.monthShort} ${selectedDay.dayNumber} Meals",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Suggest any meal, vote, or adjust anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingOption = null
                        showMealDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("request_dinner_button")
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Request")
                }
            }
        }

        if (dayOptions.isEmpty()) {
            item(key = "meal_empty") {
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
                        Icon(
                            imageVector = Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text("No meal ideas yet", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Add ideas for dinner, lunch, or breakfast — anyone in the family can vote or swap days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                editingOption = null
                                showMealDialog = true
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Suggest a Meal")
                        }
                    }
                }
            }
        } else {
            items(dayOptions, key = { it.id }) { option ->
                val voters = option.voterIds()
                val hasVoted = selfId in voters
                val voterNames = voters.mapNotNull { membersById[it]?.displayName }.joinToString(", ")
                val canEdit = state.isParent || option.proposedByMemberId == selfId

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (option.isWinner) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (option.isWinner) 1.5.dp else 1.dp,
                        color = if (option.isWinner) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("dinner_option_${option.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleMealVote(option) }
                                .testTag("vote_dinner_${option.id}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (hasVoted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (hasVoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Vote for ${option.mealName}",
                                    tint = if (hasVoted) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = voters.size.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (hasVoted) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Text(
                                        text = option.slot().label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = option.mealName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (option.notes.isNotBlank()) {
                                Text(
                                    text = option.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = buildString {
                                    append("By ${option.proposedByName.ifBlank { "Family" }}")
                                    if (voterNames.isNotBlank()) {
                                        append(" • Votes: $voterNames")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (canEdit) {
                            IconButton(
                                onClick = {
                                    editingOption = option
                                    showMealDialog = true
                                },
                                modifier = Modifier.testTag("edit_meal_${option.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit meal",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.isParent) {
                            IconButton(
                                onClick = { onSetMealWinner(option) },
                                modifier = Modifier.testTag("pick_dinner_winner_${option.id}")
                            ) {
                                Icon(
                                    imageVector = if (option.isWinner) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Lock in meal",
                                    tint = if (option.isWinner) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (canEdit) {
                            IconButton(onClick = { onDeleteMealOption(option) }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove meal",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMealDialog) {
        val editing = editingOption
        var mealName by remember(editing) { mutableStateOf(editing?.mealName.orEmpty()) }
        var notes by remember(editing) { mutableStateOf(editing?.notes.orEmpty()) }
        var chosenSlot by remember(editing, selectedSlotFilter) {
            mutableStateOf(editing?.slot() ?: selectedSlotFilter ?: MealSlot.DINNER)
        }
        var chosenDateKey by remember(editing, selectedDateKey) {
            mutableStateOf(editing?.dateKey ?: selectedDateKey)
        }

        val quickIdeas = remember(chosenSlot) {
            when (chosenSlot) {
                MealSlot.BREAKFAST -> listOf("Pancakes", "Eggs & Toast", "Oatmeal", "Smoothies")
                MealSlot.LUNCH -> listOf("Sandwiches", "Quesadillas", "Soup & Grilled Cheese", "Leftovers")
                MealSlot.DINNER -> listOf("Taco Night", "Pizza", "Pasta", "Burgers", "Stir Fry", "Takeout")
                MealSlot.ANY -> listOf("Leftovers", "Picnic", "Snack Board", "Order In")
            }
        }

        AlertDialog(
            onDismissRequest = { showMealDialog = false },
            title = { Text(if (editing == null) "Request a Meal" else "Edit Meal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(MealSlot.DINNER, MealSlot.LUNCH, MealSlot.BREAKFAST, MealSlot.ANY).forEach { slot ->
                            FilterChip(
                                selected = chosenSlot == slot,
                                onClick = { chosenSlot = slot },
                                label = { Text(slot.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal") },
                        placeholder = { Text("e.g., Tacos, Leftovers, Takeout") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dinner_name_input")
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickIdeas.forEach { idea ->
                            FilterChip(
                                selected = mealName.equals(idea, ignoreCase = true),
                                onClick = { mealName = idea },
                                label = { Text(idea) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Note (optional)") },
                        placeholder = { Text("e.g., With salad, quick night") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editing != null) {
                        Text("Move to day", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            weekSlots.forEach { slot ->
                                FilterChip(
                                    selected = chosenDateKey == slot.dateKey,
                                    onClick = { chosenDateKey = slot.dateKey },
                                    label = { Text("${slot.shortLabel} ${slot.dayNumber}") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editing == null) {
                            onProposeMeal(chosenDateKey, mealName, chosenSlot, notes)
                        } else {
                            onUpdateMeal(
                                editing.copy(
                                    dateKey = chosenDateKey,
                                    mealName = mealName.trim(),
                                    mealSlot = chosenSlot.name,
                                    notes = notes.trim()
                                )
                            )
                        }
                        selectedDateKey = chosenDateKey
                        showMealDialog = false
                    },
                    enabled = mealName.isNotBlank(),
                    modifier = Modifier.testTag("save_dinner_request_button")
                ) { Text(if (editing == null) "Add" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showMealDialog = false }) { Text("Cancel") }
            }
        )
    }
}
