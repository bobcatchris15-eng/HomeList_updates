package com.example.data

import com.example.BuildConfig
import com.example.mesh.MeshGossipPayload
import com.example.mesh.NetworkAddressDetector
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.UUID

data class GossipMergeResult(
    val newReminders: List<ReminderEntity> = emptyList(),
    val fulfilledShoppingItems: List<GroceryItemEntity> = emptyList()
)

class HomeListRepository(private val dao: HomeListDao) {
    val configFlow: Flow<NodeConfigEntity?> = dao.observeConfig()
    val peersFlow: Flow<List<MeshPeerEntity>> = dao.observePeers()
    val membersFlow: Flow<List<MemberEntity>> = dao.observeActiveMembers()
    val choresFlow: Flow<List<ChoreEntity>> = dao.observeActiveChores()
    val completionsFlow: Flow<List<CompletionEntity>> = dao.observeAllCompletions()
    val placesFlow: Flow<List<SavedPlaceEntity>> = dao.observeActivePlaces()
    val remindersFlow: Flow<List<ReminderEntity>> = dao.observeActiveReminders()
    val eventsFlow: Flow<List<CalendarEventEntity>> = dao.observeActiveEvents()
    val dinnerOptionsFlow: Flow<List<DinnerOptionEntity>> = dao.observeActiveDinnerOptions()
    val groceryItemsFlow: Flow<List<GroceryItemEntity>> = dao.observeActiveGroceryItems()

    suspend fun getConfig(): NodeConfigEntity? = dao.getConfig()
    suspend fun getAllPeers(): List<MeshPeerEntity> = dao.getAllPeers()
    suspend fun getAllMembers(): List<MemberEntity> = dao.getAllMembers()
    suspend fun getAllChores(): List<ChoreEntity> = dao.getAllChores()
    suspend fun getAllCompletions(): List<CompletionEntity> = dao.getAllCompletions()
    suspend fun getAllPlaces(): List<SavedPlaceEntity> = dao.getAllPlaces()
    suspend fun getAllReminders(): List<ReminderEntity> = dao.getAllReminders()
    suspend fun getAllEvents(): List<CalendarEventEntity> = dao.getAllEvents()
    suspend fun getAllDinnerOptions(): List<DinnerOptionEntity> = dao.getAllDinnerOptions()
    suspend fun getAllGroceryItems(): List<GroceryItemEntity> = dao.getAllGroceryItems()

    suspend fun foundHousehold(
        householdName: String,
        displayName: String,
        avatarColorKey: String,
        deviceType: DeviceType
    ): NodeConfigEntity {
        val now = System.currentTimeMillis()
        val nodeId = UUID.randomUUID().toString()
        val householdId = UUID.randomUUID().toString()
        val memberId = UUID.randomUUID().toString()
        val pairingCode = generatePairingCode()
        val addresses = NetworkAddressDetector.detect()

        val config = NodeConfigEntity(
            nodeId = nodeId,
            householdId = householdId,
            householdName = householdName.trim(),
            pairingCode = pairingCode,
            selfMemberId = memberId,
            selfRole = MemberRole.PARENT.name,
            deviceType = deviceType.name,
            isFounder = true,
            acceptPrivateCheckIns = true,
            checkInIntervalMinutes = 15,
            listenPort = 8765,
            lastSyncedAt = now
        )
        dao.upsertConfig(config)

        val selfMember = MemberEntity(
            id = memberId,
            displayName = displayName.trim(),
            role = MemberRole.PARENT.name,
            avatarColorKey = avatarColorKey,
            updatedAt = now
        )
        dao.upsertMember(selfMember)

        val selfPeer = MeshPeerEntity(
            nodeId = nodeId,
            memberId = memberId,
            displayName = displayName.trim(),
            role = MemberRole.PARENT.name,
            deviceType = deviceType.name,
            isFounder = true,
            lanIp = addresses.lanIp,
            tailscaleIp = addresses.tailscaleIp,
            port = config.listenPort,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            lastSeenAt = now,
            updatedAt = now
        )
        dao.upsertPeer(selfPeer)

        // Seed a helpful starter Home place so Wi-Fi arrival reminders work immediately
        dao.upsertPlace(
            SavedPlaceEntity(
                id = UUID.randomUUID().toString(),
                name = "Home",
                latitude = 0.0,
                longitude = 0.0,
                radiusMeters = 200,
                isHomePlace = true,
                updatedAt = now
            )
        )

        return config
    }

