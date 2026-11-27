package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MemberRole
import com.example.mesh.HouseholdInvite
import com.example.ui.theme.MemberPaletteMap

private enum class OnboardingMode {
    WELCOME,
    FOUND,
    JOIN
}

@Composable
fun OnboardingScreen(
    inviteLink: String?,
    isJoining: Boolean,
    isTablet: Boolean,
    onFoundHousehold: (householdName: String, displayName: String, colorKey: String, isTablet: Boolean) -> Unit,
    onJoinHousehold: (link: String, displayName: String, role: MemberRole, colorKey: String, isTablet: Boolean) -> Unit,
    onRestoreBackup: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var mode by remember(inviteLink) {
        mutableStateOf(if (inviteLink == null) OnboardingMode.WELCOME else OnboardingMode.JOIN)
    }
    BackHandler(enabled = mode != OnboardingMode.WELCOME || onClose != null) {
        if (mode != OnboardingMode.WELCOME) mode = OnboardingMode.WELCOME else onClose?.invoke()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onClose != null) {
                TextButton(onClick = onClose, enabled = !isJoining) { Text("Back to household") }
            }
            when (mode) {
                OnboardingMode.WELCOME -> {
                    WelcomeHeroSection(
                        onFoundClick = {
                            mode = OnboardingMode.FOUND
                        },
                        onJoinClick = { mode = OnboardingMode.JOIN },
                        onRestoreBackup = onRestoreBackup
                    )
                }

                OnboardingMode.FOUND -> {
                    FoundGroupingSection(
                        isTablet = isTablet,
                        onBack = { mode = OnboardingMode.WELCOME },
                        onSubmit = onFoundHousehold
                    )
                }

                OnboardingMode.JOIN -> {
                    JoinGroupingSection(
                        inviteLink = inviteLink,
                        isJoining = isJoining,
                        isTablet = isTablet,
                        onBack = {
                            mode = OnboardingMode.WELCOME
                        },
                        onSwitchToFound = {
                            mode = OnboardingMode.FOUND
                        },
                        onSubmit = onJoinHousehold
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeroSection(
    onFoundClick: () -> Unit,
    onJoinClick: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    Spacer(modifier = Modifier.height(6.dp))
    Image(
        painter = painterResource(id = R.drawable.img_onboarding_home_1795485489486),
        contentDescription = "Cozy family kitchen with chore board",
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(26.dp)),
        contentScale = ContentScale.Crop
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "HomeList",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureBullet(
                title = "Encrypted sync between household devices",
                body = "Share an invite link to pair devices. HomeList syncs on Wi-Fi and mobile data."
            )
            FeatureBullet(
                title = "Parent & Child sides",
                body = "Parents organize chores, events, and nudges; kids check off tasks and grow daily streaks."
            )
            FeatureBullet(
                title = "Time & place reminders + calendar",
                body = "Trigger reminders by schedule or saved places like Home & School, with optional Google Calendar sync."
            )
        }
    }

    Button(
        onClick = onFoundClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("found_family_button"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Filled.Home, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.found_grouping), style = MaterialTheme.typography.labelLarge)
    }

    OutlinedButton(
        onClick = onJoinClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("join_family_button"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.join_grouping), style = MaterialTheme.typography.labelLarge)
    }

    TextButton(
        onClick = onRestoreBackup,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_restore_backup_button")
    ) {
        Text("Restore from a saved family backup (.json)")
    }
}

@Composable
private fun FeatureBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FoundGroupingSection(
    isTablet: Boolean,
    onBack: () -> Unit,
    onSubmit: (String, String, String, Boolean) -> Unit
) {
    var householdName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("terracotta") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Found a Family", style = MaterialTheme.typography.headlineMedium)
    }

    Text(
        text = "Your phone becomes the founding Parent node and generates a 6-character pairing code for the rest of the family.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = householdName,
        onValueChange = { householdName = it },
        label = { Text("Household name") },
        placeholder = { Text("e.g., The Miller Home") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("household_name_input")
    )

    OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("Your name") },
        placeholder = { Text("e.g., Mom, Dad, Chris") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("founder_name_input")
    )

    ColorPickerRow(selectedKey = selectedColor, onSelect = { selectedColor = it })

    Button(
        onClick = { onSubmit(householdName, displayName, selectedColor, isTablet) },
        enabled = householdName.isNotBlank() && displayName.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("create_family_submit_button"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Start Family Mesh", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun JoinGroupingSection(
    inviteLink: String?,
    isJoining: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onSwitchToFound: () -> Unit,
    onSubmit: (String, String, MemberRole, String, Boolean) -> Unit
) {
    var link by remember(inviteLink) { mutableStateOf(inviteLink.orEmpty()) }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.CHILD) }
    var colorKey by remember { mutableStateOf("terracotta") }
    val invite = remember(link) { HouseholdInvite.parse(link) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = !isJoining) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Join a household", style = MaterialTheme.typography.headlineSmall)
    }
    OutlinedTextField(
        value = link, onValueChange = { link = it }, singleLine = true, enabled = !isJoining,
        label = { Text("HomeList invite link") }, modifier = Modifier.fillMaxWidth().testTag("join_peer_address")
    )
    invite?.householdName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    OutlinedTextField(
        value = displayName, onValueChange = { displayName = it }, singleLine = true, enabled = !isJoining,
        label = { Text("Your display name") }, modifier = Modifier.fillMaxWidth().testTag("join_display_name")
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MemberRole.entries.forEach { option ->
            FilterChip(selected = role == option, onClick = { role = option }, label = { Text(option.label) })
        }
    }
    ColorPickerRow(selectedKey = colorKey, onSelect = { colorKey = it })
    Button(
        onClick = { onSubmit(link, displayName, role, colorKey, isTablet) },
        enabled = !isJoining && invite != null && displayName.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("join_household_button")
    ) {
        if (isJoining) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text("Join Household")
    }
    TextButton(onClick = onSwitchToFound, enabled = !isJoining) { Text("Create a household instead") }
}

@Composable
fun ColorPickerRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Badge color",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberPaletteMap.forEach { (key, color) ->
                val selected = key == selectedKey
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onSelect(key) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected $key",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
