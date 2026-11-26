package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object NativeUsageRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "mindful-native-usage-refresh"
    private const val INITIAL_REFRESH_WORK_NAME = "mindful-native-usage-initial-refresh"

    /**
     * Queue an immediate pass so the 52-week history backfill does not wait
     * for the first hourly periodic-work window after installation or upgrade.
     */
    fun schedule(context: Context) {
        scheduleInitialRefresh(context)

        val request = PeriodicWorkRequestBuilder<NativeUsageRefreshWorker>(
            1,
            TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Retry immediately when the app returns from Usage Access settings.
     * KEEP prevents duplicate work while a backfill is already running.
     */
    fun scheduleInitialRefresh(context: Context) {
        val appContext = context.applicationContext
        val historyPrefs = appContext.getSharedPreferences(
            "native_screen_time_history",
            Context.MODE_PRIVATE
        )
        if (historyPrefs.getBoolean("backfill_complete", false)) return

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            INITIAL_REFRESH_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NativeUsageRefreshWorker>().build()
        )
    }
}
