package com.example.mesh

import android.util.Log
import com.example.BuildConfig
import com.example.data.DateStreakUtils
import com.example.data.GroceryItemEntity
import com.example.data.HomeListRepository
import com.example.data.MealSlot
import com.example.data.ReminderEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Lightweight embedded HTTP/1.1 Mesh Node server running on every HomeList phone.
 * Exposes open JSON endpoints for Android peers, future Windows apps, and Raspberry Pi displays:
 *  - GET  /api/v1/health
 *  - POST /api/v1/gossip        (Header: X-HomeList-Code)
 *  - GET  /api/v1/wall-snapshot
 *  - GET  /                     (Built-in read-only warm HTML Wall Board)
 */
class MeshNodeServer(
    private val repository: HomeListRepository,
    private val scope: CoroutineScope,
    private val onIncomingReminders: (List<ReminderEntity>) -> Unit,
    private val onFulfilledShoppingItems: (List<GroceryItemEntity>) -> Unit,
    private val onPeerGossipReceived: (String) -> Unit
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val gossipAdapter = moshi.adapter(MeshGossipPayload::class.java)
    private val healthAdapter = moshi.adapter(NodeHealthPayload::class.java)
    private val wallAdapter = moshi.adapter(WallSnapshotPayload::class.java)

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false && acceptJob?.isActive == true

    fun start(port: Int = 8765): Int {
        if (isRunning) return serverSocket?.localPort ?: port
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            try {
                socket.bind(InetSocketAddress(port))
            } catch (_: Exception) {
                socket.bind(InetSocketAddress(0))
            }
            serverSocket = socket
            val boundPort = socket.localPort
            acceptJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.w(TAG, "Accept loop error: ${e.message}")
                        }
                    }
                }
            }
            boundPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MeshNodeServer", e)
            -1
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val remoteIp = client.inetAddress?.hostAddress.orEmpty()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
            val bodyBytes = if (contentLength > 0) {
                readExactly(input, contentLength)
            } else {
                ByteArray(0)
            }

            when {
                method == "GET" && rawPath == "/api/v1/health" -> handleHealth(output)
                method == "POST" && rawPath == "/api/v1/gossip" -> handleGossip(output, headers, bodyBytes, remoteIp)
                method == "GET" && rawPath == "/api/v1/wall-snapshot" -> handleWallSnapshot(output)
                method == "GET" && (rawPath == "/" || rawPath == "/wall") -> {
                    writeResponse(output, 200, "OK", "text/html; charset=utf-8", WALL_DISPLAY_HTML.toByteArray(StandardCharsets.UTF_8))
                }
                else -> {
                    writeJson(output, 404, "Not Found", """{"error":"not_found"}""")
                }
            }
        }
    }

    private suspend fun handleHealth(output: OutputStream) {
        val config = repository.getConfig()
        if (config == null) {
            writeJson(output, 503, "Service Unavailable", """{"error":"unconfigured"}""")
            return
        }
        val selfMember = repository.getAllMembers().firstOrNull { it.id == config.selfMemberId }
        val payload = NodeHealthPayload(
            nodeId = config.nodeId,
            householdId = config.householdId,
            householdName = config.householdName,
            displayName = selfMember?.displayName ?: "HomeList Node",
            deviceType = config.deviceType,
            isFounder = config.isFounder,
            port = serverSocket?.localPort ?: config.listenPort,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME
        )
        writeJson(output, 200, "OK", healthAdapter.toJson(payload))
    }

    private suspend fun handleGossip(
        output: OutputStream,
        headers: Map<String, String>,
        bodyBytes: ByteArray,
        remoteIp: String
    ) {
        val config = repository.getConfig()
        if (config == null) {
            writeJson(output, 503, "Service Unavailable", """{"error":"unconfigured"}""")
            return
        }

        // Verify shared Household Pairing Code using constant-time comparison
        val providedCode = headers["x-homelist-code"]?.trim()?.uppercase().orEmpty()
        val expectedCode = config.pairingCode.trim().uppercase()
        val authorized = providedCode.isNotEmpty() && MessageDigest.isEqual(
            providedCode.toByteArray(StandardCharsets.UTF_8),
            expectedCode.toByteArray(StandardCharsets.UTF_8)
        )
        if (!authorized) {
            writeJson(output, 401, "Unauthorized", """{"error":"invalid_pairing_code"}""")
            return
        }

        // Enforce Founder Private Check-In window toggle for non-LAN (Tailscale/private) callers
        val isLanCaller = NetworkAddressDetector.isLanAddress(remoteIp)
        if (!isLanCaller && !config.acceptPrivateCheckIns) {
            writeJson(output, 403, "Forbidden", """{"error":"private_checkins_paused"}""")
            return
        }

        val bodyText = bodyBytes.toString(StandardCharsets.UTF_8)
        if (bodyText.isNotBlank()) {
            try {
                val incoming = gossipAdapter.fromJson(bodyText)
                if (incoming != null &&
                    (incoming.householdId.isBlank() || incoming.householdId == config.householdId)
                ) {
                    val mergeResult = repository.mergeGossipPayload(incoming)
                    if (mergeResult.newReminders.isNotEmpty()) {
                        onIncomingReminders(mergeResult.newReminders)
                    }
                    if (mergeResult.fulfilledShoppingItems.isNotEmpty()) {
                        onFulfilledShoppingItems(mergeResult.fulfilledShoppingItems)
                    }
                    onPeerGossipReceived(remoteIp)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Malformed gossip payload: ${e.message}")
                writeJson(output, 400, "Bad Request", """{"error":"invalid_payload"}""")
                return
            }
        }

        val responsePayload = repository.buildGossipPayload()
        if (responsePayload == null) {
            writeJson(output, 503, "Service Unavailable", """{"error":"unconfigured"}""")
            return
        }
        writeJson(output, 200, "OK", gossipAdapter.toJson(responsePayload))
    }

    private suspend fun handleWallSnapshot(output: OutputStream) {
        val config = repository.getConfig()
        if (config == null) {
            writeJson(output, 503, "Service Unavailable", """{"error":"unconfigured"}""")
            return
        }
        val members = repository.getAllMembers().filter { !it.isDeleted }
        val chores = repository.getAllChores().filter { !it.isDeleted }
        val completions = repository.getAllCompletions()
        val peers = repository.getAllPeers()
        val events = repository.getAllEvents().filter { !it.isDeleted }

        val week = DateStreakUtils.weekSlots(0)
        val completedIds = completions.filter { it.isCompleted }.map { it.id }.toSet()
        val today = DateStreakUtils.todayKey()
        val todayIso = DateStreakUtils.isoDayOfWeek(today)

        val todayChores = chores.filter { it.isScheduledForDayOfWeek(todayIso) }
        val overallDone = todayChores.count { "${it.id}_${today}" in completedIds }

        val memberSummaries = members.map { member ->
            val stats = DateStreakUtils.computeMemberStats(member.id, chores, completions, today)
            val cells = week.map { day ->
                val sched = chores.filter {
                    it.isScheduledForDayOfWeek(day.isoDayOfWeek) &&
                        it.effectiveAssigneeForDate(day.dateKey) == member.id
                }
                if (sched.isEmpty()) "none"
                else {
                    val doneCount = sched.count { "${it.id}_${day.dateKey}" in completedIds }
                    when {
                        doneCount >= sched.size -> "done"
                        doneCount > 0 -> "partial"
                        else -> "todo"
                    }
                }
            }
            WallMemberSummary(
                displayName = member.displayName,
                role = member.role,
                colorKey = member.avatarColorKey,
                todayCompleted = stats.todayCompleted,
                todayTotal = stats.todayTotal,
                currentStreak = stats.currentStreak,
                weekCells = cells
            )
        }

        val weekKeys = week.map { it.dateKey }.toSet()
        val upcomingEvents = events
            .filter { it.dateKey in weekKeys }
            .take(10)
            .map { ev ->
                val timeLabel = if (ev.allDay) "All day"
                else DateStreakUtils.formatTime12h(ev.startHour, ev.startMinute)
                WallEventSummary(
                    dateKey = ev.dateKey,
                    title = ev.title,
                    timeLabel = timeLabel,
                    location = ev.location
                )
            }

        val todayMeals = repository.getAllDinnerOptions()
            .filter { !it.isDeleted && it.dateKey == today }
        val tonightOptions = todayMeals.filter { it.slot() == MealSlot.DINNER }.ifEmpty { todayMeals }
        val tonightPick = tonightOptions.firstOrNull { it.isWinner }
            ?: tonightOptions.maxByOrNull { it.voterIds().size }
        val tonightDinnerLabel = when {
            tonightPick == null -> ""
            tonightPick.isWinner -> tonightPick.mealName
            else -> "${tonightPick.mealName} (leading vote)"
        }

        val snapshot = WallSnapshotPayload(
            householdName = config.householdName,
            generatedAt = System.currentTimeMillis(),
            todayDateLabel = DateStreakUtils.formatFullToday(),
            dayHeaders = week.map { "${it.shortLabel} ${it.dayNumber}" },
            overallCompleted = overallDone,
            overallTotal = todayChores.size,
            peerCount = peers.size,
            tonightDinner = tonightDinnerLabel,
            members = memberSummaries,
            upcomingEvents = upcomingEvents
        )
        writeJson(output, 200, "OK", wallAdapter.toJson(snapshot))
    }

    private fun readLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString(StandardCharsets.UTF_8.name())
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, StandardCharsets.UTF_8)
            }
            baos.write(b)
            prev = b
        }
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private fun writeJson(output: OutputStream, status: Int, reason: String, json: String) {
        writeResponse(output, status, reason, "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeResponse(output: OutputStream, status: Int, reason: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    companion object {
        private const val TAG = "MeshNodeServer"
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024 // 2 MB cap

        private val WALL_DISPLAY_HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>HomeList Wall Board</title>
<style>
  :root { --bg:#FAF6F0; --card:#FFFDF9; --border:#E4D9CB; --terra:#C66B4F; --sage:#5F8068; --sageBg:#DDE9DF; --amber:#D48F36; --ink:#2B241E; --muted:#6E6258; }
  * { box-sizing:border-box; }
  body { margin:0; padding:28px; background:var(--bg); color:var(--ink); font-family:system-ui,-apple-system,sans-serif; }
  .top { display:flex; justify-content:space-between; align-items:flex-end; margin-bottom:24px; border-bottom:1px solid var(--border); padding-bottom:16px; }
  h1 { margin:0; font-family:Georgia,serif; font-size:36px; font-weight:600; }
  .date { color:var(--muted); font-size:18px; margin-top:4px; }
  .clock { font-family:Georgia,serif; font-size:48px; color:var(--terra); font-weight:600; }
  .grid { display:grid; grid-template-columns:2fr 1fr; gap:20px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:20px; padding:20px; }
  table { width:100%; border-collapse:collapse; margin-top:12px; }
  th, td { padding:10px 8px; text-align:center; border-bottom:1px solid var(--border); }
  th:first-child, td:first-child { text-align:left; font-weight:600; }
  .dot { display:inline-block; width:22px; height:22px; border-radius:50%; }
  .done { background:var(--sage); }
  .partial { background:var(--amber); }
  .todo { border:2px solid var(--terra); background:transparent; }
  .none { background:#ECE2D4; opacity:0.5; }
  .pill { display:inline-block; padding:4px 10px; border-radius:999px; font-size:13px; background:var(--sageBg); color:var(--sage); font-weight:600; }
  .event { padding:10px 0; border-bottom:1px solid var(--border); }
  .event-title { font-weight:600; }
  .event-meta { color:var(--muted); font-size:13px; margin-top:2px; }
</style>
</head>
<body>
  <div class="top">
    <div>
      <h1 id="title">HomeList</h1>
      <div class="date" id="dateLabel">Loading household board...</div>
    </div>
    <div style="text-align:right">
      <div class="clock" id="clock">--:--</div>
      <div class="pill" id="summaryPill">Mesh Ready</div>
    </div>
  </div>
  <div class="grid">
    <div class="card">
      <h2 style="margin:0;font-family:Georgia,serif">Weekly Family Progress</h2>
      <table>
        <thead><tr id="headRow"><th>Member</th></tr></thead>
        <tbody id="memberRows"></tbody>
      </table>
    </div>
    <div class="card">
      <h2 style="margin:0;font-family:Georgia,serif">Tonight's Dinner</h2>
      <div id="dinnerBox" style="margin:10px 0 18px 0;font-size:18px;font-weight:600;color:var(--terra)">Not decided yet</div>
      <h2 style="margin:0;font-family:Georgia,serif">This Week's Events</h2>
      <div id="eventsList" style="margin-top:12px"></div>
    </div>
  </div>
<script>
function updateClock() {
  const now = new Date();
  document.getElementById('clock').textContent = now.toLocaleTimeString([], {hour:'numeric', minute:'2-digit'});
}
async function refresh() {
  try {
    const res = await fetch('/api/v1/wall-snapshot');
    if (!res.ok) return;
    const data = await res.json();
    document.getElementById('title').textContent = data.householdName || 'HomeList';
    document.getElementById('dateLabel').textContent = data.todayDateLabel || '';
    document.getElementById('summaryPill').textContent = data.overallCompleted + ' of ' + data.overallTotal + ' done today • ' + data.peerCount + ' nodes';
    document.getElementById('dinnerBox').textContent = data.tonightDinner || 'Vote open on HomeList';
    const head = document.getElementById('headRow');
    head.textContent = '';
    const th0 = document.createElement('th'); th0.textContent = 'Member'; head.appendChild(th0);
    (data.dayHeaders || []).forEach(d => { const th = document.createElement('th'); th.textContent = d; head.appendChild(th); });
    const thS = document.createElement('th'); thS.textContent = 'Streak'; head.appendChild(thS);
    const body = document.getElementById('memberRows');
    body.textContent = '';
    (data.members || []).forEach(m => {
      const tr = document.createElement('tr');
      const tdName = document.createElement('td');
      tdName.textContent = m.displayName + ' (' + m.todayCompleted + '/' + m.todayTotal + ')';
      tr.appendChild(tdName);
      (m.weekCells || []).forEach(c => {
        const td = document.createElement('td');
        const span = document.createElement('span');
        span.className = 'dot ' + c;
        td.appendChild(span);
        tr.appendChild(td);
      });
      const tdStreak = document.createElement('td');
      tdStreak.textContent = m.currentStreak + 'd';
      tr.appendChild(tdStreak);
      body.appendChild(tr);
    });
    const evList = document.getElementById('eventsList');
    evList.textContent = '';
    if (!data.upcomingEvents || data.upcomingEvents.length === 0) {
      const p = document.createElement('div'); p.className = 'event-meta'; p.textContent = 'No events scheduled this week.'; evList.appendChild(p);
    } else {
      data.upcomingEvents.forEach(ev => {
        const div = document.createElement('div'); div.className = 'event';
        const t = document.createElement('div'); t.className = 'event-title'; t.textContent = ev.title;
        const meta = document.createElement('div'); meta.className = 'event-meta';
        meta.textContent = ev.dateKey + ' • ' + ev.timeLabel + (ev.location ? ' • ' + ev.location : '');
        div.appendChild(t); div.appendChild(meta); evList.appendChild(div);
      });
    }
  } catch (e) {}
}
updateClock();
refresh();
setInterval(updateClock, 15000);
setInterval(refresh, 15000);
</script>
</body>
</html>
        """.trimIndent()
    }
}
