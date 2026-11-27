package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeListDao {
    // Node config
    @Query("SELECT * FROM node_config WHERE singletonId = 1 LIMIT 1")
    fun observeConfig(): Flow<NodeConfigEntity?>

    @Query("SELECT * FROM node_config WHERE singletonId = 1 LIMIT 1")
    suspend fun getConfig(): NodeConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: NodeConfigEntity)

    @Query("SELECT * FROM households ORDER BY joinedAt, householdId")
    fun observeHouseholds(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM households ORDER BY joinedAt, householdId")
    suspend fun getAllHouseholds(): List<HouseholdEntity>

    @Query("SELECT * FROM households WHERE householdId = :householdId")
    suspend fun getHousehold(householdId: String): HouseholdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHousehold(household: HouseholdEntity)

    @Query("DELETE FROM households WHERE householdId = :householdId")
    suspend fun deleteHousehold(householdId: String)

    @Query("""
        SELECT mesh_peers.householdId, COUNT(*) AS peerCount
        FROM mesh_peers JOIN households ON households.householdId = mesh_peers.householdId
        WHERE nodeId <> (SELECT nodeId FROM node_config WHERE singletonId = 1)
        GROUP BY mesh_peers.householdId
    """)
    fun observeHouseholdPeerCounts(): Flow<List<HouseholdPeerCount>>

    // Mesh peers
    @Query("SELECT * FROM mesh_peers WHERE householdId = :householdId ORDER BY isFounder DESC, displayName ASC")
    fun observePeers(householdId: String): Flow<List<MeshPeerEntity>>

    @Query("SELECT * FROM mesh_peers WHERE householdId = :householdId")
    suspend fun getAllPeers(householdId: String): List<MeshPeerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: MeshPeerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeers(peers: List<MeshPeerEntity>)

    // Members
    @Query("SELECT * FROM members WHERE householdId = :householdId AND isDeleted = 0 ORDER BY role DESC, displayName ASC")
    fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE householdId = :householdId")
    suspend fun getAllMembers(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE householdId = :householdId AND id = :id")
    suspend fun getMemberById(householdId: String, id: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(row: MemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>)

    // Chores
    @Query("SELECT * FROM chores WHERE householdId = :householdId AND isDeleted = 0 ORDER BY title ASC")
    fun observeActiveChores(householdId: String): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores WHERE householdId = :householdId")
    suspend fun getAllChores(householdId: String): List<ChoreEntity>

    @Query("SELECT * FROM chores WHERE householdId = :householdId AND id = :id")
    suspend fun getChoreById(householdId: String, id: String): ChoreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChore(row: ChoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChore(chore: ChoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChores(chores: List<ChoreEntity>)

    // Completions
    @Query("SELECT * FROM completions WHERE householdId = :householdId")
    fun observeAllCompletions(householdId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE householdId = :householdId")
    suspend fun getAllCompletions(householdId: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE householdId = :householdId AND id = :id LIMIT 1")
    suspend fun getCompletionById(householdId: String, id: String): CompletionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(row: CompletionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<CompletionEntity>)

    // Saved places
    @Query("SELECT * FROM saved_places WHERE householdId = :householdId AND isDeleted = 0 ORDER BY isHomePlace DESC, name ASC")
    fun observeActivePlaces(householdId: String): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places WHERE householdId = :householdId")
    suspend fun getAllPlaces(householdId: String): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE householdId = :householdId AND id = :id")
    suspend fun getPlaceById(householdId: String, id: String): SavedPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlace(row: SavedPlaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: SavedPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaces(places: List<SavedPlaceEntity>)

    // Reminders & Nudges
    @Query("SELECT * FROM reminders WHERE householdId = :householdId AND isDeleted = 0 ORDER BY isCompleted ASC, updatedAt DESC")
    fun observeActiveReminders(householdId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE householdId = :householdId")
    suspend fun getAllReminders(householdId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE householdId = :householdId AND id = :id")
    suspend fun getReminderById(householdId: String, id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminder(row: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(reminders: List<ReminderEntity>)

    // Calendar Events
    @Query("SELECT * FROM calendar_events WHERE householdId = :householdId AND isDeleted = 0 ORDER BY dateKey ASC, allDay DESC, startHour ASC, startMinute ASC")
    fun observeActiveEvents(householdId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE householdId = :householdId")
    suspend fun getAllEvents(householdId: String): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE householdId = :householdId AND id = :id")
    suspend fun getEventById(householdId: String, id: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(row: CalendarEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<CalendarEventEntity>)

    // Dinner requests & daily voting
    @Query("SELECT * FROM dinner_options WHERE householdId = :householdId AND isDeleted = 0 ORDER BY isWinner DESC, updatedAt ASC")
    fun observeActiveDinnerOptions(householdId: String): Flow<List<DinnerOptionEntity>>

    @Query("SELECT * FROM dinner_options WHERE householdId = :householdId")
    suspend fun getAllDinnerOptions(householdId: String): List<DinnerOptionEntity>

    @Query("SELECT * FROM dinner_options WHERE householdId = :householdId AND id = :id")
    suspend fun getDinnerOptionById(householdId: String, id: String): DinnerOptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDinnerOption(row: DinnerOptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDinnerOption(option: DinnerOptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDinnerOptions(options: List<DinnerOptionEntity>)

    // Synced grocery / store list
    @Query("SELECT * FROM grocery_items WHERE householdId = :householdId AND isDeleted = 0 ORDER BY isChecked ASC, storeName ASC, name ASC")
    fun observeActiveGroceryItems(householdId: String): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_items WHERE householdId = :householdId")
    suspend fun getAllGroceryItems(householdId: String): List<GroceryItemEntity>

    @Query("SELECT * FROM grocery_items WHERE householdId = :householdId AND id = :id")
    suspend fun getGroceryItemById(householdId: String, id: String): GroceryItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroceryItem(row: GroceryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroceryItem(item: GroceryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroceryItems(items: List<GroceryItemEntity>)

    // Remove only the selected household on leave.
    @Query("DELETE FROM mesh_peers WHERE householdId = :householdId")
    suspend fun deletePeersForHousehold(householdId: String)

    @Query("DELETE FROM members WHERE householdId = :householdId")
    suspend fun deleteMembersForHousehold(householdId: String)

    @Query("DELETE FROM chores WHERE householdId = :householdId")
    suspend fun deleteChoresForHousehold(householdId: String)

    @Query("DELETE FROM completions WHERE householdId = :householdId")
    suspend fun deleteCompletionsForHousehold(householdId: String)

    @Query("DELETE FROM saved_places WHERE householdId = :householdId")
    suspend fun deletePlacesForHousehold(householdId: String)

    @Query("DELETE FROM reminders WHERE householdId = :householdId")
    suspend fun deleteRemindersForHousehold(householdId: String)

    @Query("DELETE FROM calendar_events WHERE householdId = :householdId")
    suspend fun deleteEventsForHousehold(householdId: String)

    @Query("DELETE FROM dinner_options WHERE householdId = :householdId")
    suspend fun deleteDinnerOptionsForHousehold(householdId: String)

    @Query("DELETE FROM grocery_items WHERE householdId = :householdId")
    suspend fun deleteGroceryItemsForHousehold(householdId: String)
}

data class HouseholdPeerCount(val householdId: String, val peerCount: Int)
