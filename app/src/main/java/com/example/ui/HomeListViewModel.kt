package com.example.ui

import android.app.Application
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.resume
import com.example.BuildConfig
import com.example.calendar.DeviceCalendarInfo
import com.example.calendar.GoogleCalendarSyncBridge
import com.example.data.CalendarEventEntity
import com.example.data.CalendarSource
import com.example.data.ChoreEntity
import com.example.data.CompletionEntity
import com.example.data.DateStreakUtils
import com.example.data.DeviceType
import com.example.data.DinnerOptionEntity
import com.example.data.GroceryAisle
import com.example.data.GroceryItemEntity
import com.example.data.MealSlot
import com.example.data.MemberEntity
import com.example.data.MemberRole
import com.example.data.MemberStreakStats
import com.example.data.MeshPeerEntity
import com.example.data.HouseholdSession
import com.example.data.PlaceTransition
import com.example.data.ReminderEntity
import com.example.data.ReminderTriggerType
import com.example.data.SavedPlaceEntity
import com.example.data.HouseholdEntity
import com.example.mesh.HouseholdInvite
import com.example.mesh.MeshRuntime
import kotlinx.coroutines.CancellationException
import com.example.mesh.HouseholdBackupSnapshot
import com.example.mesh.MeshBackgroundSyncJobService
import com.example.mesh.MeshRuntimeStatus
import com.example.mesh.MeshSyncEngine
import com.example.reminders.PlaceProximityInfo
import com.example.reminders.ReminderNotificationHelper
import com.example.updates.AppUpdateChecker
import com.example.updates.AvailableUpdateInfo
import com.example.updates.NightlyUpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val label: String) {
    TODAY("Today"),
    SHOPPING("Shopping"),
    MEALS("Meals"),
    REMINDERS("Reminders"),
    CALENDAR("Calendar"),
    HOUSEHOLD("Households")
}

data class HomeListUiState(
    val isLoading: Boolean = true,
    val config: HouseholdSession? = null,
    val selfMember: MemberEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val peers: List<MeshPeerEntity> = emptyList(),
    val chores: List<ChoreEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList(),
    val places: List<SavedPlaceEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val dinnerOptions: List<DinnerOptionEntity> = emptyList(),
    val groceryItems: List<GroceryItemEntity> = emptyList(),
    val memberStats: Map<String, MemberStreakStats> = emptyMap(),
    val meshStatus: MeshRuntimeStatus = MeshRuntimeStatus(),
    val households: List<HouseholdEntity> = emptyList(),
    val householdPeerCounts: Map<String, Int> = emptyMap(),
    val showHouseholdSetup: Boolean = false,
    val inviteLink: String? = null,
    val placeProximity: Map<String, PlaceProximityInfo> = emptyMap(),
    val deviceCalendars: List<DeviceCalendarInfo> = emptyList(),
    val isCalendarSyncing: Boolean = false,
    val isJoiningMesh: Boolean = false,
    val availableUpdate: AvailableUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val snackbarMessage: String? = null
) {
    val isConfigured: Boolean get() = config != null
    val isParent: Boolean get() = MemberRole.fromString(config?.selfRole) == MemberRole.PARENT
    val isDarkTheme: Boolean get() = config?.darkTheme == true
}

private data class CoreDbBundle(
    val config: HouseholdSession?,
    val members: List<MemberEntity>,
    val peers: List<MeshPeerEntity>,
    val chores: List<ChoreEntity>,
    val completions: List<CompletionEntity>
)

private data class ExtraDbBundle(
    val places: List<SavedPlaceEntity>,
    val reminders: List<ReminderEntity>,
    val events: List<CalendarEventEntity>,
    val dinnerOptions: List<DinnerOptionEntity>,
    val groceryItems: List<GroceryItemEntity>
)

private data class TransientState(
    val showHouseholdSetup: Boolean = false,
    val inviteLink: String? = null,
    val deviceCalendars: List<DeviceCalendarInfo> = emptyList(),
    val isCalendarSyncing: Boolean = false,
    val isJoiningMesh: Boolean = false,
    val githubUpdate: AvailableUpdateInfo? = null,
    val updateBannerDismissedVersion: String? = null,
    val isCheckingUpdate: Boolean = false,
    val snackbarMessage: String? = null
)

class HomeListViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val meshRuntime = MeshRuntime.get(appContext)
    private val repository = meshRuntime.repository
    val locationManager = meshRuntime.locationManager
    val calendarBridge = GoogleCalendarSyncBridge(appContext)

    private val transientState = MutableStateFlow(TransientState())
    private val _pinnedCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val pinnedCoordinates: StateFlow<Pair<Double, Double>?> = _pinnedCoordinates.asStateFlow()

    private val meshEngine = MeshSyncEngine(meshRuntime)

    private val coreBundleFlow = combine(
        repository.configFlow,
        repository.membersFlow,
        repository.peersFlow,
        repository.choresFlow,
        repository.completionsFlow
    ) { config, members, peers, chores, completions ->
        val id = config?.householdId
        CoreDbBundle(config,
            members.filter { it.householdId == id }, peers.filter { it.householdId == id },
            chores.filter { it.householdId == id }, completions.filter { it.householdId == id })
    }

    private val extraBundleFlow = combine(
        repository.placesFlow,
        repository.remindersFlow,
        repository.eventsFlow,
        repository.dinnerOptionsFlow,
        repository.groceryItemsFlow
    ) { places, reminders, events, dinners, groceries ->
        ExtraDbBundle(places, reminders, events, dinners, groceries)
    }

    val uiState: StateFlow<HomeListUiState> = combine(
        coreBundleFlow,
        extraBundleFlow,
        meshEngine.status,
        combine(repository.householdsFlow, repository.peerCountsFlow) { households, counts -> households to counts },
        combine(locationManager.proximityMap, transientState) { prox, tr -> prox to tr }
    ) { core, extra, meshStatus, (households, peerCounts), (proximity, transient) ->
        val id = core.config?.householdId
        val places = extra.places.filter { it.householdId == id }
        val placeIds = places.map { it.id }.toSet()
        val today = DateStreakUtils.todayKey()
        val statsMap = core.members.associate { member ->
            member.id to DateStreakUtils.computeMemberStats(
                memberId = member.id,
                chores = core.chores,
                completions = core.completions,
                referenceDateKey = today
            )
        }
        val selfMember = core.members.firstOrNull { it.id == core.config?.selfMemberId }
        val configuredUrl = core.config?.updateReleaseUrl?.ifBlank { AppUpdateChecker.DEFAULT_RELEASES_URL }
            ?: AppUpdateChecker.DEFAULT_RELEASES_URL
        val meshPeerUpdate = AppUpdateChecker.detectMeshPeerUpdate(
            peers = core.peers,
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            configuredUrl = configuredUrl
        )
        val rawUpdate = transient.githubUpdate ?: meshPeerUpdate
        val visibleUpdate = if (rawUpdate != null && rawUpdate.versionName == transient.updateBannerDismissedVersion) {
            null
        } else {
            rawUpdate
        }
        HomeListUiState(
            isLoading = id == null && households.isNotEmpty(),
            config = core.config,
            selfMember = selfMember,
            members = core.members,
            peers = core.peers,
            chores = core.chores,
            completions = core.completions,
            places = places,
            reminders = extra.reminders.filter { it.householdId == id },
            events = extra.events.filter { it.householdId == id },
            dinnerOptions = extra.dinnerOptions.filter { it.householdId == id },
            groceryItems = extra.groceryItems.filter { it.householdId == id },
            memberStats = statsMap,
            meshStatus = meshStatus,
            households = households,
            householdPeerCounts = peerCounts,
            showHouseholdSetup = transient.showHouseholdSetup,
            inviteLink = transient.inviteLink,
            placeProximity = proximity.filterKeys { it in placeIds },
            deviceCalendars = transient.deviceCalendars,
            isCalendarSyncing = transient.isCalendarSyncing,
            isJoiningMesh = transient.isJoiningMesh,
            availableUpdate = visibleUpdate,
            isCheckingUpdate = transient.isCheckingUpdate,
            snackbarMessage = transient.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeListUiState(isLoading = true)
    )

    init {
        ReminderNotificationHelper.ensureChannels(appContext)
        viewModelScope.launch { meshRuntime.shoppingSummaries.collect(::showMessage) }
        NightlyUpdateScheduler.scheduleNightlyCheck(appContext)
        viewModelScope.launch {
            val existing = repository.getConfig()
            if (existing != null) {
                MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
                rescheduleAllNotifications()
                refreshLocationChecks()
                checkForUpdates(silent = true)
            }
        }
    }

    override fun onCleared() {
        meshEngine.close()
        super.onCleared()
    }

    fun onAppResumed() {
        NightlyUpdateScheduler.scheduleNightlyCheck(appContext)
        viewModelScope.launch {
            val cfg = repository.getConfig()
            if (cfg != null) {
                MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
                meshEngine.syncMeshNow()
                refreshLocationChecks()
            }
        }
    }

    fun consumeSnackbar() {
        transientState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        transientState.update { it.copy(snackbarMessage = message) }
    }

    // -------------------------------------------------------------------------
    // Onboarding: Found or Join a Family Grouping
    // -------------------------------------------------------------------------

    fun foundHousehold(
        householdName: String,
        displayName: String,
        avatarColorKey: String,
        isTablet: Boolean
    ) {
        if (householdName.isBlank() || displayName.isBlank()) {
            showMessage("Enter both a family name and your name.")
            return
        }
        viewModelScope.launch {
            val deviceType = if (isTablet) DeviceType.TABLET else DeviceType.PHONE
            val config = repository.foundHousehold(
                householdName = householdName,
                displayName = displayName,
                avatarColorKey = avatarColorKey,
                deviceType = deviceType
            )
            MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
            closeHouseholdSetup()
            meshEngine.gossipAfterLocalChange()
            showMessage("Created ${config.householdName}")
        }
    }

    fun openHouseholdSetup(inviteLink: String? = null) {
        if (inviteLink != null && HouseholdInvite.parse(inviteLink) == null) return
        transientState.update { it.copy(showHouseholdSetup = true, inviteLink = inviteLink) }
    }

    fun closeHouseholdSetup() {
        transientState.update { it.copy(showHouseholdSetup = false, inviteLink = null) }
    }

    fun switchHousehold(householdId: String) {
        viewModelScope.launch {
            repository.setActiveHousehold(householdId)
            clearPinnedCoordinates()
            refreshLocationChecks()
        }
    }

    fun joinHouseholdViaEndpoint(rawLink: String, displayName: String, role: MemberRole, avatarColorKey: String, isTablet: Boolean) {
        val invite = HouseholdInvite.parse(rawLink)
        if (invite == null || displayName.isBlank()) {
            showMessage("Enter a HomeList invite link and your display name.")
            return
        }
        if (transientState.value.isJoiningMesh) return
        transientState.update { it.copy(isJoiningMesh = true) }
        viewModelScope.launch {
            try {
                val existing = repository.getHousehold(invite.householdId)
                if (existing != null) {
                    repository.setActiveHousehold(invite.householdId)
                    closeHouseholdSetup()
                    return@launch
                }
                val payload = meshEngine.joinViaEndpoint(invite)
                repository.joinHousehold(
                    householdId = invite.householdId,
                    householdName = payload.householdName.ifBlank { invite.householdName },
                    pairingCode = invite.pairingCode,
                    displayName = displayName,
                    role = role,
                    avatarColorKey = avatarColorKey,
                    deviceType = if (isTablet) DeviceType.TABLET else DeviceType.PHONE,
                    initialPayload = payload,
                    hubEndpointId = payload.hubEndpointId
                )
                MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
                closeHouseholdSetup()
                rescheduleAllNotifications()
                syncLocationMonitors()
                meshEngine.gossipAfterLocalChange()
                showMessage("Joined ${payload.householdName} as ${role.label}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(error.message ?: "Could not connect to the household")
            } finally {
                transientState.update { it.copy(isJoiningMesh = false) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Chores & Streaks
    // -------------------------------------------------------------------------

    fun saveChore(input: ChoreEntity) {
        val householdId = input.householdId.ifBlank { uiState.value.config?.householdId ?: return }
        val chore = input.copy(householdId = householdId)
        if (chore.title.isBlank()) return
        viewModelScope.launch {
            if (repository.saveChore(chore) != true) return@launch
            ReminderNotificationHelper.scheduleChoreReminder(appContext, chore)
            meshEngine.gossipAfterLocalChange()
            showMessage("Saved chore \"${chore.title}\"")
        }
    }

    fun deleteChore(chore: ChoreEntity) {
        viewModelScope.launch {
            repository.deleteChore(chore)
            ReminderNotificationHelper.cancelScheduled(appContext, ("chore_${chore.id}").hashCode())
            meshEngine.gossipAfterLocalChange()
            showMessage("Removed \"${chore.title}\"")
        }
    }

    fun toggleChoreCompletion(choreId: String, dateKey: String = DateStreakUtils.todayKey()) {
        val config = uiState.value.config ?: return
        viewModelScope.launch {
            repository.toggleChoreCompletion(choreId, dateKey, config.selfMemberId, config.householdId)
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun sendChoreNudge(chore: ChoreEntity, targetMember: MemberEntity?) {
        val householdId = chore.householdId
        if (householdId.isBlank() || (targetMember != null && targetMember.householdId != householdId)) return
        viewModelScope.launch {
            val config = repository.getConfig(householdId) ?: return@launch
            val self = repository.getAllMembers(householdId).firstOrNull { it.id == config.selfMemberId }
            val nudge = ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = chore.title,
                notes = "Friendly reminder from ${self?.displayName ?: "Parent"}",
                assignedToMemberId = targetMember?.id ?: chore.effectiveAssigneeForDate(DateStreakUtils.todayKey()),
                createdByMemberId = config.selfMemberId,
                createdByName = self?.displayName ?: "Parent",
                linkedChoreId = chore.id,
                triggerType = ReminderTriggerType.NUDGE.name,
                isCompleted = false,
                householdId = householdId,
                updatedAt = System.currentTimeMillis()
            )
            if (repository.saveReminder(nudge) != true) return@launch
            val pushed = meshEngine.syncMeshNow(householdId)
            val targetName = targetMember?.displayName ?: "family"
            showMessage(
                if (pushed > 0) "Pushed reminder live to $targetName ($pushed phone${if (pushed == 1) "" else "s"} reached)"
                else "Queued reminder for $targetName (delivers when phone connects)"
            )
        }
    }

    /**
     * Dedicated Parent -> Child instant live push reminder (not tied to any specific chore).
     */
    fun sendLivePushReminder(
        message: String,
        targetMemberId: String?,
        targetLabel: String,
        isUrgent: Boolean
    ) {
        if (message.isBlank()) return
        val householdId = uiState.value.config?.householdId ?: return
        viewModelScope.launch {
            val config = repository.getConfig(householdId) ?: return@launch
            val self = repository.getAllMembers(householdId).firstOrNull { it.id == config.selfMemberId }
            val nudge = ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = message.trim(),
                notes = if (isUrgent) "Urgent live reminder" else "Live reminder from ${self?.displayName ?: "Parent"}",
                assignedToMemberId = targetMemberId,
                createdByMemberId = config.selfMemberId,
                createdByName = self?.displayName ?: "Parent",
                linkedChoreId = null,
                triggerType = ReminderTriggerType.NUDGE.name,
                isCompleted = false,
                isUrgent = isUrgent,
                householdId = householdId,
                updatedAt = System.currentTimeMillis()
            )
            if (repository.saveReminder(nudge) != true) return@launch
            val pushed = meshEngine.syncMeshNow(householdId)
            showMessage(
                if (pushed > 0) "Pushed live to $targetLabel ($pushed peer${if (pushed == 1) "" else "s"} synced)"
                else "Queued for $targetLabel — will notify as soon as their phone connects"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Flexible Meal Requests & Voting
    // -------------------------------------------------------------------------

    fun proposeDinnerOption(
        dateKey: String,
        mealName: String,
        mealSlot: MealSlot = MealSlot.DINNER,
        notes: String = ""
    ) {
        if (mealName.isBlank()) return
        viewModelScope.launch {
            val cfg = repository.getConfig() ?: return@launch
            val self = repository.getAllMembers().firstOrNull { it.id == cfg.selfMemberId }
            repository.proposeDinnerOption(
                dateKey = dateKey,
                mealName = mealName,
                mealSlot = mealSlot,
                notes = notes,
                memberId = cfg.selfMemberId,
                memberName = self?.displayName ?: "Family"
            )
            meshEngine.gossipAfterLocalChange()
            showMessage("Added \"$mealName\" to ${mealSlot.label.lowercase()} vote")
        }
    }

    fun updateDinnerOption(option: DinnerOptionEntity) {
        if (option.mealName.isBlank()) return
        viewModelScope.launch {
            repository.updateDinnerOption(option)
            meshEngine.gossipAfterLocalChange()
            showMessage("Updated \"${option.mealName}\"")
        }
    }

    fun toggleDinnerVote(option: DinnerOptionEntity) {
        viewModelScope.launch {
            val memberId = repository.getConfig()?.selfMemberId ?: return@launch
            repository.toggleDinnerVote(option, memberId)
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun setDinnerWinner(option: DinnerOptionEntity) {
        viewModelScope.launch {
            repository.setDinnerWinner(option)
            meshEngine.gossipAfterLocalChange()
            showMessage(
                if (!option.isWinner) "Locked in \"${option.mealName}\"!"
                else "Unlocked meal choice"
            )
        }
    }

    fun deleteDinnerOption(option: DinnerOptionEntity) {
        viewModelScope.launch {
            repository.deleteDinnerOption(option)
            meshEngine.gossipAfterLocalChange()
        }
    }

    // -------------------------------------------------------------------------
    // Plain Synced Shopping List
    // -------------------------------------------------------------------------

    fun addGroceryItem(name: String, note: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cfg = repository.getConfig() ?: return@launch
            val self = repository.getAllMembers().firstOrNull { it.id == cfg.selfMemberId }
            repository.addGroceryItem(
                name = name,
                quantity = note,
                memberId = cfg.selfMemberId,
                memberName = self?.displayName ?: "Family"
            )
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun toggleGroceryItem(item: GroceryItemEntity) {
        viewModelScope.launch {
            val cfg = repository.getConfig() ?: return@launch
            val self = repository.getAllMembers().firstOrNull { it.id == cfg.selfMemberId }
            repository.toggleGroceryChecked(
                item = item,
                memberId = cfg.selfMemberId,
                memberName = self?.displayName ?: "Family"
            )
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun deleteGroceryItem(item: GroceryItemEntity) {
        viewModelScope.launch {
            repository.deleteGroceryItem(item)
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun clearCheckedGroceries() {
        val householdId = uiState.value.config?.householdId ?: return
        viewModelScope.launch {
            repository.clearCheckedGroceries(householdId)
            meshEngine.gossipAfterLocalChange()
            showMessage("Cleared checked items")
        }
    }

    fun completeNudgeAndChore(nudge: ReminderEntity) {
        viewModelScope.launch {
            val selfId = repository.getConfig(nudge.householdId)?.selfMemberId ?: return@launch
            if (repository.saveReminder(nudge.copy(isCompleted = true)) != true) return@launch
            val linkedChoreId = nudge.linkedChoreId
            if (!linkedChoreId.isNullOrBlank()) {
                repository.setChoreCompleted(linkedChoreId, DateStreakUtils.todayKey(), selfId, true, nudge.householdId)
            }
            meshEngine.gossipAfterLocalChange()
            showMessage(
                if (linkedChoreId.isNullOrBlank()) "Got it — parent notified!"
                else "Nice work — marked done!"
            )
        }
    }

    fun dismissNudge(nudge: ReminderEntity) {
        viewModelScope.launch {
            repository.saveReminder(nudge.copy(isCompleted = true))
            meshEngine.gossipAfterLocalChange()
        }
    }

    // -------------------------------------------------------------------------
    // Standalone Time & Location Reminders + Saved Places
    // -------------------------------------------------------------------------

    fun saveReminder(input: ReminderEntity) {
        val householdId = input.householdId.ifBlank { uiState.value.config?.householdId ?: return }
        val reminder = input.copy(householdId = householdId)
        if (reminder.title.isBlank()) return
        viewModelScope.launch {
            val config = repository.getConfig(householdId) ?: return@launch
            val self = repository.getAllMembers(householdId).firstOrNull { it.id == config.selfMemberId }
            val enriched = reminder.copy(
                createdByMemberId = reminder.createdByMemberId.ifBlank { config.selfMemberId },
                createdByName = reminder.createdByName.ifBlank { self?.displayName ?: "Family" }
            )
            if (repository.saveReminder(enriched) != true) return@launch
            ReminderNotificationHelper.scheduleTimeReminder(appContext, enriched)
            syncLocationMonitors()
            meshEngine.gossipAfterLocalChange()
            showMessage("Saved reminder \"${enriched.title}\"")
        }
    }

    fun toggleReminderCompleted(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.toggleReminderCompleted(reminder)
            syncLocationMonitors()
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            ReminderNotificationHelper.cancelScheduled(appContext, reminder.id.hashCode())
            syncLocationMonitors()
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun pinCurrentGpsLocation(onResult: (Pair<Double, Double>?) -> Unit = {}) {
        viewModelScope.launch {
            val coords = locationManager.getCurrentLocation()
            _pinnedCoordinates.value = coords
            onResult(coords)
            if (coords == null) {
                showMessage("Could not read GPS fix — check location permission or enter coordinates.")
            } else {
                showMessage("Pinned current GPS location!")
            }
        }
    }

    fun clearPinnedCoordinates() {
        _pinnedCoordinates.value = null
    }

    fun searchAddressCoordinates(query: String) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val coords = withContext(Dispatchers.IO) {
                try {
                    if (!Geocoder.isPresent()) return@withContext null
                    val geocoder = Geocoder(appContext, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                            geocoder.getFromLocationName(cleaned, 1) { addresses ->
                                val first = addresses.firstOrNull()
                                if (cont.isActive) {
                                    cont.resume(first?.let { it.latitude to it.longitude })
                                }
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val first = geocoder.getFromLocationName(cleaned, 1)?.firstOrNull()
                        first?.let { it.latitude to it.longitude }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (coords != null) {
                _pinnedCoordinates.value = coords
                showMessage("Found address location!")
            } else {
                showMessage("Couldn't find \"$cleaned\" — try adding city/state or use current GPS.")
            }
        }
    }

    fun savePlace(input: SavedPlaceEntity) {
        val householdId = input.householdId.ifBlank { uiState.value.config?.householdId ?: return }
        val place = input.copy(householdId = householdId)
        if (place.name.isBlank()) return
        viewModelScope.launch {
            if (repository.savePlace(place) != true) return@launch
            syncLocationMonitors()
            refreshLocationChecks()
            meshEngine.gossipAfterLocalChange()
            showMessage("Saved place \"${place.name}\"")
        }
    }

    fun deletePlace(place: SavedPlaceEntity) {
        viewModelScope.launch {
            repository.deletePlace(place)
            syncLocationMonitors()
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun refreshLocationChecks() {
        viewModelScope.launch {
            val cfg = repository.getConfig() ?: return@launch
            val places = repository.getAllPlaces(cfg.householdId)
            val reminders = repository.getAllReminders(cfg.householdId)
            val fired = locationManager.evaluateLocationReminders(places, reminders, cfg.selfMemberId)
            fired.forEach { repository.markReminderFired(it) }
            syncLocationMonitors()
        }
    }

    private suspend fun syncLocationMonitors() {
        val houses = repository.getAllHouseholds()
        locationManager.syncGeofences(
            places = houses.flatMap { repository.getAllPlaces(it.householdId) },
            reminders = houses.flatMap { repository.getAllReminders(it.householdId) },
            memberIdsByHousehold = houses.associate { it.householdId to it.selfMemberId }
        )
    }

    private suspend fun rescheduleAllNotifications() {
        val today = DateStreakUtils.todayKey()
        for (house in repository.getAllHouseholds()) {
            val id = house.householdId
            repository.getAllChores(id).forEach { chore ->
                val effective = chore.effectiveAssigneeForDate(today)
                if (!chore.isDeleted && (effective == null || effective == house.selfMemberId)) {
                    ReminderNotificationHelper.scheduleChoreReminder(appContext, chore)
                } else {
                    ReminderNotificationHelper.cancelScheduled(appContext, chore.id.hashCode())
                }
            }
            repository.getAllReminders(id).forEach { reminder ->
                if (reminder.assignedToMemberId == null || reminder.assignedToMemberId == house.selfMemberId) {
                    ReminderNotificationHelper.scheduleTimeReminder(appContext, reminder)
                } else {
                    ReminderNotificationHelper.cancelScheduled(appContext, reminder.id.hashCode())
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Family Calendar Events & Two-Way Google Calendar Sync
    // -------------------------------------------------------------------------

    fun saveCalendarEvent(input: CalendarEventEntity, alsoExportToGoogle: Boolean = false) {
        val householdId = input.householdId.ifBlank { uiState.value.config?.householdId ?: return }
        val event = input.copy(householdId = householdId)
        if (event.title.isBlank()) return
        viewModelScope.launch {
            var toSave = event
            if (alsoExportToGoogle) {
                val cfg = repository.getConfig(householdId)
                if (cfg != null && cfg.googleCalendarId > 0L && calendarBridge.hasWritePermission()) {
                    calendarBridge.exportEventToDeviceCalendar(cfg.googleCalendarId, event)
                        .onSuccess { createdId ->
                            if (createdId > 0L) {
                                toSave = event.copy(externalEventId = createdId.toString())
                            }
                        }
                }
            }
            if (repository.saveCalendarEvent(toSave) != true) return@launch
            meshEngine.gossipAfterLocalChange()
            showMessage(
                if (alsoExportToGoogle && toSave.externalEventId.isNotBlank()) {
                    "Saved & synced \"${event.title}\" to Google Calendar"
                } else {
                    "Saved event \"${event.title}\""
                }
            )
        }
    }

    fun deleteCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.deleteCalendarEvent(event)
            meshEngine.gossipAfterLocalChange()
        }
    }

    fun loadDeviceCalendars() {
        viewModelScope.launch {
            val list = calendarBridge.listDeviceCalendars()
            transientState.update { it.copy(deviceCalendars = list) }
            if (list.isEmpty()) {
                showMessage("No visible calendars found on this device.")
            }
        }
    }

    fun selectAndSyncGoogleCalendar(calendar: DeviceCalendarInfo) {
        val householdId = uiState.value.config?.householdId ?: return
        viewModelScope.launch {
            transientState.update { it.copy(isCalendarSyncing = true) }
            repository.updateConfig(householdId) {
                it.copy(googleCalendarId = calendar.id, googleCalendarName = calendar.displayName)
            }
            val result = calendarBridge.importFromDeviceCalendar(calendar.id)
            transientState.update { it.copy(isCalendarSyncing = false) }
            result.fold(
                onSuccess = { events ->
                    repository.upsertImportedEvents(events, householdId)
                    meshEngine.gossipAfterLocalChange()
                    showMessage("Synced ${events.size} event${if (events.size == 1) "" else "s"} from ${calendar.displayName}")
                },
                onFailure = { err ->
                    showMessage(err.message ?: "Calendar sync failed")
                }
            )
        }
    }

    fun syncConfiguredGoogleCalendar() {
        val householdId = uiState.value.config?.householdId ?: return
        viewModelScope.launch {
            val cfg = repository.getConfig(householdId) ?: return@launch
            if (cfg.googleCalendarId <= 0L) {
                loadDeviceCalendars()
                return@launch
            }
            transientState.update { it.copy(isCalendarSyncing = true) }
            val result = calendarBridge.importFromDeviceCalendar(cfg.googleCalendarId)
            transientState.update { it.copy(isCalendarSyncing = false) }
            result.fold(
                onSuccess = { events ->
                    repository.upsertImportedEvents(events, householdId)
                    meshEngine.gossipAfterLocalChange()
                    showMessage("Refreshed ${events.size} Google Calendar events")
                },
                onFailure = { err ->
                    showMessage(err.message ?: "Could not sync calendar")
                }
            )
        }
    }

    fun importIcsFeed(url: String) {
        val householdId = uiState.value.config?.householdId ?: return
        viewModelScope.launch {
            transientState.update { it.copy(isCalendarSyncing = true) }
            repository.updateConfig(householdId) { it.copy(icsFeedUrl = url.trim()) }
            val result = calendarBridge.importFromIcsUrl(url)
            transientState.update { it.copy(isCalendarSyncing = false) }
            result.fold(
                onSuccess = { events ->
                    repository.upsertImportedEvents(events, householdId)
                    meshEngine.gossipAfterLocalChange()
                    showMessage("Imported ${events.size} events from .ics feed")
                },
                onFailure = { err ->
                    showMessage(err.message ?: "Could not import .ics feed")
                }
            )
        }
    }

    fun buildGoogleCalendarExportIntent(event: CalendarEventEntity): Intent =
        calendarBridge.buildInsertCalendarIntent(event)

    // -------------------------------------------------------------------------
    // Mesh Controls & Household Preferences
    // -------------------------------------------------------------------------

    fun syncMeshNow() {
        viewModelScope.launch {
            val count = meshEngine.syncMeshNow()
            if (count > 0) {
                showMessage("Wi-Fi mesh synced with $count peer${if (count == 1) "" else "s"}")
            } else {
                showMessage("Saved locally — no other phones found on Wi-Fi right now")
            }
        }
    }

    fun updateSelfProfile(displayName: String, avatarColorKey: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            repository.updateSelfProfile(displayName, avatarColorKey)
            meshEngine.gossipAfterLocalChange()
            showMessage("Updated profile")
        }
    }

    fun addManualMember(displayName: String, role: MemberRole, avatarColorKey: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            repository.addManualMember(displayName, role, avatarColorKey)
            meshEngine.gossipAfterLocalChange()
            showMessage("Added $displayName to household")
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            repository.updateConfig { it.copy(darkTheme = !it.darkTheme) }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            transientState.update { it.copy(isCheckingUpdate = true) }
            val cfg = repository.getConfig()
            val url = cfg?.updateReleaseUrl?.ifBlank { AppUpdateChecker.DEFAULT_RELEASES_URL }
                ?: AppUpdateChecker.DEFAULT_RELEASES_URL
            val result = AppUpdateChecker.checkGitHubLatestRelease(
                configuredUrl = url,
                currentVersionCode = BuildConfig.VERSION_CODE,
                currentVersionName = BuildConfig.VERSION_NAME
            )
            result.fold(
                onSuccess = { found ->
                    NightlyUpdateScheduler.recordCheckedNow(appContext)
                    transientState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            githubUpdate = found,
                            updateBannerDismissedVersion = if (!silent) null else it.updateBannerDismissedVersion
                        )
                    }
                    if (!silent) {
                        if (found != null) {
                            showMessage("Update v${found.versionName} is ready on GitHub!")
                        } else {
                            showMessage("HomeList v${BuildConfig.VERSION_NAME} is up to date")
                        }
                    }
                },
                onFailure = { err ->
                    transientState.update { it.copy(isCheckingUpdate = false) }
                    if (!silent) {
                        showMessage("Could not check GitHub right now (${err.message ?: "offline"})")
                    }
                }
            )
        }
    }

    fun updateReleaseUrl(url: String) {
        val cleaned = url.trim().ifBlank { AppUpdateChecker.DEFAULT_RELEASES_URL }
        viewModelScope.launch {
            repository.updateConfig { it.copy(updateReleaseUrl = cleaned) }
            meshEngine.gossipAfterLocalChange()
            showMessage("Saved update URL & synced to family mesh")
        }
    }

    fun updateHubEndpoint(rawEndpoint: String) {
        val householdId = uiState.value.config?.householdId ?: return
        val invite = HouseholdInvite.parse(rawEndpoint)
        if (invite != null && invite.householdId != householdId) {
            showMessage("That invite belongs to another household.")
            return
        }
        val endpoint = invite?.endpointId ?: rawEndpoint.trim()
        if (endpoint.isNotEmpty() && !endpoint.matches(Regex("[A-Za-z0-9]{52,64}"))) {
            showMessage("Enter a HomeList hub Endpoint ID or invite link.")
            return
        }
        viewModelScope.launch {
            repository.updateConfig(householdId) { it.copy(hubEndpointId = endpoint) }
            meshEngine.gossipAfterLocalChange()
            showMessage(if (endpoint.isBlank()) "Removed Home Hub" else "Home Hub saved")
        }
    }

    fun dismissUpdateBanner(versionName: String) {
        transientState.update { it.copy(updateBannerDismissedVersion = versionName) }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val snapshot = repository.buildBackupSnapshot()
                        ?: return@withContext Result.failure<String>(IllegalStateException("No family configured yet"))
                    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                    val json = moshi.adapter(HouseholdBackupSnapshot::class.java).toJson(snapshot)
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(StandardCharsets.UTF_8))
                        out.flush()
                    } ?: return@withContext Result.failure<String>(IllegalStateException("Could not open file for writing"))
                    Result.success(snapshot.config.householdName)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.fold(
                onSuccess = { name -> showMessage("Saved backup for $name!") },
                onFailure = { err -> showMessage("Backup failed: ${err.message ?: "could not write file"}") }
            )
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val json = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(StandardCharsets.UTF_8)
                    } ?: return@withContext Result.failure<String>(IllegalStateException("Could not read selected file"))
                    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                    val snapshot = moshi.adapter(HouseholdBackupSnapshot::class.java).fromJson(json)
                        ?: return@withContext Result.failure<String>(IllegalStateException("File is not a valid HomeList backup"))
                    val householdId = snapshot.config.householdId
                    val oldAlarms = repository.getAllChores(householdId).map { it.id } +
                        repository.getAllReminders(householdId).map { it.id }
                    val restored = repository.restoreBackupSnapshot(snapshot)
                    oldAlarms.forEach { ReminderNotificationHelper.cancelScheduled(appContext, it.hashCode()) }
                    Result.success(restored.householdName)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.fold(
                onSuccess = { name ->
                    closeHouseholdSetup()
                    MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
                    rescheduleAllNotifications()
                    refreshLocationChecks()
                    showMessage("Restored $name from backup!")
                },
                onFailure = { err ->
                    showMessage("Restore failed: ${err.message ?: "invalid backup file"}")
                }
            )
        }
    }

    fun leaveHousehold(householdId: String) {
        viewModelScope.launch {
            val household = repository.getHousehold(householdId) ?: return@launch
            val alarms = repository.getAllChores(householdId).map { it.id } +
                repository.getAllReminders(householdId).map { it.id }
            repository.leaveHousehold(householdId)
            alarms.forEach { ReminderNotificationHelper.cancelScheduled(appContext, it.hashCode()) }
            if (repository.getAllHouseholds().isEmpty()) MeshBackgroundSyncJobService.cancelPeriodicSync(appContext)
            syncLocationMonitors()
            showMessage("Left ${household.householdName}")
        }
    }
}
