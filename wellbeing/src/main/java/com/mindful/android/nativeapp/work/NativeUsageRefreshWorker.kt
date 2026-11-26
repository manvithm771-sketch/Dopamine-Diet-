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
            when (
                NativeUsageRepository(applicationContext, usageDao).refreshDay(dayStart)
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
