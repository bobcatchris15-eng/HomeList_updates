package com.example.mesh

import com.example.data.CalendarEventEntity
import com.example.data.ChoreEntity
import com.example.data.CompletionEntity
import com.example.data.DinnerOptionEntity
import com.example.data.GroceryItemEntity
import com.example.data.MemberEntity
import com.example.data.MeshPeerEntity
import com.example.data.ReminderEntity
import com.example.data.SavedPlaceEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeshGossipPayload(
    val protocolVersion: Int = 1,
    val householdId: String,
    val householdName: String,
    val senderNodeId: String,
    val sentAt: Long,
    val peers: List<MeshPeerEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val chores: List<ChoreEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList(),
    val places: List<SavedPlaceEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val dinnerOptions: List<DinnerOptionEntity> = emptyList(),
    val groceryItems: List<GroceryItemEntity> = emptyList(),
    val updateReleaseUrl: String = "",
    val hubUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class NodeHealthPayload(
    val app: String = "HomeList",
    val protocolVersion: Int = 1,
    val nodeId: String,
    val householdId: String,
    val householdName: String,
    val displayName: String,
    val deviceType: String,
    val isFounder: Boolean,
    val port: Int,
    val appVersionCode: Int = 1,
    val appVersionName: String = "1.0"
)

@JsonClass(generateAdapter = true)
data class WallMemberSummary(
    val displayName: String,
    val role: String,
    val colorKey: String,
    val todayCompleted: Int,
    val todayTotal: Int,
    val currentStreak: Int,
    val weekCells: List<String> // "done", "partial", "todo", "none" for Mon..Sun
)

@JsonClass(generateAdapter = true)
data class WallEventSummary(
    val dateKey: String,
    val title: String,
    val timeLabel: String,
    val location: String
)

@JsonClass(generateAdapter = true)
data class WallSnapshotPayload(
    val householdName: String,
    val generatedAt: Long,
    val todayDateLabel: String,
    val dayHeaders: List<String>,
    val overallCompleted: Int,
    val overallTotal: Int,
    val peerCount: Int,
    val tonightDinner: String = "",
    val members: List<WallMemberSummary>,
    val upcomingEvents: List<WallEventSummary>
)

data class DiscoveredMeshNode(
    val serviceName: String,
    val host: String,
    val port: Int,
    val householdName: String = "",
    val householdId: String = "",
    val displayName: String = "",
    val deviceType: String = "PHONE",
    val isFounder: Boolean = false
)

data class MeshRuntimeStatus(
    val serverRunning: Boolean = false,
    val listenPort: Int = 8765,
    val lanIp: String = "",
    val activeWifiPeers: Int = 0,
    val hubConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String = "Ready",
    val lastSyncTimeMs: Long = 0L
)
