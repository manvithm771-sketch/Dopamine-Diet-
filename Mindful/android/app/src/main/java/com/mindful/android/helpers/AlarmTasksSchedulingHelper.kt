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
package com.mindful.android.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mindful.android.receivers.alarm.MidnightResetReceiver
import com.mindful.android.utils.DateTimeUtils.todToTodayCal
import java.util.Calendar

/**
 * Helper class for scheduling the daily midnight reset.
 */
object AlarmTasksSchedulingHelper {
    private const val TAG = "Mindful.AlarmTasksSchedulingHelper"
    private const val MIDNIGHT_RESET_ALARM_ID = 101


    /**
     * Schedules the midnight reset task if it is not already scheduled.
     * Which will trigger at 12 midnight every day (with delay of 3 seconds).
     *
     * @param context               The application context.
     * @param checkBeforeScheduling Flag indicating whether to check if the task is already scheduled.
     */
    fun scheduleMidnightResetTask(context: Context, checkBeforeScheduling: Boolean) {
        if (checkBeforeScheduling) {
            val intent =
                Intent(context.applicationContext, MidnightResetReceiver::class.java).setAction(
                    MidnightResetReceiver.ACTION_START_MIDNIGHT_RESET
                )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_RESET_ALARM_ID,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                Log.d(TAG, "scheduleMidnightTask: Midnight reset task is already scheduled")
                return
            }
        }

        val cal = Calendar.getInstance()
        cal[Calendar.HOUR_OF_DAY] = 0
        cal[Calendar.MINUTE] = 0
        cal[Calendar.SECOND] = 3 // For safe side
        cal.add(Calendar.DATE, 1)

        scheduleOrUpdateExactAlarmTask(
            context = context,
            receiverClass = MidnightResetReceiver::class.java,
            intentAction = MidnightResetReceiver.ACTION_START_MIDNIGHT_RESET,
            requestCode = MIDNIGHT_RESET_ALARM_ID,
            epochTimeMs = cal.timeInMillis
        )
        Log.d(
            TAG,
            "scheduleMidnightTask: Midnight reset task scheduled successfully for " + cal.time
        )
    }

    /**
     * Schedules or updates an alarm task with the specified parameters.
     *
     * @param context       The application context.
     * @param receiverClass The receiver class for the alarm.
     * @param intentAction  The action to be set on the intent.
     * @param requestCode   A unique identifier for this alarm, used to update or cancel it later.
     * @param epochTimeMs   The time at which the alarm should go off, in milliseconds since epoch.
     * @param extraMap         An optional map of key-value pairs to be passed as extras in the `Intent`. Value should be serialized json.  Default is `null`.
     */
    private fun scheduleOrUpdateExactAlarmTask(
        context: Context,
        receiverClass: Class<*>,
        intentAction: String,
        requestCode: Int,
        epochTimeMs: Long,
        extraMap: Map<String, String>? = null,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context.applicationContext, receiverClass).setAction(intentAction)
        extraMap?.let {
            it.forEach { entry ->
                intent.putExtra(entry.key, entry.value)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    epochTimeMs,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochTimeMs,
                pendingIntent
            )
        }
    }

}
