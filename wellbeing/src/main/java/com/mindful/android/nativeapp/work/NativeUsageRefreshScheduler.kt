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

    /** Queue the initial Mindful-style 11-day history population immediately. */
    fun scheduleInitialRefresh(context: Context) {
        val appContext = context.applicationContext
        val historyPrefs = appContext.getSharedPreferences(
            "native_screen_time_history",
            Context.MODE_PRIVATE
        )
        if (historyPrefs.getBoolean("mindful_11_day_backfill_complete", false)) return

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            INITIAL_REFRESH_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NativeUsageRefreshWorker>().build()
        )
    }
}
