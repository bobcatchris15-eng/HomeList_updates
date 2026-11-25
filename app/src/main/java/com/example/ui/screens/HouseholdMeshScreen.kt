package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.DateStreakUtils
import com.example.data.DeviceType
import com.example.data.MemberRole
import com.example.ui.HomeListUiState
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.iconForDeviceType
import com.example.updates.AppUpdateChecker
import com.example.updates.NightlyUpdateScheduler

@Composable
fun HouseholdMeshScreen(
    state: HomeListUiState,
    onSyncMeshNow: () -> Unit,
    onAddManualMember: (name: String, role: MemberRole, colorKey: String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpdateReleaseUrl: (String) -> Unit,
    onUpdateHubUrl: (String) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onLeaveHousehold: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = state.config ?: return
    val mesh = state.meshStatus

    var releaseUrlDraft by remember(config.updateReleaseUrl) {
        mutableStateOf(config.updateReleaseUrl.ifBlank { AppUpdateChecker.DEFAULT_RELEASES_URL })
    }
    var hubUrlDraft by remember(config.hubUrl) {
        mutableStateOf(config.hubUrl)
    }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val pairingLink = if (mesh.lanIp.isNotBlank()) {
        "homelist://${mesh.lanIp}:${mesh.listenPort}?code=${config.pairingCode}"
    } else {
        config.pairingCode
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pairing Code & Mesh Identity Card
        item(key = "pairing_card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(config.householdName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = if (config.isFounder) "Founder Node • Pure Peer Mesh" else "Family Mesh Peer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("HomeList Pairing Code", config.pairingCode))
                                    onShowMessage("Copied pairing code: ${config.pairingCode}")
                                }
                                .testTag("pairing_code_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.VpnKey,
                                    contentDescription = "Pairing code",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = config.pairingCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Tap to copy pairing code",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    AddressRow(
                        label = "Home Wi-Fi IP",
                        value = if (mesh.lanIp.isNotBlank()) "${mesh.lanIp}:${mesh.listenPort}" else "Connect to Wi-Fi"
                    )
                    if (mesh.lanIp.isNotBlank()) {
                        AddressRow(
                            label = "Pi / Browser Wall URL",
                            value = "http://${mesh.lanIp}:${mesh.listenPort}"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("HomeList Pairing Code", config.pairingCode))
                                onShowMessage("Copied pairing code: ${config.pairingCode}")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_pairing_code_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Code")
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Join ${config.householdName} on HomeList")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join ${config.householdName} in HomeList!\nPairing code: ${config.pairingCode}\nLink: $pairingLink"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share HomeList Invite"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_pairing_code_button")
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Invite")
                        }
                    }
                }
            }
        }

        // Synced Peer Address Book (Wi-Fi Mesh Directory)
        item(key = "peer_book_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Peer Address Book (${state.peers.size})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = mesh.lastSyncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onSyncMeshNow,
                    enabled = !mesh.isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sync_mesh_now_button")
                ) {
                    Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (mesh.isSyncing) "Syncing..." else "Sync Mesh")
                }
            }
        }

        items(state.peers, key = { it.nodeId }) { peer ->
            val isSelf = peer.nodeId == config.nodeId
            val devType = DeviceType.fromString(peer.deviceType)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = iconForDeviceType(peer.deviceType),
                        contentDescription = devType.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = peer.displayName + if (isSelf) " (This device)" else "",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (peer.isFounder) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Founder node",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = "${MemberRole.fromString(peer.role).label} • ${devType.label} • v${peer.appVersionName.ifBlank { "1.0" }} • Seen ${DateStreakUtils.relativeTimeLabel(peer.lastSeenAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (peer.lanIp.isNotBlank()) {
                            Text(
                                text = "Wi-Fi ${peer.lanIp}:${peer.port}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Background Sync & Optional Windows Home Hub
        item(key = "home_hub_card") {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Computer,
                                contentDescription = "Home Hub sync",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Background & Home Hub Sync", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Auto-syncs in background (~every 15 min)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (config.hubUrl.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (mesh.hubConnected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Text(
                                    text = if (mesh.hubConnected) "Hub Live" else "Hub Saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mesh.hubConnected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Optional: run the Windows Home Hub on a home PC so family phones stay synced even away from home. Set it once here and every family phone picks it up automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isParent) {
                        OutlinedTextField(
                            value = hubUrlDraft,
                            onValueChange = { hubUrlDraft = it },
                            label = { Text("Optional Home Hub URL") },
                            placeholder = { Text("https://hub.yourhome.com or 192.168.1.50:8765") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_hub_url_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onUpdateHubUrl(hubUrlDraft) },
                                enabled = hubUrlDraft.isNotBlank() && !mesh.isSyncing,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_home_hub_button")
                            ) {
                                Text("Save & Test Hub")
                            }
                            if (config.hubUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        hubUrlDraft = ""
                                        onUpdateHubUrl("")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("clear_home_hub_button")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    } else if (config.hubUrl.isNotBlank()) {
                        AddressRow(
                            label = "Away-from-home Hub",
                            value = "Auto-configured by household"
                        )
                    }
                }
            }
        }

        // Family Members & Add Member Option
        item(key = "family_members_card") {
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
                        Text("Family Members", style = MaterialTheme.typography.titleMedium)
                        if (state.isParent) {
                            TextButton(
                                onClick = { showAddMemberDialog = true },
                                modifier = Modifier.testTag("add_member_button")
                            ) {
                                Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Member")
                            }
                        }
                    }
                    state.members.forEach { member ->
                        val stats = state.memberStats[member.id]
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
                                    size = 34.dp
                                )
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = MemberRole.fromString(member.role).label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "${stats?.currentStreak ?: 0}d streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // App Updates (Nightly Auto-Check + GitHub Releases + Mesh Broadcast)
        item(key = "app_updates_card") {
            val update = state.availableUpdate
            val lastCheckedMs = remember(state.isCheckingUpdate, update) {
                NightlyUpdateScheduler.lastCheckedTimeMs(context)
            }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = "App updates",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("App Updates (GitHub)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (update != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v${update.versionName} ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = buildString {
                            append("Checks GitHub Releases automatically every night and sends a notification when a new update is ready.")
                            if (lastCheckedMs > 0L) {
                                append(" Last checked: ${DateStreakUtils.relativeTimeLabel(lastCheckedMs)}.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !state.isCheckingUpdate,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_updates_button")
                        ) {
                            Text(if (state.isCheckingUpdate) "Checking..." else "Check Now")
                        }
                        OutlinedButton(
                            onClick = {
                                val target = update?.downloadUrl ?: releaseUrlDraft
                                if (!AppUpdateChecker.openUpdateUrl(context, target)) {
                                    onShowMessage("Enter a valid https:// release URL")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_releases_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (update != null) "Get v${update.versionName}" else "Releases")
                        }
                    }

                    if (state.isParent) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = releaseUrlDraft,
                                onValueChange = { releaseUrlDraft = it },
                                label = { Text("GitHub Releases URL") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onUpdateReleaseUrl(releaseUrlDraft) },
                                modifier = Modifier.testTag("save_release_url_button")
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        // Appearance & Leave Household
        item(key = "preferences_card") {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = "Theme mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Evening Dark Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Softer dark slate palette for nighttime & wall boards",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.isDarkTheme,
                            onCheckedChange = { onToggleDarkTheme() },
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Family Backup & Restore", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Save a local backup file so you can restore your household anytime if you reinstall or switch phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onExportBackup,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_backup_button")
                            ) {
                                Text("Save Backup")
                            }
                            OutlinedButton(
                                onClick = onRestoreBackup,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("restore_backup_button")
                            ) {
                                Text("Restore File")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = { showLeaveConfirmDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leave_household_button")
                    ) {
                        Icon(Icons.Outlined.Logout, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave Family Grouping on This Phone")
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(MemberRole.CHILD) }
        var color by remember { mutableStateOf("sage") }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Family Member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add a family member now to start assigning chores before their phone joins the mesh.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = role == MemberRole.CHILD,
                            onClick = { role = MemberRole.CHILD },
                            label = { Text("Child") }
                        )
                        FilterChip(
                            selected = role == MemberRole.PARENT,
                            onClick = { role = MemberRole.PARENT },
                            label = { Text("Parent") }
                        )
                    }
                    ColorPickerRow(selectedKey = color, onSelect = { color = it })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddManualMember(name, role, color)
                        showAddMemberDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave ${config.householdName}?") },
            text = {
                Text("This clears HomeList data on this phone only. Other family phones keep their synced copies.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveHousehold()
                    }
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddressRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
