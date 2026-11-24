package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DaySlot(
    val dateKey: String,
    val isoDayOfWeek: Int, // 1 = Mon .. 7 = Sun
    val shortLabel: String, // Mon, Tue, ...
    val dayNumber: String, // 23
    val monthShort: String, // Nov
    val isToday: Boolean
)

data class MemberStreakStats(
    val memberId: String,
    val todayCompleted: Int,
    val todayTotal: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastSevenDayCompleted: List<Boolean>
) {
    val todayProgress: Float
        get() = if (todayTotal <= 0) 1f else (todayCompleted.toFloat() / todayTotal.toFloat()).coerceIn(0f, 1f)
}

object DateStreakUtils {
    private val keyFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val shortDayFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEE", Locale.US)

    private val monthShortFormat: SimpleDateFormat
        get() = SimpleDateFormat("MMM", Locale.US)

    private val fullDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    private val clockFormat: SimpleDateFormat
        get() = SimpleDateFormat("h:mm", Locale.getDefault())

    private val amPmFormat: SimpleDateFormat
        get() = SimpleDateFormat("a", Locale.getDefault())

    fun todayKey(): String = keyFormat.format(Date())

    fun formatDateKey(cal: Calendar): String = keyFormat.format(cal.time)

    fun formatFullToday(millis: Long = System.currentTimeMillis()): String =
        fullDateFormat.format(Date(millis))

    fun formatClockTime(millis: Long = System.currentTimeMillis()): Pair<String, String> {
        val date = Date(millis)
        return clockFormat.format(date) to amPmFormat.format(date)
    }