    suspend fun joinHousehold(
        householdId: String,
        householdName: String,
        pairingCode: String,
        displayName: String,
        role: MemberRole,
        avatarColorKey: String,
        deviceType: DeviceType,
        initialPayload: MeshGossipPayload?
    ): NodeConfigEntity {
        val now = System.currentTimeMillis()
        val nodeId = UUID.randomUUID().toString()
        val memberId = UUID.randomUUID().toString()
        val addresses = NetworkAddressDetector.detect()

        val config = NodeConfigEntity(
            nodeId = nodeId,
            householdId = householdId,
            householdName = householdName.trim().ifEmpty { "Our Family" },
            pairingCode = pairingCode.trim().uppercase(),
            selfMemberId = memberId,
            selfRole = role.name,
            deviceType = deviceType.name,
            isFounder = false,
            acceptPrivateCheckIns = true,
            checkInIntervalMinutes = 15,
            listenPort = 8765,
            lastSyncedAt = now
        )
        dao.upsertConfig(config)

        val selfMember = MemberEntity(
            id = memberId,
            displayName = displayName.trim(),
            role = role.name,
            avatarColorKey = avatarColorKey,
            updatedAt = now
        )
        dao.upsertMember(selfMember)

        val selfPeer = MeshPeerEntity(
            nodeId = nodeId,
            memberId = memberId,
            displayName = displayName.trim(),
            role = role.name,
            deviceType = deviceType.name,
            isFounder = false,
            lanIp = addresses.lanIp,
            tailscaleIp = addresses.tailscaleIp,
            port = config.listenPort,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            lastSeenAt = now,
            updatedAt = now
        )
        dao.upsertPeer(selfPeer)

        if (initialPayload != null) {
            mergeGossipPayload(initialPayload)
        }
        return config
    }

    suspend fun refreshSelfPeerAddresses(): MeshPeerEntity? {
        val config = dao.getConfig() ?: return null
        val addresses = NetworkAddressDetector.detect()
        val selfMember = dao.getAllMembers().firstOrNull { it.id == config.selfMemberId }
        val now = System.currentTimeMillis()
        val peer = MeshPeerEntity(
            nodeId = config.nodeId,
            memberId = config.selfMemberId,
            displayName = selfMember?.displayName ?: "This Device",
            role = config.selfRole,
            deviceType = config.deviceType,
            isFounder = config.isFounder,
            lanIp = addresses.lanIp,
            tailscaleIp = addresses.tailscaleIp,
            customPrivateHost = config.customPrivateHost,
            port = config.listenPort,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            lastSeenAt = now,
            updatedAt = now
        )
        dao.upsertPeer(peer)
        return peer
    }

    suspend fun updateConfig(transform: (NodeConfigEntity) -> NodeConfigEntity): NodeConfigEntity? {
        val current = dao.getConfig() ?: return null
        val updated = transform(current)
        dao.upsertConfig(updated)
        refreshSelfPeerAddresses()
        return updated
    }

    suspend fun updateSelfProfile(displayName: String, avatarColorKey: String) {
        val config = dao.getConfig() ?: return
        val existing = dao.getAllMembers().firstOrNull { it.id == config.selfMemberId } ?: return
        val now = System.currentTimeMillis()
        dao.upsertMember(
            existing.copy(
                displayName = displayName.trim(),
                avatarColorKey = avatarColorKey,
                updatedAt = now
            )
        )
        refreshSelfPeerAddresses()
    }

