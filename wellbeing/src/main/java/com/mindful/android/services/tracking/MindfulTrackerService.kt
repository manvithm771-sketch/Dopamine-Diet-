package com.mindful.android.services.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.app.AppOpsManager
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.AppConstants
import com.mindful.android.R
import com.mindful.android.generics.ServiceBinder
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator
import com.mindful.android.nativeapp.restrictions.buildNativeRestrictionRules
import com.mindful.android.nativeapp.restrictions.filterNativeRestrictionInputs
import com.mindful.android.nativeapp.restrictions.ManagedAppSuspensionController
import com.mindful.android.nativeapp.restrictions.protectedSystemPackageNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MindfulTrackerService : Service() {
    companion object {
        private const val TAG = "Mindful.MindfulTrackerService"
        const val ACTION_RESTORE_SAVED_RESTRICTIONS =
            "com.mindful.android.action.restoreSavedAppRestrictions"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mBinder = ServiceBinder(this@MindfulTrackerService)

    private lateinit var overlayManager: OverlayManager
    private lateinit var reminderManager: ReminderManager

    private lateinit var restrictionManager: RestrictionManager
    val getRestrictionManager get() = restrictionManager

    private lateinit var launchTrackingManager: LaunchTrackingManager
    val getLaunchTrackingManager get() = launchTrackingManager

    override fun onCreate() {
        overlayManager = OverlayManager(this)
        reminderManager = ReminderManager(overlayManager, ::onNewAppLaunch)
        restrictionManager = RestrictionManager(this, ::stopIfNoUsage)
        launchTrackingManager = LaunchTrackingManager(
            context = this,
            onNewAppLaunched = ::onNewAppLaunch,
            dismissOverlay = { overlayManager.dismissSheetOverlay() },
            cancelReminders = { reminderManager.cancelReminders() },
        )
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceBinder.ACTION_START_MINDFUL_SERVICE -> {
                startFgService()
                return START_STICKY
            }
            ACTION_RESTORE_SAVED_RESTRICTIONS,
            null -> {
                // Android may recreate a START_STICKY service with a null intent.
                // Restore the durable Room rules instead of stopping with empty in-memory state.
                startFgService()
                restoreSavedRestrictions()
                return START_STICKY
            }
            else -> {
                stopIfNoUsage()
                return START_NOT_STICKY
            }
        }
    }

    private fun restoreSavedRestrictions() {
        serviceScope.launch {
            try {
                NativeStartupCoordinator.prepare(applicationContext)
                if (!hasUsageAccess() || !Settings.canDrawOverlays(applicationContext)) {
                    Log.w(TAG, "restoreSavedRestrictions: Required Usage Access or Overlay permission is missing")
                    withContext(Dispatchers.Main) { stopIfNoUsage() }
                    return@launch
                }

                val dashboard = NativeDatabaseProvider.get(applicationContext).nativeDashboardDao()
                val appRows = dashboard.observeAppRestrictions().first()
                val groupRows = dashboard.observeRestrictionGroups().first()
                val installedPackages = packageManager
                    .getInstalledApplications(PackageManager.MATCH_ALL)
                    .mapTo(mutableSetOf()) { it.packageName }
                val filtered = filterNativeRestrictionInputs(
                    appRows = appRows,
                    groupRows = groupRows,
                    installedPackageNames = installedPackages,
                    protectedPackageNames = protectedSystemPackageNames(applicationContext)
                )
                val rules = buildNativeRestrictionRules(filtered.apps, filtered.groups)
                ManagedAppSuspensionController(applicationContext).reconcileConfiguredPackages(
                    rules.apps.keys + rules.groups.values.flatMap { it.distractingApps }
                )
                withContext(Dispatchers.Main) {
                    restrictionManager.updateRestrictions(rules.apps, rules.groups)
                }
                // The tracker may already have detected the foreground app before Room rules
                // finished loading. Re-evaluate it now that the saved restrictions are active.
                launchTrackingManager.reInvokeLastLaunchEvent()
                Log.i(
                    TAG,
                    "restoreSavedRestrictions: Restored ${rules.apps.size} app rules and ${rules.groups.size} group rules"
                )
            } catch (error: Exception) {
                Log.e(TAG, "restoreSavedRestrictions: Could not restore saved app-blocking rules", error)
                SharedPrefsHelper.insertCrashLogToPrefs(applicationContext, error)
                withContext(Dispatchers.Main) { stopIfNoUsage() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun startFgService() {
        try {
            val notification = NotificationHelper.buildFgServiceNotification(
                this,
                getString(R.string.app_blocker_running_notification_info)
            )
            startForeground(AppConstants.TRACKER_SERVICE_NOTIFICATION_ID, notification)
            Log.d(TAG, "startFgService: TRACKER service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "startFgService: Failed to start TRACKER service", e)
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
            stopIfNoUsage()
        }
    }

    fun onMidnightReset() {
        restrictionManager.resetCache()
        ManagedAppSuspensionController(applicationContext).restoreAll()
        overlayManager.dismissSheetOverlay()
        val reminderAwaiting = reminderManager.cancelReminders()

        // Means app is active but timer is not over and now it is reset so re-launch same event again
        if (reminderAwaiting) launchTrackingManager.reInvokeLastLaunchEvent()
    }


    @WorkerThread
    private fun onNewAppLaunch(packageName: String) {
        processAppRestriction(packageName)
    }

    private fun processAppRestriction(packageName: String) {
        try {
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()

            val currentOrFutureState = restrictionManager.isAppRestricted(packageName)
            Log.d(TAG, "processAppRestriction: $packageName evaluated => $currentOrFutureState")

            if (currentOrFutureState == null || currentOrFutureState.timeLeftMillis > 0L) {
                // A rule was removed, or the daily limit has not been reached yet.
                ManagedAppSuspensionController(applicationContext).restorePackage(packageName)
            } else {
                val hiddenByDevicePolicy =
                    ManagedAppSuspensionController(applicationContext).hidePackage(packageName)
                if (!hiddenByDevicePolicy) {
                    // Preserve the existing Mindful blocking experience on ordinary devices
                    // and for packages Android refuses to suspend.
                    overlayManager.showSheetOverlay(
                        packageName = packageName,
                        restrictionState = currentOrFutureState,
                    )
                }
            }
        } catch (e: Exception) {
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
            Log.e(TAG, "processAppRestriction: Failed to process app restriction for $packageName", e)
        }
    }

    private fun stopIfNoUsage() {
        if (restrictionManager.isIdle) {
            Log.d(TAG, "Service no longer needed, stopping")
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Dispose tracking only when the service is actually destroyed. A bound Activity
        // may still save a new rule immediately after the last rule is removed.
        if (::launchTrackingManager.isInitialized) launchTrackingManager.dispose()
        if (::reminderManager.isInitialized) reminderManager.cancelReminders()
        if (::overlayManager.isInitialized) overlayManager.dismissSheetOverlay()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy: TRACKER service destroyed successfully")
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == ServiceBinder.ACTION_BIND_TO_MINDFUL) mBinder else null
    }
}