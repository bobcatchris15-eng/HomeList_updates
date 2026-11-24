package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class MemberRole(val label: String) {
    PARENT("Parent"),
    CHILD("Child");

    companion object {
        fun fromString(value: String?): MemberRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CHILD
    }
}

enum class DeviceType(val label: String) {
    PHONE("Phone"),
    TABLET("Tablet"),
    WINDOWS("Windows PC"),
    PI_DISPLAY("Pi Display");

    companion object {
        fun fromString(value: String?): DeviceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PHONE
    }
}

enum class TimeOfDay(val label: String, val sortOrder: Int) {
    MORNING("Morning", 0),
    AFTERNOON("Afternoon", 1),
    EVENING("Evening", 2),
    ANYTIME("Anytime", 3);

    companion object {
        fun fromString(value: String?): TimeOfDay =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ANYTIME
    }
}

enum class ChoreCategory(val label: String) {
    KITCHEN("Kitchen"),
    BEDROOM("Bedroom"),
    PETS("Pets"),
    OUTDOOR("Outdoor"),
    LAUNDRY("Laundry"),
    TIDY("Tidy"),
    SCHOOL("School"),
    ERRAND("Errand");

    companion object {
        fun fromString(value: String?): ChoreCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TIDY
    }
}

enum class ReminderTriggerType(val label: String) {
    TIME("Time"),
    LOCATION("Location"),
    TIME_AND_LOCATION("Time + Location"),
    NUDGE("Parent Nudge");

    companion object {
        fun fromString(value: String?): ReminderTriggerType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TIME
    }
}

enum class PlaceTransition(val label: String) {
    ARRIVE("Arrive at"),
    LEAVE("Leave");

    companion object {
        fun fromString(value: String?): PlaceTransition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ARRIVE
    }
}

enum class CalendarSource(val label: String) {
    HOMELIST("HomeList"),
    GOOGLE_DEVICE("Google Calendar"),
    ICS_FEED("ICS Feed");

    companion object {
        fun fromString(value: String?): CalendarSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HOMELIST
    }
}

enum class GroceryAisle(val label: String) {
    PRODUCE("Produce"),
    DAIRY("Dairy"),
    PANTRY("Pantry"),
    MEAT_DELI("Meat & Deli"),
    FROZEN("Frozen"),
    SNACKS("Snacks"),
    HOUSEHOLD("Household"),
    OTHER("Other");

