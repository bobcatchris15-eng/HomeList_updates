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

    @Query("DELETE FROM node_config")
    suspend fun clearConfig()

    // Mesh peers
    @Query("SELECT * FROM mesh_peers ORDER BY isFounder DESC, displayName ASC")
    fun observePeers(): Flow<List<MeshPeerEntity>>

    @Query("SELECT * FROM mesh_peers")
    suspend fun getAllPeers(): List<MeshPeerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: MeshPeerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeers(peers: List<MeshPeerEntity>)

    // Members
    @Query("SELECT * FROM members WHERE isDeleted = 0 ORDER BY role DESC, displayName ASC")
    fun observeActiveMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members")
    suspend fun getAllMembers(): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>)

    // Chores
    @Query("SELECT * FROM chores WHERE isDeleted = 0 ORDER BY title ASC")
    fun observeActiveChores(): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores")
    suspend fun getAllChores(): List<ChoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChore(chore: ChoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChores(chores: List<ChoreEntity>)

    // Completions
    @Query("SELECT * FROM completions")
    fun observeAllCompletions(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions")
    suspend fun getAllCompletions(): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE id = :id LIMIT 1")
    suspend fun getCompletionById(id: String): CompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<CompletionEntity>)

    // Saved places
    @Query("SELECT * FROM saved_places WHERE isDeleted = 0 ORDER BY isHomePlace DESC, name ASC")
    fun observeActivePlaces(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAllPlaces(): List<SavedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: SavedPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaces(places: List<SavedPlaceEntity>)

    // Reminders & Nudges
    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY isCompleted ASC, updatedAt DESC")
    fun observeActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(reminders: List<ReminderEntity>)

    // Calendar Events
    @Query("SELECT * FROM calendar_events WHERE isDeleted = 0 ORDER BY dateKey ASC, allDay DESC, startHour ASC, startMinute ASC")
    fun observeActiveEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAllEvents(): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<CalendarEventEntity>)

    // Dinner requests & daily voting
    @Query("SELECT * FROM dinner_options WHERE isDeleted = 0 ORDER BY isWinner DESC, updatedAt ASC")
    fun observeActiveDinnerOptions(): Flow<List<DinnerOptionEntity>>

    @Query("SELECT * FROM dinner_options")
    suspend fun getAllDinnerOptions(): List<DinnerOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDinnerOption(option: DinnerOptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDinnerOptions(options: List<DinnerOptionEntity>)

    // Synced grocery / store list
    @Query("SELECT * FROM grocery_items WHERE isDeleted = 0 ORDER BY isChecked ASC, storeName ASC, name ASC")
    fun observeActiveGroceryItems(): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_items")
    suspend fun getAllGroceryItems(): List<GroceryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroceryItem(item: GroceryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroceryItems(items: List<GroceryItemEntity>)

    // Wipe household tables on leave/reset
    @Query("DELETE FROM mesh_peers")
    suspend fun clearPeers()

    @Query("DELETE FROM members")
    suspend fun clearMembers()

    @Query("DELETE FROM chores")
    suspend fun clearChores()

    @Query("DELETE FROM completions")
    suspend fun clearCompletions()

    @Query("DELETE FROM saved_places")
    suspend fun clearPlaces()

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Query("DELETE FROM calendar_events")
    suspend fun clearEvents()

    @Query("DELETE FROM dinner_options")
    suspend fun clearDinnerOptions()

    @Query("DELETE FROM grocery_items")
    suspend fun clearGroceryItems()
}
