package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object NativeUsageRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "mindful-native-usage-refresh"

    /** WorkManager enforces a 15-minute minimum; one-hour cadence is intentionally conservative. */
    fun schedule(context: Context) {
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
}
