package com.example.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.data.GroceryItemEntity
import com.example.data.HomeListRepository
import com.example.data.ReminderEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MeshSyncEngine(
    context: Context,
    private val repository: HomeListRepository,
    private val onIncomingReminders: (List<ReminderEntity>) -> Unit,
    private val onFulfilledShoppingItems: (List<GroceryItemEntity>) -> Unit,
    private val onHomeWifiPeerSeen: () -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val gossipAdapter = moshi.adapter(MeshGossipPayload::class.java)
    private val healthAdapter = moshi.adapter(NodeHealthPayload::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val server = MeshNodeServer(
        repository = repository,
        scope = scope,
        onIncomingReminders = onIncomingReminders,
        onFulfilledShoppingItems = onFulfilledShoppingItems,
        onPeerGossipReceived = { remoteIp ->
            _status.update {
                it.copy(
                    lastSyncTimeMs = System.currentTimeMillis(),
                    lastSyncMessage = if (NetworkAddressDetector.isTailscaleAddress(remoteIp)) {
                        "Private check-in synced"
                    } else {
                        "Wi-Fi peer synced"
                    }
                )
            }
            if (NetworkAddressDetector.isLanAddress(remoteIp)) {
                onHomeWifiPeerSeen()
            }
        }
    )

    private val nsdManager: NsdManager? =
        appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var checkInTimerJob: Job? = null
    private var registeredServiceName: String? = null

    private val _status = MutableStateFlow(MeshRuntimeStatus())
    val status: StateFlow<MeshRuntimeStatus> = _status.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredMeshNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredMeshNode>> = _discoveredNodes.asStateFlow()

    fun startDiscoveryForOnboarding() {
        refreshLocalAddresses()
        startMdnsDiscovery()
    }

    fun startMeshNode() {
        scope.launch {
            val config = repository.getConfig() ?: return@launch
            val boundPort = server.start(config.listenPort)
            if (boundPort > 0 && boundPort != config.listenPort) {
                repository.updateConfig { it.copy(listenPort = boundPort) }
            }
            val addresses = NetworkAddressDetector.detect()
            repository.refreshSelfPeerAddresses()

            _status.update {
                it.copy(
                    serverRunning = boundPort > 0,
                    listenPort = if (boundPort > 0) boundPort else config.listenPort,
                    lanIp = addresses.lanIp,
                    tailscaleIp = addresses.tailscaleIp,
                    lastSyncMessage = if (boundPort > 0) "Mesh node active" else "Offline (local)"
                )
            }

            if (boundPort > 0) {
                registerMdnsService(config.nodeId, boundPort)
            }
            startMdnsDiscovery()
            startFounderCheckInLoop()
            syncMeshNow(silent = true)
        }
    }

    fun stopMeshNode() {
        checkInTimerJob?.cancel()
        checkInTimerJob = null
        stopMdnsDiscovery()
        unregisterMdnsService()
        server.stop()
        releaseMulticastLock()
        _status.update { it.copy(serverRunning = false) }
    }

    fun refreshLocalAddresses() {
        scope.launch {
            val addresses = NetworkAddressDetector.detect()
            repository.refreshSelfPeerAddresses()
            _status.update {
                it.copy(lanIp = addresses.lanIp, tailscaleIp = addresses.tailscaleIp)
            }
        }
    }

    fun gossipAfterLocalChange() {
        scope.launch {
            syncMeshNow(silent = true)
        }
    }

    fun restartCheckInTimer() {
        startFounderCheckInLoop()
    }

    private fun startFounderCheckInLoop() {
        checkInTimerJob?.cancel()
        checkInTimerJob = scope.launch {
            while (isActive) {
                val config = repository.getConfig()
                val intervalMin = config?.checkInIntervalMinutes ?: 0
                if (config == null || intervalMin <= 0) {
                    _status.update { it.copy(nextCheckInLabel = "Manual sync") }
                    delay(60_000L)
                    continue
                }
                _status.update {
                    it.copy(
                        nextCheckInLabel = if (config.isFounder) {
                            if (config.acceptPrivateCheckIns) "Accepting check-ins" else "Private window paused"
                        } else {
                            "Check-in every ${intervalMin}m"
                        }
                    )
                }
                delay(intervalMin * 60_000L)
                syncMeshNow(silent = true)
            }
        }
    }

    suspend fun probePeerHealth(host: String, port: Int): Result<NodeHealthPayload> =
        withContext(Dispatchers.IO) {
            try {
                val cleanHost = normalizeHost(host)
                val req = Request.Builder()
                    .url("http://$cleanHost:$port/api/v1/health")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("Peer returned HTTP ${res.code}"))
                    }
                    val parsed = healthAdapter.fromJson(body)
                        ?: return@withContext Result.failure(IllegalStateException("Invalid peer response"))
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun joinViaPeer(
        host: String,
        port: Int,
        pairingCode: String
    ): Result<MeshGossipPayload> = withContext(Dispatchers.IO) {
        try {
            val cleanHost = normalizeHost(host)
            val emptyBody = "".toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("http://$cleanHost:$port/api/v1/gossip")
                .header("X-HomeList-Code", pairingCode.trim().uppercase())
                .post(emptyBody)
                .build()
            httpClient.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.code == 401) {
                    return@withContext Result.failure(IllegalArgumentException("Incorrect 6-character pairing code"))
                }
                if (res.code == 403) {
                    return@withContext Result.failure(IllegalStateException("Founder private check-in window is currently paused"))
                }
                if (!res.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Peer returned HTTP ${res.code}"))
                }
                val parsed = gossipAdapter.fromJson(body)
                    ?: return@withContext Result.failure(IllegalStateException("Could not read household snapshot"))
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncMeshNow(silent: Boolean = false): Int = withContext(Dispatchers.IO) {
        val config = repository.getConfig() ?: return@withContext 0
        val addresses = NetworkAddressDetector.detect()
        repository.refreshSelfPeerAddresses()
        _status.update {
            it.copy(
                isSyncing = true,
                lanIp = addresses.lanIp,
                tailscaleIp = addresses.tailscaleIp
            )
        }

        val payload = repository.buildGossipPayload()
        if (payload == null) {
            _status.update { it.copy(isSyncing = false) }
            return@withContext 0
        }

        // Gather all candidate endpoints: mDNS Wi-Fi peers + saved Peer Address Book (LAN, Tailscale, custom host)
        val targets = linkedSetOf<Pair<String, Int>>()
        _discoveredNodes.value
            .filter { it.householdId.isBlank() || it.householdId == config.householdId }
            .forEach { node ->
                if (node.host.isNotBlank() && node.host != addresses.lanIp) {
                    targets.add(node.host to node.port)
                }
            }

        val savedPeers = repository.getAllPeers().filter { it.nodeId != config.nodeId }
        // Prioritize Founder endpoints first for timed private check-ins
        savedPeers.sortedByDescending { it.isFounder }.forEach { peer ->
            if (peer.lanIp.isNotBlank() && peer.lanIp != addresses.lanIp) {
                targets.add(peer.lanIp to peer.port)
            }
            if (peer.tailscaleIp.isNotBlank() && peer.tailscaleIp != addresses.tailscaleIp) {
                targets.add(peer.tailscaleIp to peer.port)
            }
            if (peer.customPrivateHost.isNotBlank()) {
                targets.add(normalizeHost(peer.customPrivateHost) to peer.port)
            }
        }

        var syncedCount = 0
        var sawLanPeer = false
        val jsonBody = gossipAdapter.toJson(payload)

        for ((host, port) in targets) {
            try {
                val req = Request.Builder()
                    .url("http://$host:$port/api/v1/gossip")
                    .header("X-HomeList-Code", config.pairingCode)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val responseText = res.body?.string().orEmpty()
                        val incoming = gossipAdapter.fromJson(responseText)
                        if (incoming != null && incoming.householdId == config.householdId) {
                            val mergeResult = repository.mergeGossipPayload(incoming)
                            if (mergeResult.newReminders.isNotEmpty()) {
                                onIncomingReminders(mergeResult.newReminders)
                            }
                            if (mergeResult.fulfilledShoppingItems.isNotEmpty()) {
                                onFulfilledShoppingItems(mergeResult.fulfilledShoppingItems)
                            }
                            syncedCount++
                            if (NetworkAddressDetector.isLanAddress(host)) {
                                sawLanPeer = true
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Peer unreachable right now; continue gossiping with remaining peers
            }
        }

        if (sawLanPeer) {
            onHomeWifiPeerSeen()
        }

        val now = System.currentTimeMillis()
        _status.update {
            it.copy(
                isSyncing = false,
                activeWifiPeers = _discoveredNodes.value.size,
                lastSyncTimeMs = if (syncedCount > 0) now else it.lastSyncTimeMs,
                lastSyncMessage = when {
                    syncedCount > 0 -> "Synced with $syncedCount peer${if (syncedCount == 1) "" else "s"}"
                    targets.isEmpty() -> if (config.isFounder) "Waiting for family phones" else "No peers reachable"
                    !silent -> "Saved locally • peers offline"
                    else -> it.lastSyncMessage
                }
            )
        }
        syncedCount
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("HomeListMeshNsd")?.apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun registerMdnsService(nodeId: String, port: Int) {
        val manager = nsdManager ?: return
        unregisterMdnsService()
        val shortId = nodeId.take(8)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "HomeList-$shortId"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registeredServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "registerService error: ${e.message}")
        }
    }

    private fun unregisterMdnsService() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        registrationListener = null
        try {
            manager.unregisterService(listener)
        } catch (_: Exception) {
        }
    }

    private fun startMdnsDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return
        acquireMulticastLock()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == registeredServiceName) return
                resolveDiscoveredService(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discoveredNodes.update { list ->
                    list.filterNot { it.serviceName == serviceInfo.serviceName }
                }
                _status.update { it.copy(activeWifiPeers = _discoveredNodes.value.size) }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopMdnsDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopMdnsDiscovery()
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices error: ${e.message}")
            discoveryListener = null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDiscoveredService(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.registerServiceInfoCallback(
                    serviceInfo,
                    { runnable -> scope.launch { runnable.run() } },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                        override fun onServiceUpdated(info: NsdServiceInfo) {
                            val host = info.hostAddresses.firstOrNull { !it.isLoopbackAddress }?.hostAddress
                            if (!host.isNullOrBlank() && info.port > 0) {
                                handleResolvedPeer(info.serviceName, host, info.port)
                            }
                            try {
                                manager.unregisterServiceInfoCallback(this)
                            } catch (_: Exception) {
                            }
                        }

                        override fun onServiceLost() {}
                        override fun onServiceInfoCallbackUnregistered() {}
                    }
                )
            } else {
                manager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress
                            if (!host.isNullOrBlank() && info.port > 0) {
                                handleResolvedPeer(info.serviceName, host, info.port)
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveDiscoveredService error: ${e.message}")
        }
    }

    private fun handleResolvedPeer(serviceName: String, host: String, port: Int) {
        scope.launch {
            val selfConfig = repository.getConfig()
            val health = probePeerHealth(host, port).getOrNull()
            if (health != null && selfConfig != null && health.nodeId == selfConfig.nodeId) {
                return@launch // Ignore self broadcast
            }
            val node = DiscoveredMeshNode(
                serviceName = serviceName,
                host = host,
                port = port,
                householdName = health?.householdName ?: "HomeList Phone",
                householdId = health?.householdId.orEmpty(),
                displayName = health?.displayName ?: serviceName,
                deviceType = health?.deviceType ?: "PHONE",
                isFounder = health?.isFounder ?: false
            )
            _discoveredNodes.update { existing ->
                (existing.filterNot { it.host == host && it.port == port } + node)
            }
            _status.update { it.copy(activeWifiPeers = _discoveredNodes.value.size) }

            // If we already belong to this household, immediately trigger Wi-Fi gossip!
            if (selfConfig != null &&
                (node.householdId.isBlank() || node.householdId == selfConfig.householdId)
            ) {
                onHomeWifiPeerSeen()
                syncMeshNow(silent = true)
            }
        }
    }

    private fun stopMdnsDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "MeshSyncEngine"
        const val SERVICE_TYPE = "_homelist._tcp."
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeHost(raw: String): String {
            return raw.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("homelist://")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore(":")
                .trim()
        }

        fun parsePairingUri(input: String): Triple<String, Int, String>? {
            val trimmed = input.trim()
            if (!trimmed.startsWith("homelist://", ignoreCase = true)) return null
            val withoutScheme = trimmed.substringAfter("://")
            val hostPort = withoutScheme.substringBefore("?")
            val query = withoutScheme.substringAfter("?", "")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "8765").toIntOrNull() ?: 8765
            val code = query.split("&")
                .mapNotNull { kv ->
                    val parts = kv.split("=")
                    if (parts.size == 2 && parts[0].equals("code", ignoreCase = true)) parts[1] else null
                }
                .firstOrNull()
                ?.uppercase()
                .orEmpty()
            return if (host.isNotBlank()) Triple(host, port, code) else null
        }
    }
}
