package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mesh.GossipRequestEnvelope
import com.example.mesh.GossipResponseEnvelope
import com.example.mesh.HouseholdInvite
import com.example.mesh.MeshGossipPayload
import com.example.mesh.MeshRuntime
import com.example.mesh.MeshTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HouseholdRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migratesPopulatedVersionSevenDatabase() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        try {
            // Unchanged columns are produced by Room itself. Reverse only v8 schema additions.
            Room.databaseBuilder(context, HomeListDatabase::class.java, name).build().useDb { initial ->
                val dao = initial.dao()
                dao.upsertMember(MemberEntity("member", "Alex", "PARENT"))
                dao.upsertChore(ChoreEntity("chore", "Dishes", isRecurring = false, dueDateKey = "2026-11-27"))
                dao.upsertCompletion(CompletionEntity("chore_2026-11-27", "chore", "2026-11-27", "member"))
                dao.upsertPlace(SavedPlaceEntity("place", "Home", 1.0, 2.0, isHomePlace = true))
                dao.upsertReminder(ReminderEntity("reminder", "Keys", placeId = "place"))
                dao.upsertEvent(CalendarEventEntity("event", "Game", dateKey = "2026-11-28", assignedMemberIdsCsv = "member"))
                dao.upsertDinnerOption(DinnerOptionEntity("dinner", "2026-11-27", "Pasta"))
                dao.upsertGroceryItem(GroceryItemEntity("grocery", "Milk", isDeleted = true))

                val sql = initial.openHelper.writableDatabase
                sql.execSQL("DROP TABLE households")
                sql.execSQL("DROP TABLE node_config")
                sql.execSQL("""
                    CREATE TABLE node_config (
                        singletonId INTEGER NOT NULL PRIMARY KEY, nodeId TEXT NOT NULL,
                        householdId TEXT NOT NULL, householdName TEXT NOT NULL, pairingCode TEXT NOT NULL,
                        selfMemberId TEXT NOT NULL, selfRole TEXT NOT NULL, deviceType TEXT NOT NULL,
                        isFounder INTEGER NOT NULL, listenPort INTEGER NOT NULL, googleCalendarId INTEGER NOT NULL,
                        googleCalendarName TEXT NOT NULL, icsFeedUrl TEXT NOT NULL, updateReleaseUrl TEXT NOT NULL,
                        darkTheme INTEGER NOT NULL, lastSyncedAt INTEGER NOT NULL, hubUrl TEXT NOT NULL
                    )
                """.trimIndent())
                sql.execSQL("""
                    INSERT INTO node_config VALUES (
                       1, 'node', 'home-v7', 'Original home', 'ABC234', 'member', 'PARENT', 'TABLET',
                       1, 8765, 123, 'Family Calendar', 'https://example.com/family.ics',
                       'https://example.com/releases', 1, 2000, 'http://192.168.1.2:8765'
                    )
                """.trimIndent())
                sql.execSQL("DROP TABLE mesh_peers")
                sql.execSQL("""
                    CREATE TABLE mesh_peers (
                        nodeId TEXT NOT NULL PRIMARY KEY, memberId TEXT NOT NULL, displayName TEXT NOT NULL,
                        role TEXT NOT NULL, deviceType TEXT NOT NULL, isFounder INTEGER NOT NULL,
                        lanIp TEXT NOT NULL, port INTEGER NOT NULL, appVersionCode INTEGER NOT NULL,
                        appVersionName TEXT NOT NULL, lastSeenAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                sql.execSQL("INSERT INTO mesh_peers VALUES ('node', 'member', 'Alex', 'PARENT', 'TABLET', 1, '', 8765, 1, '1.0', 2000, 2000)")
                for (table in listOf(
                    "members", "chores", "completions", "saved_places", "reminders",
                    "calendar_events", "dinner_options", "grocery_items"
                )) {
                    sql.execSQL("DROP INDEX index_${table}_householdId")
                    sql.execSQL("ALTER TABLE $table DROP COLUMN householdId")
                }
                sql.version = 7
            }

            Room.databaseBuilder(context, HomeListDatabase::class.java, name)
                .addMigrations(HomeListDatabase.MIGRATION_7_8).build().useDb { migrated ->
                    val repository = HomeListRepository(migrated)
                    val config = checkNotNull(repository.getConfig())
                    assertEquals("home-v7", config.householdId)
                    assertEquals("node", config.nodeId)
                    assertEquals("http://192.168.1.2:8765", config.hubUrl)
                    assertEquals(123L, config.googleCalendarId)
                    assertEquals(2000L, config.lastSyncedAt)
                    assertTrue(config.darkTheme)
                    val id = config.householdId
                    assertEquals(listOf(id, id, id, id, id, id, id, id, id), listOf(
                        repository.getAllPeers(id).single().householdId,
                        repository.getAllMembers(id).single().householdId,
                        repository.getAllChores(id).single().householdId,
                        repository.getAllCompletions(id).single().householdId,
                        repository.getAllPlaces(id).single().householdId,
                        repository.getAllReminders(id).single().householdId,
                        repository.getAllEvents(id).single().householdId,
                        repository.getAllDinnerOptions(id).single().householdId,
                        repository.getAllGroceryItems(id).single().householdId
                    ))
                    assertEquals("2026-11-27", repository.getAllChores(id).single().dueDateKey)
                    assertEquals("member", repository.getAllEvents(id).single().assignedMemberIdsCsv)
                    assertTrue(repository.getAllGroceryItems(id).single().isDeleted)
                }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun activeReadsAndAuthenticatedMergesStayInsideHousehold() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, HomeListDatabase::class.java).build().useDb { database ->
            val repo = HomeListRepository(database)
            val a = repo.foundHousehold("House A", "Alex", "terracotta", DeviceType.PHONE)
            repo.saveChore(ChoreEntity("a-chore", "A dishes"))
            repo.addGroceryItem("A milk", memberId = a.selfMemberId, memberName = "Alex")
            val b = repo.foundHousehold("House B", "Alex", "terracotta", DeviceType.PHONE)
            assertEquals(a.nodeId, b.nodeId)
            assertNotEquals(a.selfMemberId, b.selfMemberId)
            assertEquals(a.nodeId, repo.getAllPeers(a.householdId).single().nodeId)
            assertEquals(b.nodeId, repo.getAllPeers(b.householdId).single().nodeId)
            assertTrue(repo.choresFlow.first().isEmpty())
            assertTrue(repo.groceryItemsFlow.first().isEmpty())

            val malicious = MeshGossipPayload(
                householdId = b.householdId, householdName = "House B", senderNodeId = "peer", sentAt = 1,
                chores = listOf(
                    ChoreEntity("a-chore", "Collision", updatedAt = Long.MAX_VALUE),
                    ChoreEntity("foreign", "Other house", householdId = a.householdId),
                    ChoreEntity("b-chore", "B dishes", updatedAt = 10)
                ),
                reminders = listOf(ReminderEntity("reminder", "B reminder", assignedToMemberId = b.selfMemberId)),
                groceryItems = listOf(GroceryItemEntity(
                    "grocery", "B oranges", addedByMemberId = b.selfMemberId, checkedByMemberId = "peer",
                    isChecked = true, updatedAt = 1
                ))
            )
            val wrong = repo.handleIncomingGossip(GossipRequestEnvelope(
                householdId = b.householdId, pairingCode = "WRONG", payload = malicious
            ))
            assertFalse(wrong.response.ok)
            assertEquals("invalid_pairing_code", wrong.response.error)
            assertTrue(repo.getAllChores(b.householdId).isEmpty())
            val unknown = repo.handleIncomingGossip(GossipRequestEnvelope(householdId = "absent", pairingCode = b.pairingCode))
            assertEquals("unknown_household", unknown.response.error)
            assertNull(unknown.response.payload)

            val request = GossipRequestEnvelope(householdId = b.householdId, pairingCode = b.pairingCode, payload = malicious)
            val result = repo.handleIncomingGossip(request)
            assertTrue(result.response.ok)
            assertEquals("House B", result.changes?.householdName)
            assertEquals("B reminder", result.changes?.newReminders?.single()?.title)
            assertEquals("B oranges", result.changes?.fulfilledShoppingItems?.single()?.name)
            assertEquals(listOf("B dishes"), repo.getAllChores(b.householdId).map { it.title })
            assertEquals(listOf("A dishes"), repo.getAllChores(a.householdId).map { it.title })
            assertEquals("A milk", repo.getAllGroceryItems(a.householdId).single().name)
            assertTrue(repo.handleIncomingGossip(request).changes?.newReminders.orEmpty().isEmpty())

            val tombstone = malicious.copy(chores = listOf(ChoreEntity("b-chore", "B dishes", updatedAt = 11, isDeleted = true)))
            repo.mergeGossipPayload(b.householdId, tombstone)
            repo.mergeGossipPayload(b.householdId, malicious)
            assertTrue(repo.getAllChores(b.householdId).single().isDeleted)
            assertTrue(repo.choresFlow.first().isEmpty())
            repo.setActiveHousehold(a.householdId)
            assertEquals("A dishes", repo.choresFlow.first().single().title)
        }
        Unit
    }

    @Test
    fun syncPassVisitsBothHouseholdsEvenWhenOneIsActive() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, HomeListDatabase::class.java).build().useDb { database ->
            val repository = HomeListRepository(database)
            val a = repository.foundHousehold("House A", "Alex", "terracotta", DeviceType.PHONE)
            val b = repository.foundHousehold("House B", "Alex", "terracotta", DeviceType.PHONE)
            repository.updateConfig(a.householdId) { it.copy(hubEndpointId = "hub-a") }
            repository.updateConfig(b.householdId) { it.copy(hubEndpointId = "hub-b") }
            val requests = mutableListOf<Pair<String, GossipRequestEnvelope>>()
            var open = false
            val transport = object : MeshTransport {
                override suspend fun start() { open = true }
                override suspend fun stop() { open = false }
                override suspend fun localEndpointId() = "test-device"
                override suspend fun exchangeGossip(
                    peerEndpointId: String, request: GossipRequestEnvelope
                ): GossipResponseEnvelope {
                    check(open)
                    requests.add(peerEndpointId to request)
                    return GossipResponseEnvelope(true, payload = MeshGossipPayload(
                        householdId = request.householdId,
                        householdName = checkNotNull(request.payload).householdName,
                        senderNodeId = peerEndpointId,
                        sentAt = 0
                    ))
                }
            }
            val result = MeshRuntime(context, repository, transport).sync()
            assertEquals(2, result.successCount)
            assertFalse(open)
            val byHouse = requests.associateBy { it.second.householdId }
            for ((house, hub) in listOf(a to "hub-a", b to "hub-b")) {
                val (endpoint, request) = checkNotNull(byHouse[house.householdId])
                assertEquals(hub, endpoint)
                assertEquals(house.pairingCode, request.pairingCode)
                assertEquals(house.selfMemberId, request.payload?.members?.single()?.id)
            }
        }
        Unit
    }

    @Test
    fun inviteRoundTripsUrlEncodedHouseholdNames() {
        val invite = HouseholdInvite("abc123".repeat(10), "house-a", "ABC234", "Alex + Mia & family")
        assertEquals(invite, HouseholdInvite.parse(invite.toUri().toString()))
        assertNull(HouseholdInvite.parse("homelist://192.168.0.2:8765?code=ABC234"))
    }

    @Test
    fun leavingOneHouseholdKeepsTheOtherAndDeviceIdentity() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, HomeListDatabase::class.java).build().useDb { database ->
            val repo = HomeListRepository(database)
            val a = repo.foundHousehold("House A", "Alex", "terracotta", DeviceType.PHONE)
            repo.saveChore(ChoreEntity("a", "A only"))
            val b = repo.foundHousehold("House B", "Alex", "terracotta", DeviceType.PHONE)
            repo.saveChore(ChoreEntity("b", "B only"))
            repo.setActiveHousehold(a.householdId)
            repo.leaveHousehold(a.householdId)
            assertEquals(b.householdId, repo.getConfig()?.householdId)
            assertEquals("B only", repo.choresFlow.first().single().title)
            assertTrue(repo.getAllChores(a.householdId).isEmpty())
            assertEquals(b.selfMemberId, repo.getAllPeers(b.householdId).single().memberId)
            repo.leaveHousehold(b.householdId)
            assertNull(repo.getConfig())
            assertNotNull(repo.getDeviceConfig())
            assertEquals(a.nodeId, repo.getDeviceConfig()?.nodeId)
        }
        Unit
    }
}

private inline fun <T> HomeListDatabase.useDb(block: (HomeListDatabase) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
