package com.example.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.CalendarEventEntity
import com.example.data.CalendarSource
import com.example.data.DateStreakUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean
)

class GoogleCalendarSyncBridge(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun listDeviceCalendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val result = mutableListOf<DeviceCalendarInfo>()
        try {
            appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "Calendar"
                    val account = cursor.getString(2) ?: ""
                    val isPrimary = cursor.getInt(3) == 1
                    result.add(DeviceCalendarInfo(id, name, account, isPrimary))
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    suspend fun importFromDeviceCalendar(
        calendarId: Long,
        daysBack: Int = 7,
        daysAhead: Int = 35
    ): Result<List<CalendarEventEntity>> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            return@withContext Result.failure(SecurityException("Calendar read permission not granted"))
        }
        val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }
        val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysAhead) }

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(uriBuilder, startCal.timeInMillis)
        android.content.ContentUris.appendId(uriBuilder, endCal.timeInMillis)

        val selection = if (calendarId > 0L) "${CalendarContract.Instances.CALENDAR_ID} = ?" else null
        val selectionArgs = if (calendarId > 0L) arrayOf(calendarId.toString()) else null

        val imported = mutableListOf<CalendarEventEntity>()
        try {
            appContext.contentResolver.query(
                uriBuilder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val now = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    val extId = cursor.getLong(0).toString()
                    val title = cursor.getString(1)?.trim().orEmpty()
                    if (title.isBlank()) continue
                    val notes = cursor.getString(2)?.trim().orEmpty().take(240)
                    val beginMs = cursor.getLong(3)
                    val endMs = cursor.getLong(4)
                    val allDay = cursor.getInt(5) == 1
                    val location = cursor.getString(6)?.trim().orEmpty()

                    val sCal = Calendar.getInstance().apply { timeInMillis = beginMs }
                    val eCal = Calendar.getInstance().apply { timeInMillis = endMs }
                    val dateKey = DateStreakUtils.formatDateKey(sCal)

                    imported.add(
                        CalendarEventEntity(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            notes = notes,
                            dateKey = dateKey,
                            startHour = sCal.get(Calendar.HOUR_OF_DAY),
                            startMinute = sCal.get(Calendar.MINUTE),
                            endHour = eCal.get(Calendar.HOUR_OF_DAY),
                            endMinute = eCal.get(Calendar.MINUTE),
                            allDay = allDay,
                            location = location,
                            colorKey = "slate_blue",
                            source = CalendarSource.GOOGLE_DEVICE.name,
                            externalEventId = "${extId}_${dateKey}",
                            updatedAt = now
                        )
                    )
                }
            }
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportEventToDeviceCalendar(
        calendarId: Long,
        event: CalendarEventEntity
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            return@withContext Result.failure(SecurityException("Calendar write permission not granted"))
        }
        val baseCal = DateStreakUtils.parseDateKey(event.dateKey) ?: Calendar.getInstance()
        val startCal = (baseCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, if (event.allDay) 0 else event.startHour.coerceIn(0, 23))
            set(Calendar.MINUTE, if (event.allDay) 0 else event.startMinute.coerceIn(0, 59))
        }
        val endCal = (baseCal.clone() as Calendar).apply {
            if (event.allDay) {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, event.endHour.coerceIn(0, 23))
                set(Calendar.MINUTE, event.endMinute.coerceIn(0, 59))
                if (timeInMillis <= startCal.timeInMillis) {
                    timeInMillis = startCal.timeInMillis + 60 * 60_000L
                }
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.notes)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
            put(CalendarContract.Events.DTEND, endCal.timeInMillis)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        try {
            val uri = appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val createdId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            Result.success(createdId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Zero-permission fallback to open an event directly in Google Calendar / system calendar app.
     */
    fun buildInsertCalendarIntent(event: CalendarEventEntity): Intent {
        val baseCal = DateStreakUtils.parseDateKey(event.dateKey) ?: Calendar.getInstance()
        val startCal = (baseCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, if (event.allDay) 0 else event.startHour)
            set(Calendar.MINUTE, if (event.allDay) 0 else event.startMinute)
        }
        val endCal = (baseCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, if (event.allDay) 23 else event.endHour)
            set(Calendar.MINUTE, if (event.allDay) 59 else event.endMinute)
        }
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endCal.timeInMillis)
        }
    }

    /**
     * Zero-permission iCalendar (.ics) HTTPS feed importer for Google Calendar secret iCal links.
     */
    suspend fun importFromIcsUrl(url: String): Result<List<CalendarEventEntity>> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid https:// .ics calendar URL"))
        }
        try {
            val req = Request.Builder().url(trimmed).get().build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Feed returned HTTP ${res.code}"))
                }
                val body = res.body?.string().orEmpty()
                Result.success(parseIcsEvents(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIcsEvents(icsText: String): List<CalendarEventEntity> {
        val unfolded = icsText.replace("\r\n ", "").replace("\n ", "")
        val lines = unfolded.lines()
        val events = mutableListOf<CalendarEventEntity>()
        val now = System.currentTimeMillis()

        var inEvent = false
        var uid = ""
        var summary = ""
        var description = ""
        var location = ""
        var dtStart = ""
        var dtEnd = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    uid = ""
                    summary = ""
                    description = ""
                    location = ""
                    dtStart = ""
                    dtEnd = ""
                }
                line == "END:VEVENT" && inEvent -> {
                    inEvent = false
                    val parsedStart = parseIcsDateTime(dtStart)
                    if (summary.isNotBlank() && parsedStart != null) {
                        val parsedEnd = parseIcsDateTime(dtEnd)
                        events.add(
                            CalendarEventEntity(
                                id = UUID.randomUUID().toString(),
                                title = unescapeIcs(summary),
                                notes = unescapeIcs(description).take(240),
                                dateKey = parsedStart.dateKey,
                                startHour = parsedStart.hour,
                                startMinute = parsedStart.minute,
                                endHour = parsedEnd?.hour ?: ((parsedStart.hour + 1) % 24),
                                endMinute = parsedEnd?.minute ?: parsedStart.minute,
                                allDay = parsedStart.allDay,
                                location = unescapeIcs(location),
                                colorKey = "olive",
                                source = CalendarSource.ICS_FEED.name,
                                externalEventId = uid.ifBlank { "${summary}_${parsedStart.dateKey}" },
                                updatedAt = now
                            )
                        )
                    }
                }
                inEvent -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).substringBefore(';').uppercase()
                        val value = line.substring(colonIdx + 1).trim()
                        when (key) {
                            "UID" -> uid = value
                            "SUMMARY" -> summary = value
                            "DESCRIPTION" -> description = value
                            "LOCATION" -> location = value
                            "DTSTART" -> dtStart = value
                            "DTEND" -> dtEnd = value
                        }
                    }
                }
            }
        }
        return events.take(120)
    }

    private data class ParsedIcsDate(
        val dateKey: String,
        val hour: Int,
        val minute: Int,
        val allDay: Boolean
    )

    private fun parseIcsDateTime(raw: String): ParsedIcsDate? {
        val clean = raw.trim()
        if (clean.length < 8) return null
        val y = clean.substring(0, 4).toIntOrNull() ?: return null
        val m = clean.substring(4, 6).toIntOrNull() ?: return null
        val d = clean.substring(6, 8).toIntOrNull() ?: return null
        val dateKey = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m, d)
        val tIdx = clean.indexOf('T')
        if (tIdx < 0 || clean.length < tIdx + 5) {
            return ParsedIcsDate(dateKey, 0, 0, allDay = true)
        }
        val hour = clean.substring(tIdx + 1, tIdx + 3).toIntOrNull() ?: 9
        val minute = clean.substring(tIdx + 3, tIdx + 5).toIntOrNull() ?: 0
        return ParsedIcsDate(dateKey, hour.coerceIn(0, 23), minute.coerceIn(0, 59), allDay = false)
    }

    private fun unescapeIcs(text: String): String =
        text.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";")
}
