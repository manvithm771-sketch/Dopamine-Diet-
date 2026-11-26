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
     * KEEP avoids starting duplicate backfills while one is already running.
     */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        workManager.enqueueUniqueWork(
            INITIAL_REFRESH_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NativeUsageRefreshWorker>().build()
        )

        val request = PeriodicWorkRequestBuilder<NativeUsageRefreshWorker>(
            1,
            TimeUnit.HOURS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
