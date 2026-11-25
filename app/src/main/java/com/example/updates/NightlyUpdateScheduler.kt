package com.example.updates

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import com.example.data.HomeListDatabase
import com.example.mesh.MeshBackgroundSyncJobService
import com.example.reminders.ReminderNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Schedules a battery-friendly nightly check (~2:00 AM local time) against GitHub Releases
 * so family phones automatically notify users when a new HomeList APK is published.
 * Uses inexact repeating alarms (no special SCHEDULE_EXACT_ALARM permission needed).
 */
object NightlyUpdateScheduler {
    private const val PREFS_NAME = "homelist_update_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val REQUEST_CODE = 876501

    fun scheduleNightlyCheck(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appContext, NightlyUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextRun.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun recordCheckedNow(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
            .apply()
    }

    fun lastCheckedTimeMs(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK_MS, 0L)
    }

    fun shouldNotifyForVersion(context: Context, versionName: String): Boolean {
        val last = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_NOTIFIED_VERSION, "")
            .orEmpty()
        return !last.equals(versionName.trim(), ignoreCase = true)
    }

    fun markNotifiedVersion(context: Context, versionName: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFIED_VERSION, versionName.trim())
            .apply()
    }
}

class NightlyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = HomeListDatabase.getInstance(appContext).dao()
                val cfg = dao.getConfig()
                if (cfg != null) {
                    MeshBackgroundSyncJobService.schedulePeriodicSync(appContext)
                }
                val configuredUrl = cfg?.updateReleaseUrl
                    ?.ifBlank { AppUpdateChecker.DEFAULT_RELEASES_URL }
                    ?: AppUpdateChecker.DEFAULT_RELEASES_URL

                val result = AppUpdateChecker.checkGitHubLatestRelease(
                    configuredUrl = configuredUrl,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME
                )
                NightlyUpdateScheduler.recordCheckedNow(appContext)

                val found = result.getOrNull()
                if (found != null && NightlyUpdateScheduler.shouldNotifyForVersion(appContext, found.versionName)) {
                    NightlyUpdateScheduler.markNotifiedVersion(appContext, found.versionName)
                    ReminderNotificationHelper.postUpdateNotification(
                        context = appContext,
                        versionName = found.versionName,
                        downloadUrl = found.downloadUrl,
                        releaseNotes = found.releaseNotes
                    )
                }
            } catch (_: Exception) {
                // Silent failure overnight; will retry next night or on next app open
            } finally {
                pendingResult.finish()
            }
        }
    }
}