    companion object {
        fun fromString(value: String?): GroceryAisle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class RotationCadence(val label: String, val shortLabel: String) {
    NONE("Fixed", "Fixed"),
    DAILY("Rotate each turn", "Daily rotation"),
    WEEKLY("Rotate weekly", "Weekly rotation");

    companion object {
        fun fromString(value: String?): RotationCadence =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class MealSlot(val label: String, val sortOrder: Int) {
    BREAKFAST("Breakfast", 0),
    LUNCH("Lunch", 1),
    DINNER("Dinner", 2),
    ANY("Any Meal", 3);

    companion object {
        fun fromString(value: String?): MealSlot =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DINNER
    }
}

@Entity(tableName = "node_config")
data class NodeConfigEntity(
    @PrimaryKey val singletonId: Int = 1,
    val nodeId: String,
    val householdId: String,
    val householdName: String,
    val pairingCode: String,
    val selfMemberId: String,
    val selfRole: String,
    val deviceType: String = DeviceType.PHONE.name,
    val isFounder: Boolean = false,
    val customPrivateHost: String = "",
    val acceptPrivateCheckIns: Boolean = true,
    val checkInIntervalMinutes: Int = 15,
    val listenPort: Int = 8765,
    val googleCalendarId: Long = -1L,
    val googleCalendarName: String = "",
    val icsFeedUrl: String = "",
    val updateReleaseUrl: String = "https://github.com/bobcatchris15-eng/HomeList/releases/latest",
    val darkTheme: Boolean = false,
    val lastSyncedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "mesh_peers")
data class MeshPeerEntity(
    @PrimaryKey val nodeId: String,
    val memberId: String,
    val displayName: String,
    val role: String,
    val deviceType: String = DeviceType.PHONE.name,
    val isFounder: Boolean = false,
    val lanIp: String = "",
    val tailscaleIp: String = "",
    val customPrivateHost: String = "",
    val port: Int = 8765,
    val appVersionCode: Int = 1,
    val appVersionName: String = "1.0",
    val lastSeenAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val role: String,
    val avatarColorKey: String = "terracotta",
    val avatarIconKey: String = "home",
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "chores")
data class ChoreEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String = "",
    val category: String = ChoreCategory.TIDY.name,
    val assignedToMemberId: String? = null,
    val rotationMemberIdsCsv: String = "",
    val rotationCadence: String = RotationCadence.NONE.name,
    val rotationAnchorDateKey: String = "",
    val daysOfWeekCsv: String = "1,2,3,4,5,6,7",
    val timeOfDay: String = TimeOfDay.ANYTIME.name,
    val reminderHour: Int = -1,
    val reminderMinute: Int = 0,
    val remindersEnabled: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun scheduledDays(): Set<Int> =
        daysOfWeekCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    fun isScheduledForDayOfWeek(isoDay: Int): Boolean {
        val days = scheduledDays()
        return days.isEmpty() || isoDay in days
    }

    fun rotationMemberIds(): List<String> =
        rotationMemberIdsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun cadence(): RotationCadence = RotationCadence.fromString(rotationCadence)

    fun isRotating(): Boolean =
        cadence() != RotationCadence.NONE && rotationMemberIds().size >= 2

    fun effectiveAssigneeForDate(dateKey: String): String? =
        DateStreakUtils.resolveChoreAssignee(this, dateKey)

    fun nextAssigneeAfterDate(dateKey: String): String? =
        DateStreakUtils.nextAssigneeInRotation(this, dateKey)
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "completions")
data class CompletionEntity(
    @PrimaryKey val id: String,
    val choreId: String,
    val dateKey: String,
    val completedByMemberId: String,
    val isCompleted: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun key(choreId: String, dateKey: String): String = "${choreId}_${dateKey}"
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 200,
    val isHomePlace: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String = "",
    val assignedToMemberId: String? = null,
    val createdByMemberId: String = "",
    val createdByName: String = "",
    val linkedChoreId: String? = null,
    val triggerType: String = ReminderTriggerType.TIME.name,
    val triggerDateKey: String = "",
    val triggerHour: Int = -1,
    val triggerMinute: Int = 0,
    val repeatDaysCsv: String = "",
    val placeId: String? = null,
    val placeTransition: String = PlaceTransition.ARRIVE.name,
    val isCompleted: Boolean = false,
    val isUrgent: Boolean = false,
    val lastFiredAt: Long = 0L,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "dinner_options")
data class DinnerOptionEntity(
    @PrimaryKey val id: String,
    val dateKey: String,
    val mealName: String,
    val mealSlot: String = MealSlot.DINNER.name,
    val notes: String = "",
    val proposedByMemberId: String = "",
    val proposedByName: String = "",
    val voterIdsCsv: String = "",
    val isWinner: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun voterIds(): Set<String> =
        voterIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun voteCount(): Int = voterIds().size

    fun slot(): MealSlot = MealSlot.fromString(mealSlot)
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "grocery_items")
data class GroceryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: String = "",
    val storeName: String = "",
    val aisleCategory: String = GroceryAisle.OTHER.name,
    val addedByMemberId: String = "",
    val addedByName: String = "",
    val isChecked: Boolean = false,
    val checkedByMemberId: String = "",
    val checkedByName: String = "",
    val checkedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String = "",
    val dateKey: String,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0,
    val allDay: Boolean = false,
    val location: String = "",
    val assignedMemberIdsCsv: String = "",
    val colorKey: String = "terracotta",
    val source: String = CalendarSource.HOMELIST.name,
    val externalEventId: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun assignedMemberIds(): Set<String> =
        assignedMemberIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
