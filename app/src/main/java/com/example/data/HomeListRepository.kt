package com.example.data

import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.mesh.GossipRequestEnvelope
import com.example.mesh.GossipResponseEnvelope
import com.example.mesh.HouseholdBackupSnapshot
import com.example.mesh.MeshGossipPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class GossipMergeResult(
    val newReminders: List<ReminderEntity> = emptyList(),
    val fulfilledShoppingItems: List<GroceryItemEntity> = emptyList(),
    val householdName: String = ""
)

data class IncomingGossip(val response: GossipResponseEnvelope, val changes: GossipMergeResult? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeListRepository(private val database: HomeListDatabase) {
    private val dao = database.dao()
    val householdsFlow = dao.observeHouseholds()
    val peerCountsFlow = dao.observeHouseholdPeerCounts().map { counts ->
        counts.associate { it.householdId to it.peerCount }
    }
    private val deviceFlow = dao.observeConfig()
    val activeHouseholdIdFlow = deviceFlow.map { it?.activeHouseholdId.orEmpty() }.distinctUntilChanged()
    val configFlow: Flow<HouseholdSession?> = combine(deviceFlow, householdsFlow) { device, households ->
        device?.let { current ->
            households.firstOrNull { it.householdId == current.activeHouseholdId }?.let { session(current, it) }
        }
    }

    private fun <T> observeActive(query: (String) -> Flow<List<T>>): Flow<List<T>> =
        activeHouseholdIdFlow.flatMapLatest { if (it.isBlank()) flowOf(emptyList()) else query(it) }

    val peersFlow = observeActive(dao::observePeers)
    val membersFlow = observeActive(dao::observeActiveMembers)
    val choresFlow = observeActive(dao::observeActiveChores)
    val completionsFlow = observeActive(dao::observeAllCompletions)
    val placesFlow = observeActive(dao::observeActivePlaces)
    val remindersFlow = observeActive(dao::observeActiveReminders)
    val eventsFlow = observeActive(dao::observeActiveEvents)
    val dinnerOptionsFlow = observeActive(dao::observeActiveDinnerOptions)
    val groceryItemsFlow = observeActive(dao::observeActiveGroceryItems)

    suspend fun getDeviceConfig() = dao.getConfig()
    suspend fun getAllHouseholds() = dao.getAllHouseholds()
    suspend fun getHousehold(householdId: String) = dao.getHousehold(householdId)
    private suspend fun activeId() = dao.getConfig()?.activeHouseholdId.orEmpty()

    suspend fun getConfig(householdId: String? = null): HouseholdSession? {
        val device = dao.getConfig() ?: return null
        return dao.getHousehold(householdId ?: device.activeHouseholdId)?.let { session(device, it) }
    }

    suspend fun getAllPeers(householdId: String? = null): List<MeshPeerEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllPeers(it) }.orEmpty()
    suspend fun getAllMembers(householdId: String? = null): List<MemberEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllMembers(it) }.orEmpty()
    suspend fun getAllChores(householdId: String? = null): List<ChoreEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllChores(it) }.orEmpty()
    suspend fun getAllCompletions(householdId: String? = null): List<CompletionEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllCompletions(it) }.orEmpty()
    suspend fun getAllPlaces(householdId: String? = null): List<SavedPlaceEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllPlaces(it) }.orEmpty()
    suspend fun getAllReminders(householdId: String? = null): List<ReminderEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllReminders(it) }.orEmpty()
    suspend fun getAllEvents(householdId: String? = null): List<CalendarEventEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllEvents(it) }.orEmpty()
    suspend fun getAllDinnerOptions(householdId: String? = null): List<DinnerOptionEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllDinnerOptions(it) }.orEmpty()
    suspend fun getAllGroceryItems(householdId: String? = null): List<GroceryItemEntity> =
        (householdId ?: activeId()).takeIf { it.isNotBlank() }?.let { dao.getAllGroceryItems(it) }.orEmpty()

    suspend fun setActiveHousehold(householdId: String) = database.withTransaction {
        val device = dao.getConfig() ?: return@withTransaction
        if (dao.getHousehold(householdId) != null) {
            dao.upsertConfig(device.copy(activeHouseholdId = householdId))
        }
    }

    suspend fun setEndpointId(endpointId: String) = database.withTransaction {
        val device = dao.getConfig() ?: newDevice(DeviceType.PHONE)
        if (device.irohEndpointId != endpointId) {
            dao.upsertConfig(device.copy(irohEndpointId = endpointId))
        }
    }

    private fun newDevice(type: DeviceType) = NodeConfigEntity(nodeId = UUID.randomUUID().toString(), deviceType = type.name)

    suspend fun foundHousehold(
        householdName: String,
        displayName: String,
        avatarColorKey: String,
        deviceType: DeviceType
    ): HouseholdSession = database.withTransaction {
        val household = HouseholdEntity(
            householdId = UUID.randomUUID().toString(),
            householdName = householdName.trim(),
            pairingCode = generatePairingCode(),
            selfMemberId = UUID.randomUUID().toString(),
            selfRole = MemberRole.PARENT.name,
            isFounder = true
        )
        val config = addMembership(household, displayName, avatarColorKey, deviceType)
        dao.insertPlace(SavedPlaceEntity(
            id = UUID.randomUUID().toString(), name = "Home", latitude = 0.0, longitude = 0.0,
            isHomePlace = true, householdId = household.householdId
        ))
        config
    }

    suspend fun joinHousehold(
        householdId: String,
        householdName: String,
        pairingCode: String,
        displayName: String,
        role: MemberRole,
        avatarColorKey: String,
        deviceType: DeviceType,
        initialPayload: MeshGossipPayload?,
        hubEndpointId: String = ""
    ): HouseholdSession = database.withTransaction {
        require(householdId.isNotBlank() && (initialPayload == null || initialPayload.householdId == householdId))
        val existing = getConfig(householdId)
        if (existing != null) {
            setActiveHousehold(householdId)
            return@withTransaction existing
        }
        val household = HouseholdEntity(
            householdId = householdId,
            householdName = householdName.trim().ifEmpty { "Our Family" },
            pairingCode = pairingCode.trim().uppercase(),
            selfMemberId = UUID.randomUUID().toString(),
            selfRole = role.name,
            hubEndpointId = hubEndpointId
        )
        val config = addMembership(household, displayName, avatarColorKey, deviceType)
        if (initialPayload != null) mergeGossipPayload(householdId, initialPayload)
        getConfig(householdId) ?: config
    }

    private suspend fun addMembership(
        household: HouseholdEntity,
        displayName: String,
        avatarColorKey: String,
        deviceType: DeviceType
    ): HouseholdSession {
        val id = household.householdId
        val current = dao.getConfig() ?: newDevice(deviceType)
        val device = current.copy(
            deviceType = if (dao.getAllHouseholds().isEmpty()) deviceType.name else current.deviceType,
            activeHouseholdId = id
        )
        dao.upsertConfig(device)
        dao.upsertHousehold(household)
        check(dao.insertMember(MemberEntity(
            id = household.selfMemberId, displayName = displayName.trim(), role = household.selfRole,
            avatarColorKey = avatarColorKey, householdId = id
        )) != -1L)
        val config = session(device, household)
        refreshSelfPeer(config)
        return config
    }

    private suspend fun refreshSelfPeer(config: HouseholdSession) {
        val householdId = config.householdId
        val self = dao.getMemberById(householdId, config.selfMemberId)
        val now = System.currentTimeMillis()
        dao.upsertPeer(MeshPeerEntity(
            nodeId = config.nodeId,
            memberId = config.selfMemberId,
            displayName = self?.displayName ?: "This Device",
            role = config.selfRole,
            deviceType = config.deviceType,
            isFounder = config.isFounder,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            lastSeenAt = now,
            updatedAt = now,
            endpointId = config.irohEndpointId,
            householdId = householdId
        ))
    }

    suspend fun updateConfig(
        householdId: String? = null,
        transform: (HouseholdSession) -> HouseholdSession
    ): HouseholdSession? = database.withTransaction {
        val current = getConfig(householdId) ?: return@withTransaction null
        val updated = transform(current)
        val device = checkNotNull(dao.getConfig())
        val household = checkNotNull(dao.getHousehold(current.householdId))
        dao.upsertConfig(device.copy(
            darkTheme = updated.darkTheme, updateReleaseUrl = updated.updateReleaseUrl, listenPort = updated.listenPort
        ))
        dao.upsertHousehold(household.copy(
            householdName = updated.householdName, hubUrl = updated.hubUrl, hubEndpointId = updated.hubEndpointId,
            googleCalendarId = updated.googleCalendarId, googleCalendarName = updated.googleCalendarName,
            icsFeedUrl = updated.icsFeedUrl
        ))
        updated
    }

    suspend fun updateSelfProfile(displayName: String, avatarColorKey: String) = withHousehold { config ->
        val existing = dao.getMemberById(config.householdId, config.selfMemberId) ?: return@withHousehold
        dao.upsertMember(existing.copy(
            displayName = displayName.trim(), avatarColorKey = avatarColorKey, updatedAt = System.currentTimeMillis()
        ))
        refreshSelfPeer(config)
    }

    suspend fun addManualMember(displayName: String, role: MemberRole, avatarColorKey: String) = withHousehold { config ->
        dao.insertMember(MemberEntity(
            id = UUID.randomUUID().toString(), displayName = displayName.trim(), role = role.name,
            avatarColorKey = avatarColorKey, householdId = config.householdId
        ))
        Unit
    }

    private suspend fun <T> withHousehold(
        householdId: String? = null,
        block: suspend (HouseholdSession) -> T
    ): T? = database.withTransaction {
        val config = getConfig(householdId) ?: return@withTransaction null
        block(config)
    }

    suspend fun saveChore(chore: ChoreEntity) = withHousehold(chore.householdId.ifBlank { null }) { config ->
        saveChoreRow(chore.copy(householdId = config.householdId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteChore(chore: ChoreEntity) = saveChore(chore.copy(isDeleted = true))

    suspend fun toggleChoreCompletion(
        choreId: String, dateKey: String, completedByMemberId: String, householdId: String? = null
    ) = writeCompletion(choreId, dateKey, completedByMemberId, completed = null, householdId = householdId)

    suspend fun setChoreCompleted(
        choreId: String, dateKey: String, completedByMemberId: String, completed: Boolean, householdId: String? = null
    ) = writeCompletion(choreId, dateKey, completedByMemberId, completed, householdId)

    private suspend fun writeCompletion(
        choreId: String, dateKey: String, memberId: String, completed: Boolean?, householdId: String?
    ): Boolean = withHousehold(householdId) { config ->
        val id = config.householdId
        if (dao.getChoreById(id, choreId) == null || dao.getMemberById(id, memberId) == null) return@withHousehold false
        val key = CompletionEntity.key(choreId, dateKey)
        val existing = dao.getCompletionById(id, key)
        val isCompleted = completed ?: (existing?.isCompleted != true)
        val row = CompletionEntity(
            id = key, choreId = choreId, dateKey = dateKey, completedByMemberId = memberId,
            isCompleted = isCompleted, householdId = id
        )
        persist(row, existing, dao::insertCompletion, dao::upsertCompletion) && isCompleted
    } ?: false

    suspend fun savePlace(place: SavedPlaceEntity) = withHousehold(place.householdId.ifBlank { null }) { config ->
        savePlaceRow(place.copy(householdId = config.householdId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlace(place: SavedPlaceEntity) = savePlace(place.copy(isDeleted = true))

    suspend fun saveReminder(reminder: ReminderEntity) = withHousehold(reminder.householdId.ifBlank { null }) { config ->
        saveReminderRow(reminder.copy(householdId = config.householdId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleReminderCompleted(reminder: ReminderEntity) =
        saveReminder(reminder.copy(isCompleted = !reminder.isCompleted))

    suspend fun markReminderFired(reminder: ReminderEntity) = withHousehold(reminder.householdId.ifBlank { null }) { config ->
        val latest = dao.getReminderById(config.householdId, reminder.id) ?: return@withHousehold
        if (!latest.isDeleted && !latest.isCompleted) {
            val now = System.currentTimeMillis()
            dao.upsertReminder(latest.copy(lastFiredAt = now, updatedAt = now))
        }
    }

    suspend fun deleteReminder(reminder: ReminderEntity) = saveReminder(reminder.copy(isDeleted = true))

    suspend fun saveCalendarEvent(event: CalendarEventEntity) = withHousehold(event.householdId.ifBlank { null }) { config ->
        saveEventRow(event.copy(householdId = config.householdId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCalendarEvent(event: CalendarEventEntity) = saveCalendarEvent(event.copy(isDeleted = true))

    suspend fun proposeDinnerOption(
        dateKey: String, mealName: String, mealSlot: MealSlot, notes: String, memberId: String, memberName: String
    ) = withHousehold { config ->
        if (memberId != config.selfMemberId) return@withHousehold
        dao.insertDinnerOption(DinnerOptionEntity(
            id = UUID.randomUUID().toString(), dateKey = dateKey, mealName = mealName.trim(),
            mealSlot = mealSlot.name, notes = notes.trim(), proposedByMemberId = memberId,
            proposedByName = memberName, voterIdsCsv = memberId, householdId = config.householdId
        ))
    }

    suspend fun updateDinnerOption(option: DinnerOptionEntity) =
        withHousehold(option.householdId.ifBlank { null }) { config ->
            saveDinnerOptionRow(option.copy(
                householdId = config.householdId, mealName = option.mealName.trim(),
                notes = option.notes.trim(), updatedAt = System.currentTimeMillis()
            ))
        }

    suspend fun toggleDinnerVote(option: DinnerOptionEntity, voterId: String) = withHousehold(option.householdId) { config ->
        if (voterId != config.selfMemberId) return@withHousehold
        val latest = dao.getDinnerOptionById(config.householdId, option.id) ?: return@withHousehold
        val voters = latest.voterIds().toMutableSet()
        if (!voters.remove(voterId)) voters.add(voterId)
        dao.upsertDinnerOption(latest.copy(voterIdsCsv = voters.sorted().joinToString(","), updatedAt = System.currentTimeMillis()))
    }

    suspend fun setDinnerWinner(option: DinnerOptionEntity) = withHousehold(option.householdId) { config ->
        val latest = dao.getDinnerOptionById(config.householdId, option.id) ?: return@withHousehold
        val now = System.currentTimeMillis()
        val options = dao.getAllDinnerOptions(config.householdId).filter {
            !it.isDeleted && it.dateKey == latest.dateKey && it.mealSlot == latest.mealSlot
        }
        dao.upsertDinnerOptions(options.map { it.copy(isWinner = it.id == latest.id && !latest.isWinner, updatedAt = now) })
    }

    suspend fun deleteDinnerOption(option: DinnerOptionEntity) = updateDinnerOption(option.copy(isDeleted = true))

    suspend fun addGroceryItem(name: String, quantity: String = "", memberId: String, memberName: String) =
        withHousehold { config ->
            if (memberId != config.selfMemberId) return@withHousehold
            dao.insertGroceryItem(GroceryItemEntity(
                id = UUID.randomUUID().toString(), name = name.trim(), quantity = quantity.trim(),
                addedByMemberId = memberId, addedByName = memberName, householdId = config.householdId
            ))
        }

    suspend fun toggleGroceryChecked(item: GroceryItemEntity, memberId: String, memberName: String) =
        withHousehold(item.householdId) { config ->
            if (memberId != config.selfMemberId) return@withHousehold
            val latest = dao.getGroceryItemById(config.householdId, item.id) ?: return@withHousehold
            val now = System.currentTimeMillis()
            val checked = !latest.isChecked
            dao.upsertGroceryItem(latest.copy(
                isChecked = checked, checkedByMemberId = if (checked) memberId else "",
                checkedByName = if (checked) memberName else "", checkedAt = if (checked) now else 0,
                updatedAt = now
            ))
        }

    suspend fun deleteGroceryItem(item: GroceryItemEntity) = withHousehold(item.householdId) { config ->
        val latest = dao.getGroceryItemById(config.householdId, item.id) ?: return@withHousehold
        dao.upsertGroceryItem(latest.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearCheckedGroceries(householdId: String? = null) = withHousehold(householdId) { config ->
        val now = System.currentTimeMillis()
        val checked = dao.getAllGroceryItems(config.householdId).filter { !it.isDeleted && it.isChecked }
        if (checked.isNotEmpty()) dao.upsertGroceryItems(checked.map { it.copy(isDeleted = true, updatedAt = now) })
    }

    suspend fun upsertImportedEvents(events: List<CalendarEventEntity>, householdId: String? = null) =
        withHousehold(householdId) { config ->
            val existing = dao.getAllEvents(config.householdId).filter { it.externalEventId.isNotBlank() }
                .associateBy { it.source to it.externalEventId }.toMutableMap()
            for (event in events) {
                val key = event.source to event.externalEventId
                val match = existing[key]
                val row = event.copy(id = match?.id ?: UUID.randomUUID().toString(),
                    householdId = config.householdId, updatedAt = System.currentTimeMillis())
                if (persist(row, match, dao::insertEvent, dao::upsertEvent)) existing[key] = row
            }
        }

    private suspend fun <T> persist(
        row: T, existing: T?, insert: suspend (T) -> Long, update: suspend (T) -> Unit
    ): Boolean {
        // IDs are global. Never replace an ID collision unless the scoped read proved ownership.
        if (existing == null) return insert(row) != -1L
        update(row)
        return true
    }

    private suspend fun saveChoreRow(row: ChoreEntity) = persist(
        row, dao.getChoreById(row.householdId, row.id), dao::insertChore, dao::upsertChore
    )

    private suspend fun savePlaceRow(row: SavedPlaceEntity) = persist(
        row, dao.getPlaceById(row.householdId, row.id), dao::insertPlace, dao::upsertPlace
    )

    private suspend fun saveReminderRow(row: ReminderEntity) = persist(
        row, dao.getReminderById(row.householdId, row.id), dao::insertReminder, dao::upsertReminder
    )

    private suspend fun saveEventRow(row: CalendarEventEntity) = persist(
        row, dao.getEventById(row.householdId, row.id), dao::insertEvent, dao::upsertEvent
    )

    private suspend fun saveDinnerOptionRow(row: DinnerOptionEntity) = persist(
        row, dao.getDinnerOptionById(row.householdId, row.id), dao::insertDinnerOption, dao::upsertDinnerOption
    )

    suspend fun buildGossipPayload(householdId: String? = null): MeshGossipPayload? =
        withHousehold(householdId) { config ->
            refreshSelfPeer(config)
            val id = config.householdId
            MeshGossipPayload(
                householdId = id,
                householdName = config.householdName,
                senderNodeId = config.nodeId,
                senderEndpointId = config.irohEndpointId,
                sentAt = System.currentTimeMillis(),
                peers = dao.getAllPeers(id),
                members = dao.getAllMembers(id),
                chores = dao.getAllChores(id),
                completions = dao.getAllCompletions(id),
                places = dao.getAllPlaces(id),
                reminders = dao.getAllReminders(id),
                events = dao.getAllEvents(id),
                dinnerOptions = dao.getAllDinnerOptions(id),
                groceryItems = dao.getAllGroceryItems(id),
                updateReleaseUrl = config.updateReleaseUrl,
                hubEndpointId = config.hubEndpointId
            )
        }

    suspend fun handleIncomingGossip(request: GossipRequestEnvelope): IncomingGossip = database.withTransaction {
        if (request.protocolVersion != 2 || request.householdId.isBlank() ||
            (request.payload != null && (request.payload.householdId != request.householdId || request.payload.protocolVersion != 2))
        ) {
            return@withTransaction IncomingGossip(GossipResponseEnvelope(false, "bad_request"))
        }
        val household = dao.getHousehold(request.householdId)
            ?: return@withTransaction IncomingGossip(GossipResponseEnvelope(false, "unknown_household"))
        if (!MessageDigest.isEqual(household.pairingCode.toByteArray(), request.pairingCode.toByteArray())) {
            return@withTransaction IncomingGossip(GossipResponseEnvelope(false, "invalid_pairing_code"))
        }
        val changes = request.payload?.let { mergeGossipPayload(household.householdId, it) }
        IncomingGossip(
            GossipResponseEnvelope(ok = true, payload = buildGossipPayload(household.householdId)),
            changes
        )
    }

    suspend fun mergeGossipPayload(householdId: String, payload: MeshGossipPayload): GossipMergeResult =
        database.withTransaction {
            if (payload.householdId != householdId || payload.protocolVersion != 2) return@withTransaction GossipMergeResult()
            val household = dao.getHousehold(householdId) ?: return@withTransaction GossipMergeResult()
            val device = dao.getConfig() ?: return@withTransaction GossipMergeResult()
            val now = System.currentTimeMillis()

            val peers = dao.getAllPeers(householdId).associateBy { it.nodeId }.toMutableMap()
            for (incoming in payload.peers) {
                if (incoming.nodeId == device.nodeId || !belongsTo(incoming.householdId, householdId)) continue
                val existing = peers[incoming.nodeId]
                val isSender = incoming.nodeId == payload.senderNodeId
                val chosen = when {
                    existing == null || incoming.updatedAt > existing.updatedAt -> incoming.copy(
                        householdId = householdId,
                        lastSeenAt = if (isSender) now else maxOf(existing?.lastSeenAt ?: 0L, incoming.lastSeenAt)
                    )
                    isSender -> existing.copy(lastSeenAt = now)
                    else -> continue
                }
                val peer = if (isSender && payload.senderEndpointId.isNotBlank()) {
                    chosen.copy(endpointId = payload.senderEndpointId)
                } else {
                    chosen
                }
                dao.upsertPeer(peer)
                peers[peer.nodeId] = peer
            }

            val newReminders = mutableMapOf<String, ReminderEntity>()
            val fulfilled = mutableMapOf<String, GroceryItemEntity>()
            mergeRows(
                householdId, payload.members, dao.getAllMembers(householdId),
                MemberEntity::id, MemberEntity::updatedAt, MemberEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertMember, dao::upsertMember
            )
            mergeRows(
                householdId, payload.chores, dao.getAllChores(householdId),
                ChoreEntity::id, ChoreEntity::updatedAt, ChoreEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertChore, dao::upsertChore
            )
            mergeRows(
                householdId, payload.completions, dao.getAllCompletions(householdId),
                CompletionEntity::id, CompletionEntity::updatedAt, CompletionEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertCompletion, dao::upsertCompletion
            )
            mergeRows(
                householdId, payload.places, dao.getAllPlaces(householdId),
                SavedPlaceEntity::id, SavedPlaceEntity::updatedAt, SavedPlaceEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertPlace, dao::upsertPlace
            )
            mergeRows(
                householdId, payload.reminders, dao.getAllReminders(householdId),
                ReminderEntity::id, ReminderEntity::updatedAt, ReminderEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertReminder, dao::upsertReminder
            ) { existing, incoming ->
                if (incoming.isDeleted || incoming.isCompleted) {
                    newReminders.remove(incoming.id)
                } else if ((existing == null || incoming.id in newReminders) &&
                    incoming.createdByMemberId != household.selfMemberId &&
                    (incoming.assignedToMemberId == null || incoming.assignedToMemberId == household.selfMemberId)
                ) {
                    newReminders[incoming.id] = incoming
                }
            }
            mergeRows(
                householdId, payload.events, dao.getAllEvents(householdId),
                CalendarEventEntity::id, CalendarEventEntity::updatedAt, CalendarEventEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertEvent, dao::upsertEvent
            )
            mergeRows(
                householdId, payload.dinnerOptions, dao.getAllDinnerOptions(householdId),
                DinnerOptionEntity::id, DinnerOptionEntity::updatedAt, DinnerOptionEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertDinnerOption, dao::upsertDinnerOption
            )
            mergeRows(
                householdId, payload.groceryItems, dao.getAllGroceryItems(householdId),
                GroceryItemEntity::id, GroceryItemEntity::updatedAt, GroceryItemEntity::householdId, { row, id -> row.copy(householdId = id) },
                dao::insertGroceryItem, dao::upsertGroceryItem
            ) { existing, incoming ->
                if (incoming.isDeleted || !incoming.isChecked) {
                    fulfilled.remove(incoming.id)
                } else if ((existing?.isChecked != true || incoming.id in fulfilled) &&
                    incoming.addedByMemberId == household.selfMemberId &&
                    incoming.checkedByMemberId.isNotBlank() && incoming.checkedByMemberId != household.selfMemberId
                ) {
                    fulfilled[incoming.id] = incoming
                }
            }

            val name = payload.householdName.ifBlank { household.householdName }
            dao.upsertHousehold(household.copy(
                householdName = name, lastSyncedAt = now,
                hubEndpointId = payload.hubEndpointId.ifBlank { household.hubEndpointId }
            ))
            GossipMergeResult(newReminders.values.toList(), fulfilled.values.toList(), name)
        }

    private fun belongsTo(rowHousehold: String, householdId: String) =
        rowHousehold.isBlank() || rowHousehold == householdId

    private suspend fun <T> mergeRows(
        householdId: String,
        incoming: List<T>,
        local: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        owner: (T) -> String,
        scoped: (T, String) -> T,
        insert: suspend (T) -> Long,
        update: suspend (T) -> Unit,
        adopted: (T?, T) -> Unit = { _, _ -> }
    ) {
        val existingRows = local.associateBy(id).toMutableMap()
        for (unscoped in incoming) {
            if (!belongsTo(owner(unscoped), householdId)) continue
            val row = scoped(unscoped, householdId)
            val key = id(row)
            val existing = existingRows[key]
            if (existing != null && updatedAt(row) <= updatedAt(existing)) continue
            if (persist(row, existing, insert, update)) {
                existingRows[key] = row
                adopted(existing, row)
            }
        }
    }

    suspend fun buildBackupSnapshot(): HouseholdBackupSnapshot? = withHousehold { config ->
        val payload = checkNotNull(buildGossipPayload(config.householdId))
        HouseholdBackupSnapshot(
            exportedAt = System.currentTimeMillis(), config = config,
            peers = payload.peers, members = payload.members, chores = payload.chores,
            completions = payload.completions, places = payload.places, reminders = payload.reminders,
            events = payload.events, dinnerOptions = payload.dinnerOptions, groceryItems = payload.groceryItems
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: HouseholdBackupSnapshot): HouseholdSession = database.withTransaction {
        val saved = snapshot.config
        val id = saved.householdId
        require(id.isNotBlank() && saved.selfMemberId.isNotBlank() && saved.pairingCode.isNotBlank())
        val device = (dao.getConfig() ?: NodeConfigEntity(
            nodeId = saved.nodeId, deviceType = saved.deviceType,
            darkTheme = saved.darkTheme, updateReleaseUrl = saved.updateReleaseUrl
        )).copy(activeHouseholdId = id)
        val oldHousehold = dao.getHousehold(id)
        if (oldHousehold != null) deleteHouseholdRows(id)
        dao.upsertConfig(device)
        dao.upsertHousehold(HouseholdEntity(
            householdId = id, householdName = saved.householdName, pairingCode = saved.pairingCode,
            selfMemberId = saved.selfMemberId, selfRole = saved.selfRole, isFounder = saved.isFounder,
            hubUrl = saved.hubUrl, hubEndpointId = saved.hubEndpointId,
            googleCalendarId = saved.googleCalendarId, googleCalendarName = saved.googleCalendarName,
            icsFeedUrl = saved.icsFeedUrl, joinedAt = oldHousehold?.joinedAt ?: System.currentTimeMillis()
        ))
        mergeGossipPayload(id, MeshGossipPayload(
            householdId = id, householdName = saved.householdName, senderNodeId = saved.nodeId,
            sentAt = snapshot.exportedAt, peers = snapshot.peers.filter { it.nodeId != saved.nodeId },
            members = snapshot.members, chores = snapshot.chores, completions = snapshot.completions,
            places = snapshot.places, reminders = snapshot.reminders, events = snapshot.events,
            dinnerOptions = snapshot.dinnerOptions, groceryItems = snapshot.groceryItems
        ))
        val restored = checkNotNull(getConfig(id))
        refreshSelfPeer(restored)
        restored
    }

    suspend fun leaveHousehold(householdId: String) = database.withTransaction {
        val device = dao.getConfig() ?: return@withTransaction
        if (dao.getHousehold(householdId) == null) return@withTransaction
        deleteHouseholdRows(householdId)
        dao.deleteHousehold(householdId)
        if (device.activeHouseholdId == householdId) {
            dao.upsertConfig(device.copy(activeHouseholdId = dao.getAllHouseholds().firstOrNull()?.householdId.orEmpty()))
        }
    }

    private suspend fun deleteHouseholdRows(householdId: String) {
        dao.deletePeersForHousehold(householdId)
        dao.deleteMembersForHousehold(householdId)
        dao.deleteChoresForHousehold(householdId)
        dao.deleteCompletionsForHousehold(householdId)
        dao.deletePlacesForHousehold(householdId)
        dao.deleteRemindersForHousehold(householdId)
        dao.deleteEventsForHousehold(householdId)
        dao.deleteDinnerOptionsForHousehold(householdId)
        dao.deleteGroceryItemsForHousehold(householdId)
    }

    private fun session(device: NodeConfigEntity, household: HouseholdEntity) = HouseholdSession(
        nodeId = device.nodeId, householdId = household.householdId, householdName = household.householdName,
        pairingCode = household.pairingCode, selfMemberId = household.selfMemberId, selfRole = household.selfRole,
        deviceType = device.deviceType, isFounder = household.isFounder, listenPort = device.listenPort,
        googleCalendarId = household.googleCalendarId, googleCalendarName = household.googleCalendarName,
        icsFeedUrl = household.icsFeedUrl, updateReleaseUrl = device.updateReleaseUrl, hubUrl = household.hubUrl,
        darkTheme = device.darkTheme, lastSyncedAt = household.lastSyncedAt, irohEndpointId = device.irohEndpointId,
        hubEndpointId = household.hubEndpointId
    )

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
