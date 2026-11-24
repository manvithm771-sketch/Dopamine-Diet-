package com.mindful.android.nativeapp.focus

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.nativeapp.restrictions.protectedSystemPackageNames
import com.mindful.android.models.FocusSession
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import com.mindful.android.services.timer.FocusSessionService
import org.json.JSONArray

internal fun filterNativeFocusPackages(
    packages: Set<String>,
    installedPackageNames: Set<String>,
    protectedPackageNames: Set<String>
): Set<String> = packages.filterTo(linkedSetOf()) {
    it in installedPackageNames && it !in protectedPackageNames
}

internal enum class NativeFocusServiceStartResult {
    READY,
    STARTED,
    NO_DISTRACTING_APPS,
    REQUIRED_PERMISSIONS_MISSING,
    DND_PERMISSION_MISSING
}

/** Adapts native focus records to Mindful's existing foreground focus/enforcement service. */
internal class NativeFocusServiceController(
    context: Context,
    private val installedPackageNames: () -> Set<String> = {
        context.applicationContext.packageManager.getInstalledApplications(0)
            .mapTo(mutableSetOf()) { it.packageName }
    },
    private val protectedPackageNames: () -> Set<String> = {
        protectedSystemPackageNames(context.applicationContext)
    }
) {
    private val appContext = context.applicationContext
    private val connection = SafeServiceConnection(appContext, FocusSessionService::class.java)

    fun validate(profile: NativeFocusProfileEntity?): NativeFocusServiceStartResult {
        if (restrictablePackages(profile?.distractingAppsJson ?: "[]").isEmpty()) {
            return NativeFocusServiceStartResult.NO_DISTRACTING_APPS
        }
        if (!hasUsageAccess() || !Settings.canDrawOverlays(appContext)) {
            return NativeFocusServiceStartResult.REQUIRED_PERMISSIONS_MISSING
        }
        if (profile?.shouldStartDnd == true) {
            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                return NativeFocusServiceStartResult.DND_PERMISSION_MISSING
            }
        }
        return NativeFocusServiceStartResult.READY
    }

    fun start(
        active: NativeFocusSessionEntity,
        profile: NativeFocusProfileEntity?
    ): NativeFocusServiceStartResult {
        val validation = validate(profile)
        if (validation != NativeFocusServiceStartResult.READY) return validation
        val distractingApps = restrictablePackages(profile?.distractingAppsJson ?: "[]")
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val focusSession = FocusSession(
            toggleDnd = profile?.shouldStartDnd == true &&
                notificationManager.isNotificationPolicyAccessGranted,
            startTimeMsEpoch = active.startDateTimeEpochMillis,
            durationSecs = active.durationSecs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            distractingApps = distractingApps
        )
        connection.setOnConnectedCallback { service -> service.startFocusSession(focusSession) }
        connection.startAndBind()
        return NativeFocusServiceStartResult.STARTED
    }

    fun finish(successful: Boolean) {
        if (connection.isActive) {
            connection.service?.giveUpOrStopFocusSession(successful)
            connection.unBindService()
            return
        }
        connection.setOnConnectedCallback { service ->
            service.giveUpOrStopFocusSession(successful)
            connection.unBindService()
        }
        connection.bindService()
    }

    fun close() = connection.unBindService()

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun restrictablePackages(raw: String): Set<String> =
        filterNativeFocusPackages(
            packages = parsePackages(raw),
            installedPackageNames = installedPackageNames(),
            protectedPackageNames = protectedPackageNames()
        )

    private fun parsePackages(raw: String): Set<String> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.getString(index).trim().takeIf { it.isNotEmpty() }
        }.toSet()
    }.getOrDefault(emptySet())
}