    suspend fun addManualMember(displayName: String, role: MemberRole, avatarColorKey: String): MemberEntity {
        val member = MemberEntity(
            id = UUID.randomUUID().toString(),
            displayName = displayName.trim(),
            role = role.name,
            avatarColorKey = avatarColorKey,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertMember(member)
        return member
    }

    suspend fun saveChore(chore: ChoreEntity) {
        dao.upsertChore(chore.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteChore(chore: ChoreEntity) {
        dao.upsertChore(chore.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleChoreCompletion(
        choreId: String,
        dateKey: String,
        completedByMemberId: String
    ): Boolean {
        val key = CompletionEntity.key(choreId, dateKey)
        val existing = dao.getCompletionById(key)
        val newCompleted = !(existing?.isCompleted ?: false)
        dao.upsertCompletion(
            CompletionEntity(
                id = key,
                choreId = choreId,
                dateKey = dateKey,
                completedByMemberId = completedByMemberId,
                isCompleted = newCompleted,
                updatedAt = System.currentTimeMillis()
            )
        )
        return newCompleted
    }

    suspend fun setChoreCompleted(
        choreId: String,
        dateKey: String,
        completedByMemberId: String,
        completed: Boolean
    ) {
        val key = CompletionEntity.key(choreId, dateKey)
        dao.upsertCompletion(
            CompletionEntity(
                id = key,
                choreId = choreId,
                dateKey = dateKey,
                completedByMemberId = completedByMemberId,
                isCompleted = completed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun savePlace(place: SavedPlaceEntity) {
        dao.upsertPlace(place.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlace(place: SavedPlaceEntity) {
        dao.upsertPlace(place.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveReminder(reminder: ReminderEntity) {
        dao.upsertReminder(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleReminderCompleted(reminder: ReminderEntity) {
        dao.upsertReminder(
            reminder.copy(
                isCompleted = !reminder.isCompleted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markReminderFired(reminder: ReminderEntity) {
        val now = System.currentTimeMillis()
        dao.upsertReminder(reminder.copy(lastFiredAt = now, updatedAt = now))
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        dao.upsertReminder(reminder.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveCalendarEvent(event: CalendarEventEntity) {
        dao.upsertEvent(event.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCalendarEvent(event: CalendarEventEntity) {
        dao.upsertEvent(event.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    // Flexible Meal Requests & Voting
    suspend fun proposeDinnerOption(
        dateKey: String,
        mealName: String,
        mealSlot: MealSlot,
        notes: String,
        memberId: String,
        memberName: String
    ) {
        val now = System.currentTimeMillis()
        dao.upsertDinnerOption(
            DinnerOptionEntity(
                id = UUID.randomUUID().toString(),
                dateKey = dateKey,
                mealName = mealName.trim(),
                mealSlot = mealSlot.name,
                notes = notes.trim(),
                proposedByMemberId = memberId,
                proposedByName = memberName,
                voterIdsCsv = memberId,
                isWinner = false,
                updatedAt = now
            )
        )
    }

    suspend fun updateDinnerOption(option: DinnerOptionEntity) {
        dao.upsertDinnerOption(
            option.copy(
                mealName = option.mealName.trim(),
                notes = option.notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleDinnerVote(option: DinnerOptionEntity, voterId: String) {
        val voters = option.voterIds().toMutableSet()
        if (voterId in voters) voters.remove(voterId) else voters.add(voterId)
        dao.upsertDinnerOption(
            option.copy(
                voterIdsCsv = voters.sorted().joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setDinnerWinner(option: DinnerOptionEntity) {
        val now = System.currentTimeMillis()
        val sameDayAndSlot = dao.getAllDinnerOptions().filter {
            !it.isDeleted && it.dateKey == option.dateKey && it.mealSlot == option.mealSlot
        }
        val updated = sameDayAndSlot.map { item ->
            val shouldWin = item.id == option.id && !option.isWinner
            item.copy(isWinner = shouldWin, updatedAt = now)
        }
        dao.upsertDinnerOptions(updated)
    }

    suspend fun deleteDinnerOption(option: DinnerOptionEntity) {
        dao.upsertDinnerOption(option.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    // Plain Synced Shopping List (with check-off attribution to notify requestor on sync)
    suspend fun addGroceryItem(
        name: String,
        quantity: String = "",
        memberId: String,
        memberName: String
    ) {
        dao.upsertGroceryItem(
            GroceryItemEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                quantity = quantity.trim(),
                addedByMemberId = memberId,
                addedByName = memberName,
                isChecked = false,
                checkedByMemberId = "",
                checkedByName = "",
                checkedAt = 0L,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleGroceryChecked(
        item: GroceryItemEntity,
        memberId: String,
        memberName: String
    ) {
        val now = System.currentTimeMillis()
        val nowChecked = !item.isChecked
        dao.upsertGroceryItem(
            item.copy(
                isChecked = nowChecked,
                checkedByMemberId = if (nowChecked) memberId else "",
                checkedByName = if (nowChecked) memberName else "",
                checkedAt = if (nowChecked) now else 0L,
                updatedAt = now
            )
        )
    }

    suspend fun deleteGroceryItem(item: GroceryItemEntity) {
        dao.upsertGroceryItem(item.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearCheckedGroceries() {
        val now = System.currentTimeMillis()
        val toClear = dao.getAllGroceryItems().filter { !it.isDeleted && it.isChecked }
        if (toClear.isNotEmpty()) {
            dao.upsertGroceryItems(toClear.map { it.copy(isDeleted = true, updatedAt = now) })
        }
    }

    suspend fun upsertImportedEvents(events: List<CalendarEventEntity>) {
        if (events.isEmpty()) return
        val existingByExt = dao.getAllEvents()
            .filter { it.externalEventId.isNotBlank() }
            .associateBy { "${it.source}:${it.externalEventId}" }

        val merged = events.map { incoming ->
            val key = "${incoming.source}:${incoming.externalEventId}"
            val match = existingByExt[key]
            if (match != null) {
                incoming.copy(id = match.id, updatedAt = System.currentTimeMillis())
            } else {
                incoming
            }
        }
        dao.upsertEvents(merged)
    }

    suspend fun buildGossipPayload(): MeshGossipPayload? {
        val config = dao.getConfig() ?: return null
        refreshSelfPeerAddresses()
        return MeshGossipPayload(
            protocolVersion = 1,
            householdId = config.householdId,
            householdName = config.householdName,
            senderNodeId = config.nodeId,
            sentAt = System.currentTimeMillis(),
            peers = dao.getAllPeers(),
            members = dao.getAllMembers(),
            chores = dao.getAllChores(),
            completions = dao.getAllCompletions(),
            places = dao.getAllPlaces(),
            reminders = dao.getAllReminders(),
            events = dao.getAllEvents(),
            dinnerOptions = dao.getAllDinnerOptions(),
            groceryItems = dao.getAllGroceryItems(),
            updateReleaseUrl = config.updateReleaseUrl
        )
    }

    /**
     * Last-Write-Wins (LWW) associative CRDT merge across all synced tables.
     * Returns newly received uncompleted reminders addressed to this device AND
     * shopping items requested by this user that another household member checked off.
     */
    suspend fun mergeGossipPayload(payload: MeshGossipPayload): GossipMergeResult {
        val config = dao.getConfig()
        val selfMemberId = config?.selfMemberId.orEmpty()
        val now = System.currentTimeMillis()

        // 1. Peers (Peer Address Book)
        val localPeers = dao.getAllPeers().associateBy { it.nodeId }.toMutableMap()
        payload.peers.forEach { incoming ->
            val existing = localPeers[incoming.nodeId]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                val mergedSeen = maxOf(existing?.lastSeenAt ?: 0L, incoming.lastSeenAt)
                localPeers[incoming.nodeId] = incoming.copy(
                    lastSeenAt = if (incoming.nodeId == payload.senderNodeId) now else mergedSeen
                )
            } else if (incoming.nodeId == payload.senderNodeId) {
                localPeers[incoming.nodeId] = existing.copy(lastSeenAt = now)
            }
        }
        dao.upsertPeers(localPeers.values.toList())

        // 2. Members
        val localMembers = dao.getAllMembers().associateBy { it.id }.toMutableMap()
        payload.members.forEach { incoming ->
            val existing = localMembers[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localMembers[incoming.id] = incoming
            }
        }
        dao.upsertMembers(localMembers.values.toList())

        // 3. Chores
        val localChores = dao.getAllChores().associateBy { it.id }.toMutableMap()
        payload.chores.forEach { incoming ->
            val existing = localChores[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localChores[incoming.id] = incoming
            }
        }
        dao.upsertChores(localChores.values.toList())

        // 4. Completions
        val localCompletions = dao.getAllCompletions().associateBy { it.id }.toMutableMap()
        payload.completions.forEach { incoming ->
            val existing = localCompletions[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localCompletions[incoming.id] = incoming
            }
        }
        dao.upsertCompletions(localCompletions.values.toList())

        // 5. Saved Places
        val localPlaces = dao.getAllPlaces().associateBy { it.id }.toMutableMap()
        payload.places.forEach { incoming ->
            val existing = localPlaces[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localPlaces[incoming.id] = incoming
            }
        }
        dao.upsertPlaces(localPlaces.values.toList())

        // 6. Reminders & Parent Nudges (track brand-new nudges/reminders for this user)
        val localReminders = dao.getAllReminders().associateBy { it.id }.toMutableMap()
        val newlyArrivedForSelf = mutableListOf<ReminderEntity>()
        payload.reminders.forEach { incoming ->
            val existing = localReminders[incoming.id]
            if (existing == null) {
                localReminders[incoming.id] = incoming
                if (!incoming.isDeleted &&
                    !incoming.isCompleted &&
                    incoming.createdByMemberId != selfMemberId &&
                    (incoming.assignedToMemberId == null || incoming.assignedToMemberId == selfMemberId)
                ) {
                    newlyArrivedForSelf.add(incoming)
                }
            } else if (incoming.updatedAt > existing.updatedAt) {
                localReminders[incoming.id] = incoming
            }
        }
        dao.upsertReminders(localReminders.values.toList())

        // 7. Calendar Events
        val localEvents = dao.getAllEvents().associateBy { it.id }.toMutableMap()
        payload.events.forEach { incoming ->
            val existing = localEvents[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localEvents[incoming.id] = incoming
            }
        }
        dao.upsertEvents(localEvents.values.toList())

        // 8. Dinner Requests & Votes
        val localDinners = dao.getAllDinnerOptions().associateBy { it.id }.toMutableMap()
        payload.dinnerOptions.forEach { incoming ->
            val existing = localDinners[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                localDinners[incoming.id] = incoming
            }
        }
        dao.upsertDinnerOptions(localDinners.values.toList())

        // 9. Synced Shopping List (detect items requested by this member that someone else checked off)
        val localGroceries = dao.getAllGroceryItems().associateBy { it.id }.toMutableMap()
        val newlyFulfilledShopping = mutableListOf<GroceryItemEntity>()
        payload.groceryItems.forEach { incoming ->
            val existing = localGroceries[incoming.id]
            val shouldAdopt = existing == null || incoming.updatedAt > existing.updatedAt
            if (shouldAdopt) {
                val wasUncheckedLocally = existing == null || !existing.isChecked
                if (selfMemberId.isNotBlank() &&
                    wasUncheckedLocally &&
                    incoming.isChecked &&
                    incoming.addedByMemberId == selfMemberId &&
                    incoming.checkedByMemberId.isNotBlank() &&
                    incoming.checkedByMemberId != selfMemberId
                ) {
                    newlyFulfilledShopping.add(incoming)
                }
                localGroceries[incoming.id] = incoming
            }
        }
        dao.upsertGroceryItems(localGroceries.values.toList())

        if (config != null) {
            dao.upsertConfig(
                config.copy(
                    householdName = payload.householdName.ifBlank { config.householdName },
                    updateReleaseUrl = payload.updateReleaseUrl.ifBlank { config.updateReleaseUrl },
                    lastSyncedAt = now
                )
            )
        }
        return GossipMergeResult(
            newReminders = newlyArrivedForSelf,
            fulfilledShoppingItems = newlyFulfilledShopping
        )
    }

    suspend fun leaveHousehold() {
        dao.clearConfig()
        dao.clearPeers()
        dao.clearMembers()
        dao.clearChores()
        dao.clearCompletions()
        dao.clearPlaces()
        dao.clearReminders()
        dao.clearEvents()
        dao.clearDinnerOptions()
        dao.clearGroceryItems()
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun generatePairingCode(): String {
            return buildString(6) {
                repeat(6) {
                    append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
                }
            }
        }
    }
}
