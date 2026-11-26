package com.mindful.android.services.tracking

import android.app.Service.USAGE_STATS_SERVICE
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.receivers.AccessibilityReceiver
import com.mindful.android.receivers.DeviceLockUnlockReceiver
import com.mindful.android.services.accessibility.MindfulAccessibilityService
import com.mindful.android.utils.Utils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class LaunchTrackingManager(
    private val context: Context,
    private val onNewAppLaunched: (packageName: String) -> Unit,
    private val onCurrentAppRechecked: (packageName: String) -> Unit,
    private val dismissOverlay: () -> Unit,
    private val cancelReminders: () -> Unit,
) {
    companion object {
        private const val TAG = "Mindful.LaunchTrackingManager"

        // Keep the original Mindful foreground-detection cadence.
        private const val TIMER_RATE: Long = 750
    }

    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val lockUnlockReceiver = DeviceLockUnlockReceiver { isUnlocked ->
        if (isUnlocked) onDeviceUnlocked() else onDeviceLocked()
    }
    private val accessibilityReceiver = AccessibilityReceiver(
        onNewAppLaunched = { executorService.submit { invokeNewAppLaunched(it) } },
        onServiceStatusChanged = { isActive ->
            isManualTrackingOn = !isActive
            if (isActive) onDeviceLocked() else onDeviceUnlocked()
        },
    )

    private var periodicTaskHandle: ScheduledFuture<*>? = null
    private var usageStatsManager: UsageStatsManager? = null
    private var isManualTrackingOn: Boolean = true
    private var isTrackingPaused: Boolean = false

    private var lastUsageQueryTimestamp = System.currentTimeMillis()
    private var lastLaunchedApp = ""
    // Keep the same package-level foreground event tracking used by the original Mindful app.
    private val activeApps = mutableListOf<String>()

    init {
        lockUnlockReceiver.register(context)
        accessibilityReceiver.register(context)
        onDeviceUnlocked()
    }

    @WorkerThread
    private fun onDeviceUnlocked() {
        isManualTrackingOn =
            !Utils.isServiceRunning(context, MindfulAccessibilityService::class.java)

        if (isManualTrackingOn) {
            periodicTaskHandle?.cancel(true)
            periodicTaskHandle = executorService.scheduleWithFixedDelay(
                { findLaunchedApp() },
                0L,
                TIMER_RATE,
                TimeUnit.MILLISECONDS
            )
        }

        Log.d(TAG, "onDeviceUnlocked: Usage tracking started (isManual=$isManualTrackingOn)")
        executorService.submit { recheckCurrentApp(lastLaunchedApp) }
    }

    @WorkerThread
    private fun onDeviceLocked() {
        periodicTaskHandle?.cancel(true)
        periodicTaskHandle = null
        cancelReminders.invoke()
        dismissOverlay.invoke()
        Log.d(TAG, "onDeviceLocked: Usage tracking stopped")
    }

    /**
     * Detect foreground app transitions using the same UsageEvents logic as Mindful.
     * @param msInPastFromLastCheck past milliseconds to include in the query
     */
    @WorkerThread
    private fun findLaunchedApp(msInPastFromLastCheck: Long = 0L) {
        if (usageStatsManager == null) {
            usageStatsManager =
                context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        }

        val timeNow = System.currentTimeMillis()
        val start = lastUsageQueryTimestamp - msInPastFromLastCheck
        lastUsageQueryTimestamp = timeNow

        val usageEvents = usageStatsManager?.queryEvents(start, timeNow)
        usageEvents?.let {
            val currentEvent = UsageEvents.Event()
            while (it.hasNextEvent()) {
                it.getNextEvent(currentEvent)
                val packageName = currentEvent.packageName ?: continue
                when (currentEvent.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> activeApps.add(packageName)
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> activeApps.remove(packageName)
                    else -> Unit
                }
            }
        }

        activeApps.firstOrNull()?.let { packageName ->
            if (lastLaunchedApp == packageName) return
            invokeNewAppLaunched(packageName)
        }
        Log.d(TAG, "findLaunchedApp: Opened app:$lastLaunchedApp Active apps: $activeApps")
    }

    /** Counts a genuine foreground transition as a launch. */
    private fun invokeNewAppLaunched(packageName: String) {
        lastLaunchedApp = packageName
        if (isTrackingPaused || packageName.isEmpty()) return
        onNewAppLaunched.invoke(packageName)
    }

    /** Re-evaluates the current app without incrementing its launch counter. */
    private fun recheckCurrentApp(packageName: String) {
        if (isTrackingPaused || packageName.isEmpty()) return
        onCurrentAppRechecked.invoke(packageName)
    }

    fun reInvokeLastLaunchEvent() {
        // Service rule updates can arrive on the main thread; keep UsageStats queries off it.
        executorService.submit { recheckCurrentApp(lastLaunchedApp) }
    }

    fun pauseResumeTracking(shouldPause: Boolean) {
        isTrackingPaused = shouldPause
        if (!shouldPause) recheckCurrentApp(lastLaunchedApp)
    }

    fun detectActiveAppForBedtime() {
        findLaunchedApp(6 * 60 * 60 * 1000)
    }

    fun dispose() {
        lockUnlockReceiver.unRegister(context)
        accessibilityReceiver.unRegister(context)
        periodicTaskHandle?.cancel(true)
        executorService.shutdownNow()
        onDeviceLocked()
    }
}