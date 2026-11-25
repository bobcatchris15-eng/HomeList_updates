package com.example.mesh

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.data.HomeListDatabase
import com.example.data.HomeListRepository
import com.example.reminders.LocationReminderManager
import com.example.reminders.ReminderNotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Play-compliant periodic background sync using Android's native JobScheduler.
 * Runs roughly every 15 minutes whenever any network (home Wi-Fi or mobile data) is available,
 * syncing chores, shopping items, nudges, and reminders with saved LAN peers and the optional
 * Home Hub without requiring a foreground service or special permissions.
 */
class MeshBackgroundSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val gossipAdapter = moshi.adapter(MeshGossipPayload::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .writeTimeout(7, TimeUnit.SECONDS)
        .build()

    override fun onStartJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                performBackgroundSync()
            } catch (e: Exception) {
                Log.w(TAG, "Background mesh sync skipped: ${e.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }

    private suspend fun performBackgroundSync() {
        val db = HomeListDatabase.getInstance(applicationContext)
        val repository = HomeListRepository(db.dao())
        val config = repository.getConfig() ?: return

        val addresses = NetworkAddressDetector.detect()
        val initialPayload = repository.buildGossipPayload() ?: return
        var sawLanPeer = false

        // 1. Try optional Home Hub first (works both at home and away on cellular)
        val hubBase = MeshSyncEngine.normalizeHubBaseUrl(config.hubUrl)
        if (hubBase.isNotBlank()) {
            gossipWithUrl(
                url = "$hubBase/api/v1/gossip",
                pairingCode = config.pairingCode,
                householdId = config.householdId,
                payload = initialPayload,
                repository = repository
            )
        }

        // 2. Also gossip with saved LAN peers (e.g., family phones or Windows Hub on home Wi-Fi)
        val savedPeers = repository.getAllPeers()
            .filter { it.nodeId != config.nodeId && it.lanIp.isNotBlank() && it.lanIp != addresses.lanIp }
            .sortedByDescending { it.isFounder }

        val refreshedPayload = repository.buildGossipPayload() ?: initialPayload
        val seenEndpoints = hashSetOf<String>()
        for (peer in savedPeers) {
            val endpoint = "${peer.lanIp}:${peer.port}"
            if (!seenEndpoints.add(endpoint)) continue
            val ok = gossipWithUrl(
                url = "http://${peer.lanIp}:${peer.port}/api/v1/gossip",
                pairingCode = config.pairingCode,
                householdId = config.householdId,
                payload = refreshedPayload,
                repository = repository
            )
            if (ok && NetworkAddressDetector.isLanAddress(peer.lanIp)) {
                sawLanPeer = true
            }
        }

        if (sawLanPeer) {
            val places = repository.getAllPlaces()
            val reminders = repository.getAllReminders()
            val fired = LocationReminderManager(applicationContext)
                .evaluateHomeWifiArrival(places, reminders, config.selfMemberId)
            fired.forEach { repository.markReminderFired(it) }
        }
    }

    private suspend fun gossipWithUrl(
        url: String,
        pairingCode: String,
        householdId: String,
        payload: MeshGossipPayload,
        repository: HomeListRepository
    ): Boolean {
        return try {
            val bodyJson = gossipAdapter.toJson(payload)
            val req = Request.Builder()
                .url(url)
                .header("X-HomeList-Code", pairingCode)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val responseText = res.body?.string().orEmpty()
                val incoming = gossipAdapter.fromJson(responseText) ?: return false
                if (incoming.householdId.isNotBlank() && incoming.householdId != householdId) {
                    return false
                }
                val mergeResult = repository.mergeGossipPayload(incoming)
                if (mergeResult.newReminders.isNotEmpty() || mergeResult.fulfilledShoppingItems.isNotEmpty()) {
                    ReminderNotificationHelper.notifyIncomingMeshItems(
                        context = applicationContext,
                        newReminders = mergeResult.newReminders,
                        fulfilledShoppingItems = mergeResult.fulfilledShoppingItems
                    )
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "MeshBackgroundSync"
        private const val JOB_ID = 7865
        private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun schedulePeriodicSync(context: Context) {
            val appCtx = context.applicationContext
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return

            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(appCtx, MeshBackgroundSyncJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(SYNC_INTERVAL_MS)
                .setBackoffCriteria(5 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()

            try {
                scheduler.schedule(jobInfo)
            } catch (e: Exception) {
                Log.w(TAG, "Could not schedule background sync job: ${e.message}")
            }
        }

        fun cancelPeriodicSync(context: Context) {
            val scheduler = context.applicationContext
                .getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }
    }
}
