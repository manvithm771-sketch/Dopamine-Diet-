/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android.helpers.usages

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * Calculates foreground screen time without adding overlapping app intervals.
 *
 * Android can report activity-resume events for more than one app around
 * multi-window and activity transitions. Counting every activity interval
 * independently can make the device total exceed the actual elapsed time.
 * We attribute each instant to the most recently resumed activity.
 */
object ScreenUsageHelper {
    /**
     * Fetches screen usage statistics for a specified time interval.
     *
     * @return A map of package names to foreground usage time in seconds.
     */
    fun fetchUsageForInterval(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        strict: Boolean = false,
    ): Map<String, Long> {
        if (end <= start) return emptyMap()

        val usageMap = mutableMapOf<String, Long>()
        var activePackageName: String? = null
        var activeEventKey: String? = null
        var activeStartTime: Long? = null

        fun closeActiveInterval(atTime: Long) {
            val packageName = activePackageName
            val intervalStart = activeStartTime

            if (packageName != null && intervalStart != null) {
                val intervalEnd = atTime.coerceAtMost(end)
                if (intervalEnd > intervalStart) {
                    usageMap[packageName] = usageMap.getOrDefault(packageName, 0L) +
                        (intervalEnd - intervalStart)
                }
            }

            activePackageName = null
            activeEventKey = null
            activeStartTime = null
        }

        try {
            // Load earlier events so an app already open at 'start' is handled.
            val usageEvents = usageStatsManager.queryEvents(
                (start - (3 * 60 * 60 * 1000L)).coerceAtLeast(0L),
                end,
            )

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                val eventTime = event.timeStamp
                val packageName = event.packageName ?: continue
                val eventKey = "${packageName}|${event.className ?: ""}"

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // Close the previous foreground activity before switching.
                        closeActiveInterval(eventTime)
                        activePackageName = packageName
                        activeEventKey = eventKey
                        activeStartTime = eventTime.coerceAtLeast(start)
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        // Ignore delayed pause/stop events from an older activity.
                        if (activeEventKey == eventKey) {
                            closeActiveInterval(eventTime)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (strict) throw error
        }

        // Include the final foreground interval if it is still open.
        closeActiveInterval(end)

        return usageMap
            .mapValues { it.value / 1000L }
            .filterValues { it > 0L }
    }

    /**
     * Fetches all installed applications' usage from midnight until now.
     */
    fun fetchAppUsageTodayTillNow(
        usageStatsManager: UsageStatsManager,
    ): Map<String, Long> {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        return fetchUsageForInterval(
            usageStatsManager = usageStatsManager,
            start = calendar.timeInMillis,
            end = System.currentTimeMillis(),
        )
    }
}
