package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator
import com.mindful.android.nativeapp.data.usage.NativeUsageRefreshResult
import com.mindful.android.nativeapp.data.usage.NativeUsageRepository
import java.util.Calendar

/**
 * Mindful-style usage refresh: populate the recent 10 completed days once,
 * refresh today, then keep today's usage current on the normal hourly schedule.
 */
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
            val repository = NativeUsageRepository(applicationContext, usageDao)
            val historyPrefs = applicationContext.getSharedPreferences(
                "native_screen_time_history",
                Context.MODE_PRIVATE
            )

            // Match Mindful's recent-history population pattern. The current day
            // is refreshed separately below, giving 11 calendar days in total.
            val backfillKey = "mindful_11_day_backfill_complete"
            if (!historyPrefs.getBoolean(backfillKey, false)) {
                for (daysAgo in 1..10) {
                    val start = Calendar.getInstance().apply {
                        timeInMillis = dayStart
                        add(Calendar.DAY_OF_YEAR, -daysAgo)
                    }.timeInMillis
                    val end = Calendar.getInstance().apply {
                        timeInMillis = start
                        add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis

                    // Mindful's provider calls fetchAppsUsageForInterval for each
                    // completed day, then batch-inserts the returned app/network rows.
                    // Do the same here without clearing other records for that date.
                    when (repository.refreshDay(start, end, replaceExisting = false)) {
                        is NativeUsageRefreshResult.Updated -> Unit
                        NativeUsageRefreshResult.UsageAccessRequired -> return Result.success()
                    }
                }
                historyPrefs.edit().putBoolean(backfillKey, true).apply()
            }

            when (repository.refreshDay(dayStart)) {
                is NativeUsageRefreshResult.Updated -> {
                    val cutoff = Calendar.getInstance().apply {
                        timeInMillis = dayStart
                        add(Calendar.DAY_OF_YEAR, -10)
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
