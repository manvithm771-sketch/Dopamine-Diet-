package com.mindful.android.nativeapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindful.android.nativeapp.data.focus.NativeFocusRepository
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator

internal class NativeFocusCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val expectedSessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (expectedSessionId <= 0L) return Result.failure()
        return try {
            NativeStartupCoordinator.prepare(applicationContext)
            val dao = NativeDatabaseProvider.get(applicationContext).nativeFocusDao()
            when (NativeFocusRepository(dao).completeExpectedSessionIfExpired(expectedSessionId)) {
                com.mindful.android.nativeapp.data.focus.ExpectedFocusCompletion.COMPLETED,
                com.mindful.android.nativeapp.data.focus.ExpectedFocusCompletion.STALE -> Result.success()
                com.mindful.android.nativeapp.data.focus.ExpectedFocusCompletion.NOT_DUE -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "native_focus_session_id"
    }
}
