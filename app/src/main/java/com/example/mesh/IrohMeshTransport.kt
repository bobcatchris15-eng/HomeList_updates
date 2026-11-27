package com.example.mesh

import android.content.Context
import android.util.Log
import com.example.data.GossipMergeResult
import com.example.data.HomeListRepository
import com.squareup.moshi.Moshi
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointId
import computer.iroh.EndpointOptions
import computer.iroh.Incoming
import computer.iroh.IrohAndroid
import computer.iroh.RecvStream
import computer.iroh.SendStream
import computer.iroh.presetN0
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer

internal class IrohMeshTransport(
    context: Context,
    private val repository: HomeListRepository,
    private val onChanges: suspend (GossipMergeResult) -> Unit,
    private val onHomePath: suspend (String) -> Unit
) : MeshTransport {
    private val app = context.applicationContext
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        Log.w(TAG, "Peer listener ended: ${error.javaClass.simpleName}")
    })
    private val moshi = Moshi.Builder().build()
    private val requests = moshi.adapter(GossipRequestEnvelope::class.java)
    private val responses = moshi.adapter(GossipResponseEnvelope::class.java)
    private var users = 0
    private var endpoint: Endpoint? = null
    private var worker: Job? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (users == 0) {
                val bound = bindEndpoint()
                try {
                    repository.setEndpointId(bound.id().use { it.toString() })
                } catch (error: Throwable) {
                    withContext(NonCancellable) { bound.use { it.shutdown() } }
                    throw error
                }
                endpoint = bound
                worker = scope.launch { acceptConnections(bound) }
            }
            users++
            Unit
        }
    }

    private suspend fun bindEndpoint(): Endpoint {
        try {
            IrohAndroid.installAndroidContext(app)
            val key = IrohKeyStore(app).loadOrCreate()
            try {
                val options = EndpointOptions(secretKey = key, preset = presetN0(), alpns = listOf(ALPN))
                try {
                    return Endpoint.bind(options)
                } finally {
                    options.destroy()
                }
            } finally {
                key.fill(0)
            }
        } catch (error: LinkageError) {
            throw IllegalStateException("HomeList's native peer sync library could not load on this device", error)
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO + NonCancellable) {
        lock.withLock {
            if (users == 0) return@withLock
            users--
            if (users == 0) {
                val old = endpoint
                endpoint = null
                worker?.cancelAndJoin()
                worker = null
                old?.use { it.shutdown() }
            }
        }
    }

    private suspend fun runningEndpoint(): Endpoint = lock.withLock { checkNotNull(endpoint) }

    override suspend fun localEndpointId(): String = runningEndpoint().id().use { it.toString() }

    override suspend fun exchangeGossip(
        peerEndpointId: String, request: GossipRequestEnvelope
    ): GossipResponseEnvelope = try {
        withContext(Dispatchers.IO) {
            withTimeout(EXCHANGE_TIMEOUT_MS) {
                val current = runningEndpoint()
                EndpointId.fromString(peerEndpointId).use { id ->
                    EndpointAddr(id, null, emptyList()).use { addr ->
                        current.connect(addr, ALPN).use { connection ->
                            try {
                                connection.openBi().use { stream ->
                                    stream.send().use { send -> writeFrame(send, requests.toJson(request)) }
                                    val response = stream.recv().use { recv -> responses.fromJson(readFrame(recv)) }
                                        ?: error("Empty sync response")
                                    val payload = response.payload
                                    if (response.ok) {
                                        val remoteId = connection.remoteId().use { it.toString() }
                                        check(payload != null && payload.householdId == request.householdId &&
                                            payload.protocolVersion == 2 && payload.senderEndpointId == remoteId) {
                                            "The peer sent an invalid household snapshot"
                                        }
                                        if (request.payload != null && connection.paths().any {
                                            it.isIp && it.isSelected && isPrivateIpv4(it.remoteAddr)
                                        }) {
                                            onHomePath(request.householdId)
                                        }
                                    }
                                    response
                                }
                            } finally {
                                connection.close(0L, byteArrayOf())
                            }
                        }
                    }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        throw java.io.IOException("HomeList sync timed out")
    }

    private suspend fun acceptConnections(endpoint: Endpoint) = coroutineScope {
        val slots = Semaphore(8)
        while (true) {
            slots.acquire()
            val incoming = try {
                endpoint.acceptNext()
            } catch (error: Throwable) {
                slots.release()
                throw error
            }
            if (incoming == null) {
                slots.release()
                break
            }
            val job = launch {
                try {
                    withTimeout(EXCHANGE_TIMEOUT_MS) { receive(incoming) }
                } catch (_: TimeoutCancellationException) {
                    // The peer may disappear between discovery and opening a stream.
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.d(TAG, "Incoming sync ended: ${error.javaClass.simpleName}")
                }
            }
            job.invokeOnCompletion { incoming.close(); slots.release() }
        }
    }

    private suspend fun receive(incoming: Incoming) {
        incoming.accept().use { accepting ->
            accepting.connect().use { connection ->
                try {
                    connection.acceptBi().use { stream ->
                        val remote = connection.remoteId().use { it.toString() }
                        val request = try {
                            stream.recv().use { requests.fromJson(readFrame(it)) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                        val outcome = if (request == null ||
                            (request.payload != null && request.payload.senderEndpointId != remote)
                        ) null else repository.handleIncomingGossip(request)
                        stream.send().use { send ->
                            writeFrame(send, responses.toJson(outcome?.response ?: GossipResponseEnvelope(false, "bad_request")))
                            outcome?.changes?.let { onChanges(it) }
                            // Wait for the peer to read the response before closing the connection.
                            send.stopped()
                        }
                    }
                } finally {
                    connection.close(0L, byteArrayOf())
                }
            }
        }
    }

    private suspend fun writeFrame(stream: SendStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME_BYTES) { "HomeList snapshot is too large" }
        stream.writeAll(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        stream.writeAll(bytes)
        stream.finish()
    }

    private suspend fun readFrame(stream: RecvStream): String {
        val length = ByteBuffer.wrap(stream.readExact(Int.SIZE_BYTES.toUInt())).int
        require(length in 1..MAX_FRAME_BYTES) { "Invalid HomeList frame length" }
        return stream.readExact(length.toUInt()).decodeToString(throwOnInvalidSequence = true)
    }

    private fun isPrivateIpv4(address: String): Boolean {
        val parts = address.substringBeforeLast(':').split('.').mapNotNull(String::toIntOrNull)
        return parts.size == 4 && parts.all { it in 0..255 } &&
            (parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 192 && parts[1] == 168))
    }

    private companion object {
        const val TAG = "HomeListIroh"
        const val EXCHANGE_TIMEOUT_MS = 15_000L
        const val MAX_FRAME_BYTES = 16 * 1024 * 1024
        val ALPN = "homelist/gossip/2".toByteArray(Charsets.UTF_8)
    }
}
