/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  *
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android.receivers.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.scheduleMidnightResetTask
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.services.tracking.MindfulTrackerService

class MidnightResetReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "Mindful.MidnightResetReceiver"
        const val ACTION_START_MIDNIGHT_RESET = "com.mindful.android.action.startMidnightReset"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START_MIDNIGHT_RESET) {
            WorkManager.getInstance(context).let {

                /// Enqueue midnight worker for services
                it.enqueueUniqueWork(
                    "Mindful.MidnightResetReceiver.Native",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequest.Builder(MidnightResetWorker::class.java).build()
                )

            }
        }
    }

    class MidnightResetWorker(
        private val context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        private val mTrackerServiceConn = SafeServiceConnection(
            context = context,
            serviceClass = MindfulTrackerService::class.java,
        )


        override fun doWork(): Result {
            try {
                // Let tracking service know about midnight reset
                mTrackerServiceConn.setOnConnectedCallback { service: MindfulTrackerService -> service.onMidnightReset() }
                mTrackerServiceConn.bindService()

                Log.d(TAG, "doWork: Midnight reset work completed successfully")
                return Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "doWork: Error during work execution", e)
                SharedPrefsHelper.insertCrashLogToPrefs(context, e)
                return Result.failure()
            } finally {
                // Unbind service and schedule task for the next day
                mTrackerServiceConn.unBindService()
                scheduleMidnightResetTask(context, false)
            }
        }
    }
}