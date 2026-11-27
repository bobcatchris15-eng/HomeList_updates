package com.example.mesh

import com.example.data.CalendarEventEntity
import com.example.data.ChoreEntity
import com.example.data.CompletionEntity
import com.example.data.DinnerOptionEntity
import com.example.data.GroceryItemEntity
import com.example.data.MemberEntity
import com.example.data.MeshPeerEntity
import com.example.data.HouseholdSession
import com.example.data.ReminderEntity
import com.example.data.SavedPlaceEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeshGossipPayload(
    val protocolVersion: Int = 2,
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
    val hubUrl: String = "",
    val senderEndpointId: String = "",
    val hubEndpointId: String = ""
)

@JsonClass(generateAdapter = true)
data class GossipRequestEnvelope(
    val protocolVersion: Int = 2,
    val householdId: String,
    val pairingCode: String,
    val payload: MeshGossipPayload? = null
)

@JsonClass(generateAdapter = true)
data class GossipResponseEnvelope(
    val ok: Boolean,
    val error: String? = null,
    val payload: MeshGossipPayload? = null
)

@JsonClass(generateAdapter = true)
data class HouseholdBackupSnapshot(
    val backupVersion: Int = 1,
    val exportedAt: Long,
    val config: HouseholdSession,
    val peers: List<MeshPeerEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val chores: List<ChoreEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList(),
    val places: List<SavedPlaceEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val dinnerOptions: List<DinnerOptionEntity> = emptyList(),
    val groceryItems: List<GroceryItemEntity> = emptyList()
)

data class MeshRuntimeStatus(
    val isOnline: Boolean = false,
    val connectedHubs: Set<String> = emptySet(),
    val isSyncing: Boolean = false,
    val lastSyncMessage: String = "Ready"
)
