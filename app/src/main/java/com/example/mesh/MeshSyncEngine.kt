package com.example.mesh

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.GossipMergeResult
import com.example.data.HomeListDatabase
import com.example.data.HomeListRepository
import com.example.reminders.LocationReminderManager
import com.example.reminders.ReminderNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal suspend fun <T> MeshTransport.withEndpoint(block: suspend () -> T): T {
    var started = false
    try {
        withContext(NonCancellable) {
            start()
            started = true
        }
        return block()
    } finally {
        if (started) withContext(NonCancellable) { stop() }
    }
}

internal class MeshRuntime(
    context: Context,
    val repository: HomeListRepository,
    transport: MeshTransport? = null
) {
    private val app = context.applicationContext
    val locationManager = LocationReminderManager(app)
    val transport = transport ?: IrohMeshTransport(app, repository, ::notifyChanges, ::onHomePath)
    private val shoppingMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val shoppingSummaries = shoppingMessages.asSharedFlow()
    private val syncLock = Mutex()
    private val requests = Semaphore(3)

    suspend fun sync(): MeshPassResult = syncLock.withLock {
        transport.withEndpoint { syncHouseholds() }
    }

    private suspend fun syncHouseholds(): MeshPassResult = coroutineScope {
        val self = transport.localEndpointId()
        val device = repository.getDeviceConfig()
        val tasks = repository.getAllHouseholds().flatMap { house ->
            val id = house.householdId
            val snapshot = repository.buildGossipPayload(id) ?: return@flatMap emptyList()
            val targets = buildList {
                add(house.hubEndpointId)
                addAll(snapshot.peers.sortedByDescending { it.isFounder }
                    .filter { it.nodeId != device?.nodeId }.map { it.endpointId })
            }.filter { it.isNotBlank() && it != self }.distinct()
            targets.map { endpoint ->
                async {
                    requests.withPermit {
                        try {
                            val response = transport.exchangeGossip(
                                endpoint, GossipRequestEnvelope(householdId = id, pairingCode = house.pairingCode, payload = snapshot)
                            )
                            val payload = response.payload
                            if (!response.ok || payload == null || payload.householdId != id) return@withPermit null
                            notifyChanges(repository.mergeGossipPayload(id, payload))
                            id to (endpoint == house.hubEndpointId)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.d(TAG, "Peer sync skipped: ${error.javaClass.simpleName}")
                            null
                        }
                    }
                }
            }
        }
        val successes = tasks.awaitAll().filterNotNull()
        MeshPassResult(
            successes.groupingBy { it.first }.eachCount(),
            successes.filter { it.second }.map { it.first }.toSet()
        )
    }

    private suspend fun notifyChanges(result: GossipMergeResult) {
        if (result.newReminders.isEmpty() && result.fulfilledShoppingItems.isEmpty()) return
        ReminderNotificationHelper.notifyIncomingMeshItems(
            context = app,
            newReminders = result.newReminders,
            fulfilledShoppingItems = result.fulfilledShoppingItems,
            householdName = result.householdName.takeIf { repository.getAllHouseholds().size > 1 },
            onShoppingSummary = { shoppingMessages.tryEmit(it) }
        )
    }

    private suspend fun onHomePath(householdId: String) {
        val house = repository.getHousehold(householdId) ?: return
        locationManager.evaluateHomeWifiArrival(
            repository.getAllPlaces(householdId), repository.getAllReminders(householdId), house.selfMemberId
        ).forEach { repository.markReminderFired(it) }
    }

    companion object {
        private const val TAG = "HomeListMesh"
        @Volatile private var instance: MeshRuntime? = null

        fun get(context: Context): MeshRuntime = instance ?: synchronized(this) {
            instance ?: MeshRuntime(
                context, HomeListRepository(HomeListDatabase.getInstance(context.applicationContext))
            ).also { instance = it }
        }
    }
}

internal data class MeshPassResult(val peerCounts: Map<String, Int>, val connectedHubs: Set<String>) {
    val successCount: Int get() = peerCounts.values.sum()
}

internal class MeshSyncEngine(private val runtime: MeshRuntime) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = MutableStateFlow(false)
    private val _status = MutableStateFlow(MeshRuntimeStatus())
    val status = _status.asStateFlow()
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foregroundWorker = scope.launch {
        foreground.collectLatest { isForeground ->
            if (!isForeground) return@collectLatest
            while (true) {
                try {
                    runtime.transport.withEndpoint {
                        _status.update { it.copy(isOnline = true) }
                        while (true) {
                            syncMeshNow()
                            delay(60_000)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _status.update { it.copy(lastSyncMessage = "Could not start peer sync") }
                } finally {
                    _status.update { it.copy(isOnline = false, connectedHubs = emptySet()) }
                }
                delay(60_000)
            }
        }
    }
    private var pendingSync: kotlinx.coroutines.Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) { foreground.value = true }
    override fun onStop(owner: LifecycleOwner) { foreground.value = false }

    fun close() {
        lifecycle.removeObserver(this)
        foregroundWorker.cancel()
        pendingSync?.cancel()
    }

    fun gossipAfterLocalChange() {
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(300)
            if (foreground.value) syncMeshNow()
        }
    }

    suspend fun syncMeshNow(householdId: String? = null): Int {
        _status.update { it.copy(isSyncing = true, lastSyncMessage = "Syncing households...") }
        return try {
            val result = runtime.sync()
            _status.update { it.copy(
                lastSyncMessage = if (result.successCount == 0) "No peers reached" else "Synced with ${result.successCount} peer connections",
                connectedHubs = result.connectedHubs
            ) }
            if (householdId == null) result.successCount else result.peerCounts[householdId] ?: 0
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _status.update { it.copy(lastSyncMessage = "Peer sync unavailable") }
            0
        } finally {
            _status.update { it.copy(isSyncing = false) }
        }
    }

    suspend fun joinViaEndpoint(invite: HouseholdInvite): MeshGossipPayload = runtime.transport.withEndpoint {
        val response = runtime.transport.exchangeGossip(
            invite.endpointId, GossipRequestEnvelope(householdId = invite.householdId, pairingCode = invite.pairingCode)
        )
        response.payload?.takeIf { response.ok && it.householdId == invite.householdId }
            ?: error(when (response.error) {
                "invalid_pairing_code" -> "The pairing code was rejected. Ask for a fresh invite."
                "unknown_household" -> "The peer no longer hosts this household."
                else -> "Could not join the household. Check the invite and try again."
            })
    }
}
