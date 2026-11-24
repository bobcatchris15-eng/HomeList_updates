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
import androidx.compose.runtime.Composable
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
import com.example.mesh.DiscoveredMeshNode
import com.example.mesh.MeshSyncEngine
import com.example.ui.theme.MemberPaletteMap

private enum class OnboardingMode {
    WELCOME,
    FOUND,
    JOIN
}

@Composable
fun OnboardingScreen(
    discoveredNodes: List<DiscoveredMeshNode>,
    isJoining: Boolean,
    isTablet: Boolean,
    onFoundHousehold: (householdName: String, displayName: String, colorKey: String, isTablet: Boolean) -> Unit,
    onJoinHousehold: (address: String, code: String, displayName: String, role: MemberRole, colorKey: String, isTablet: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(OnboardingMode.WELCOME) }

    BackHandler(enabled = mode != OnboardingMode.WELCOME) {
        mode = OnboardingMode.WELCOME
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
            when (mode) {
                OnboardingMode.WELCOME -> {
                    WelcomeHeroSection(
                        discoveredCount = discoveredNodes.size,
                        onFoundClick = { mode = OnboardingMode.FOUND },
                        onJoinClick = { mode = OnboardingMode.JOIN }
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
                        discoveredNodes = discoveredNodes,
                        isJoining = isJoining,
                        isTablet = isTablet,
                        onBack = { mode = OnboardingMode.WELCOME },
                        onSubmit = onJoinHousehold
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeroSection(
    discoveredCount: Int,
    onFoundClick: () -> Unit,
    onJoinClick: () -> Unit
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
                title = "Pure phone-to-phone mesh",
                body = "No cloud accounts or PC required. Phones auto-sync on home Wi-Fi and privately over Tailscale."
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

    if (discoveredCount > 0) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = "Wi-Fi peer found",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "$discoveredCount HomeList phone${if (discoveredCount == 1) "" else "s"} found on this Wi-Fi!",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    discoveredNodes: List<DiscoveredMeshNode>,
    isJoining: Boolean,
    isTablet: Boolean,
    onBack: () -> Unit,
    onSubmit: (String, String, String, MemberRole, String, Boolean) -> Unit
) {
    var peerAddress by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.CHILD) }
    var selectedColor by remember { mutableStateOf("sage") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Join a Family", style = MaterialTheme.typography.headlineMedium)
    }

    if (discoveredNodes.isNotEmpty()) {
        Text(
            text = "Nearby on Home Wi-Fi",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            discoveredNodes.forEach { node ->
                val isSelected = peerAddress == "${node.host}:${node.port}"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { peerAddress = "${node.host}:${node.port}" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.householdName.ifBlank { node.displayName },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${node.displayName} • ${node.host}:${node.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected peer",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = peerAddress,
        onValueChange = { input ->
            peerAddress = input
            val parsed = MeshSyncEngine.parsePairingUri(input)
            if (parsed != null) {
                peerAddress = "${parsed.first}:${parsed.second}"
                if (parsed.third.isNotBlank()) pairingCode = parsed.third
            }
        },
        label = { Text("Peer Wi-Fi IP, Tailscale IP, or pairing link") },
        placeholder = { Text("e.g., 192.168.1.42:8765 or 100.82.10.5:8765") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("join_address_input")
    )

    OutlinedTextField(
        value = pairingCode,
        onValueChange = { pairingCode = it.uppercase().take(8) },
        label = { Text("6-character pairing code") },
        placeholder = { Text("e.g., HL48K2") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("join_code_input")
    )

    OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("Your name") },
        placeholder = { Text("e.g., Maya, Leo, Dad") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("join_name_input")
    )

    Text("Your role on this phone", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = role == MemberRole.CHILD,
            onClick = { role = MemberRole.CHILD },
            label = { Text("Child") },
            modifier = Modifier.testTag("role_chip_child")
        )
        FilterChip(
            selected = role == MemberRole.PARENT,
            onClick = { role = MemberRole.PARENT },
            label = { Text("Parent") },
            modifier = Modifier.testTag("role_chip_parent")
        )
    }

    ColorPickerRow(selectedKey = selectedColor, onSelect = { selectedColor = it })

    Button(
        onClick = { onSubmit(peerAddress, pairingCode, displayName, role, selectedColor, isTablet) },
        enabled = !isJoining && peerAddress.isNotBlank() && pairingCode.length >= 4 && displayName.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("join_family_submit_button"),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isJoining) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Connecting to Mesh...")
        } else {
            Text("Connect & Join Family", style = MaterialTheme.typography.labelLarge)
        }
    }
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