    fun formatTime12h(hour: Int, minute: Int): String {
        if (hour !in 0..23) return ""
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, minute.coerceIn(0, 59), amPm)
    }

    fun greetingForHour(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String =
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

    fun isoDayOfWeek(dateKey: String): Int {
        val cal = parseDateKey(dateKey) ?: Calendar.getInstance()
        return calendarToIsoDay(cal.get(Calendar.DAY_OF_WEEK))
    }

    fun calendarToIsoDay(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    fun parseDateKey(dateKey: String): Calendar? {
        return try {
            val parsed = keyFormat.parse(dateKey) ?: return null
            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shiftDateKey(dateKey: String, deltaDays: Int): String {
        val cal = parseDateKey(dateKey) ?: Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        return formatDateKey(cal)
    }

    fun weekSlots(weekOffset: Int = 0): List<DaySlot> {
        val today = todayKey()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val iso = calendarToIsoDay(get(Calendar.DAY_OF_WEEK))
            add(Calendar.DAY_OF_YEAR, -(iso - 1) + (weekOffset * 7))
        }
        return (0..6).map { idx ->
            val slotCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, idx) }
            val key = formatDateKey(slotCal)
            DaySlot(
                dateKey = key,
                isoDayOfWeek = idx + 1,
                shortLabel = shortDayFormat.format(slotCal.time),
                dayNumber = slotCal.get(Calendar.DAY_OF_MONTH).toString(),
                monthShort = monthShortFormat.format(slotCal.time),
                isToday = key == today
            )
        }
    }

    fun daysBetween(startDateKey: String, endDateKey: String): Int {
        val start = parseDateKey(startDateKey) ?: return 0
        val end = parseDateKey(endDateKey) ?: return 0
        // Round using noon-safe division to avoid DST 23h/25h off-by-one drift
        val diffMs = end.timeInMillis - start.timeInMillis
        val dayMs = 86_400_000.0
        return kotlin.math.round(diffMs / dayMs).toInt()
    }

    private fun startOfIsoWeek(cal: Calendar): Calendar {
        return (cal.clone() as Calendar).apply {
            val iso = calendarToIsoDay(get(Calendar.DAY_OF_WEEK))
            add(Calendar.DAY_OF_YEAR, -(iso - 1))
        }
    }

    fun weeksBetween(startDateKey: String, endDateKey: String): Int {
        val start = parseDateKey(startDateKey)?.let { startOfIsoWeek(it) } ?: return 0
        val end = parseDateKey(endDateKey)?.let { startOfIsoWeek(it) } ?: return 0
        val diffMs = end.timeInMillis - start.timeInMillis
        val weekMs = 7.0 * 86_400_000.0
        return kotlin.math.round(diffMs / weekMs).toInt()
    }

    /**
     * Deterministically resolves who owns a chore on a given date across all mesh phones.
     */
    fun resolveChoreAssignee(chore: ChoreEntity, dateKey: String): String? {
        val cadence = chore.cadence()
        val rotation = chore.rotationMemberIds()
        if (cadence == RotationCadence.NONE || rotation.size < 2) {
            return chore.assignedToMemberId
        }
        val anchor = chore.rotationAnchorDateKey.ifBlank { todayKey() }
        val step = when (cadence) {
            RotationCadence.WEEKLY -> weeksBetween(anchor, dateKey)
            RotationCadence.DAILY -> {
                val scheduledDays = chore.scheduledDays()
                val totalDays = daysBetween(anchor, dateKey)
                if (scheduledDays.isEmpty() || scheduledDays.size == 7) {
                    totalDays
                } else {
                    countScheduledTurns(anchor, totalDays, scheduledDays)
                }
            }
            RotationCadence.NONE -> 0
        }
        val size = rotation.size
        val index = ((step % size) + size) % size
        return rotation[index]
    }

    fun nextAssigneeInRotation(chore: ChoreEntity, dateKey: String): String? {
        val rotation = chore.rotationMemberIds()
        if (!chore.isRotating() || rotation.size < 2) return null
        val current = resolveChoreAssignee(chore, dateKey) ?: return rotation.firstOrNull()
        val idx = rotation.indexOf(current)
        if (idx < 0) return rotation.firstOrNull()
        return rotation[(idx + 1) % rotation.size]
    }

    private fun countScheduledTurns(anchorDateKey: String, deltaDays: Int, scheduledDays: Set<Int>): Int {
        if (deltaDays == 0) return 0
        val fullWeeks = deltaDays / 7
        val remainder = deltaDays % 7
        var turns = fullWeeks * scheduledDays.size
        if (remainder > 0) {
            for (d in 1..remainder) {
                val dayIso = isoDayOfWeek(shiftDateKey(anchorDateKey, d))
                if (dayIso in scheduledDays) turns++
            }
        } else if (remainder < 0) {
            for (d in -1 downTo remainder) {
                val dayIso = isoDayOfWeek(shiftDateKey(anchorDateKey, d))
                if (dayIso in scheduledDays) turns--
            }
        }
        return turns
    }

    fun computeMemberStats(
        memberId: String,
        chores: List<ChoreEntity>,
        completions: List<CompletionEntity>,
        referenceDateKey: String = todayKey()
    ): MemberStreakStats {
        val activeChores = chores.filter { !it.isDeleted }
        val completedLookup = completions
            .filter { it.isCompleted }
            .associateBy { it.id }

        fun dayStatus(dateKey: String): Pair<Int, Int> {
            val isoDay = isoDayOfWeek(dateKey)
            val scheduled = activeChores.filter {
                it.isScheduledForDayOfWeek(isoDay) && it.effectiveAssigneeForDate(dateKey) == memberId
            }
            if (scheduled.isEmpty()) return 0 to 0
            val done = scheduled.count { chore ->
                completedLookup.containsKey(CompletionEntity.key(chore.id, dateKey))
            }
            return done to scheduled.size
        }

        val (todayDone, todayTotal) = dayStatus(referenceDateKey)

        // Compute current streak walking backwards up to 90 days.
        // Today counts if all scheduled chores are done; if today is still in progress,
        // streak stays intact from yesterday so kids don't feel penalized before bedtime.
        var currentStreak = 0
        val todayComplete = todayTotal > 0 && todayDone >= todayTotal
        var cursorOffset = if (todayComplete) 0 else -1
        for (step in 0 until 90) {
            val key = shiftDateKey(referenceDateKey, cursorOffset - step)
            val (done, total) = dayStatus(key)
            if (total == 0) {
                // Rest day with no assigned chores does not break streak
                continue
            }
            if (done >= total) {
                currentStreak++
            } else {
                break
            }
        }

        // Compute longest streak over the past 60 days
        var longestStreak = currentStreak
        var running = 0
        for (offset in -60..0) {
            val key = shiftDateKey(referenceDateKey, offset)
            val (done, total) = dayStatus(key)
            if (total == 0) continue
            if (done >= total) {
                running++
                if (running > longestStreak) longestStreak = running
            } else {
                running = 0
            }
        }

        val lastSeven = (-6..0).map { offset ->
            val key = shiftDateKey(referenceDateKey, offset)
            val (done, total) = dayStatus(key)
            total > 0 && done >= total
        }

        return MemberStreakStats(
            memberId = memberId,
            todayCompleted = todayDone,
            todayTotal = todayTotal,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastSevenDayCompleted = lastSeven
        )
    }

    fun formatDaysSummary(daysCsv: String): String {
        val days = daysCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        return when {
            days.size == 7 || days.isEmpty() -> "Daily"
            days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
            days == setOf(6, 7) -> "Weekends"
            else -> {
                val labels = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                days.sorted().mapNotNull { labels[it] }.joinToString(", ")
            }
        }
    }

    fun relativeTimeLabel(epochMs: Long): String {
        if (epochMs <= 0L) return "Never"
        val diffSec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 45 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> "${diffSec / 86400}d ago"
        }
    }
}
