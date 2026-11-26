package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator
import com.mindful.android.nativeapp.data.usage.NativeUsageRefreshResult
import com.mindful.android.nativeapp.data.usage.NativeUsageRepository
import java.util.Calendar
import kotlinx.coroutines.flow.first

/** Hourly, permission-aware refresh of the current local day into the native Room database. */
internal class NativeUsageRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            NativeStartupCoordinator.prepare(applicationContext)
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val database = NativeDatabaseProvider.get(applicationContext)
            val usageDao = database.nativeUsageDao()
            val usageRepository = NativeUsageRepository(applicationContext, usageDao)

            // Backfill historical daily aggregates once. UsageEvents cannot provide
            // a year of history because Android retains those events only briefly.
            val historyPrefs = applicationContext.getSharedPreferences(
                "native_screen_time_history",
                Context.MODE_PRIVATE
            )
            val historyStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                add(Calendar.WEEK_OF_YEAR, -51)
            }.timeInMillis
            if (!historyPrefs.getBoolean("backfill_complete", false)) {
                var cursor = historyPrefs.getLong("next_day", historyStart)
                    .coerceAtLeast(historyStart)
                while (cursor < dayStart) {
                    val nextDay = Calendar.getInstance().apply {
                        timeInMillis = cursor
                        add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis
                    when (usageRepository.refreshHistoricalScreenTimeDay(cursor, nextDay)) {
                        is NativeUsageRefreshResult.Updated -> {
                            cursor = nextDay
                            historyPrefs.edit().putLong("next_day", cursor).apply()
                        }
                        NativeUsageRefreshResult.UsageAccessRequired -> return Result.success()
                    }
                }
                historyPrefs.edit().putBoolean("backfill_complete", true).apply()
            }

            // Keep the previous local day complete after UsageEvents ages out.
            val yesterdayStart = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            usageRepository.refreshHistoricalScreenTimeDay(yesterdayStart, dayStart)
            when (
                usageRepository.refreshDay(dayStart)
            ) {
                is NativeUsageRefreshResult.Updated -> {
                    val retentionWeeks = database.nativeDashboardDao()
                        .observeSettings().first()?.usageHistoryWeeks ?: 52L
                    val cutoff = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        add(Calendar.WEEK_OF_YEAR, -retentionWeeks.coerceIn(1L, 260L).toInt())
                    }.timeInMillis
                    usageDao.deleteBefore(cutoff)
                    Result.success()
                }
                NativeUsageRefreshResult.UsageAccessRequired -> Result.success()
            }
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
