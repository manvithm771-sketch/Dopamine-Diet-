package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal object NativeFocusCompletionScheduler {
    private fun workName(sessionId: Long) = "mindful-focus-complete-$sessionId"

    fun schedule(
        context: Context,
        sessionId: Long,
        plannedDurationSecs: Long,
        startTimeEpochMillis: Long
    ) {
        if (plannedDurationSecs <= 0L) return
        val elapsedSecs = ((System.currentTimeMillis() - startTimeEpochMillis) / 1_000L)
            .coerceAtLeast(0L)
        val remainingSecs = (plannedDurationSecs - elapsedSecs).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<NativeFocusCompletionWorker>()
            .setInputData(workDataOf(NativeFocusCompletionWorker.KEY_SESSION_ID to sessionId))
            .setInitialDelay(remainingSecs, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(sessionId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, sessionId: Long) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(sessionId))
    }
}
