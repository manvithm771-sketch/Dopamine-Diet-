package com.mindful.android.nativeapp.restrictions

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.mindful.android.enums.ReminderType
import com.mindful.android.AppConstants
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.helpers.device.ImpSystemAppsHelper
import com.mindful.android.models.AppRestriction
import com.mindful.android.models.RestrictionGroup
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.services.tracking.MindfulTrackerService
import org.json.JSONArray

internal fun protectedSystemPackageNames(context: Context): Set<String> =
    ImpSystemAppsHelper.fetchImpApps(context.packageManager).apply {
        add(context.packageName)
        add(AppConstants.SETTINGS_PACKAGE)
    }

internal data class NativeRestrictionRules(
    val apps: HashMap<String, AppRestriction>,
    val groups: HashMap<Int, RestrictionGroup>
)

internal data class NativeRestrictionInputs(
    val apps: List<NativeAppRestrictionEntity>,
    val groups: List<NativeRestrictionGroupEntity>
)

internal fun filterNativeRestrictionInputs(
    appRows: List<NativeAppRestrictionEntity>,
    groupRows: List<NativeRestrictionGroupEntity>,
    installedPackageNames: Set<String>,
    protectedPackageNames: Set<String> = emptySet()
): NativeRestrictionInputs {
    val activeApps = appRows.filter { row ->
        row.appPackage in installedPackageNames &&
            row.appPackage !in protectedPackageNames &&
            (row.timerSec > 0L || row.launchLimit > 0L || row.associatedGroupId != null)
    }
    val activeGroups = groupRows.mapNotNull { group ->
        val eligiblePackages = parsePackageSet(group.distractingAppsJson)
            .filter { it in installedPackageNames && it !in protectedPackageNames }
        if (group.timerSec <= 0L || eligiblePackages.isEmpty()) {
            null
        } else {
            group.copy(distractingAppsJson = JSONArray(eligiblePackages).toString())
        }
    }
    return NativeRestrictionInputs(activeApps, activeGroups)
}

internal fun buildNativeRestrictionRules(
    appRows: List<NativeAppRestrictionEntity>,
    groupRows: List<NativeRestrictionGroupEntity>
): NativeRestrictionRules {
    val apps = HashMap<String, AppRestriction>()
    appRows.forEach { row ->
        apps[row.appPackage] = AppRestriction(
            appPackage = row.appPackage,
            timerSec = row.timerSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            launchLimit = row.launchLimit.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            reminderType = ReminderType.fromName(row.reminderType),
            associatedGroupId = row.associatedGroupId
                ?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt()
        )
    }
    val groups = HashMap<Int, RestrictionGroup>()
    groupRows.forEach { row ->
        val id = row.id.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        groups[id] = RestrictionGroup(
            id = id,
            groupName = row.groupName,
            timerSec = row.timerSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            distractingApps = parsePackageSet(row.distractingAppsJson)
        )
    }
    return NativeRestrictionRules(apps, groups)
}

private fun parsePackageSet(raw: String): Set<String> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        runCatching { array.getString(index).trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }.toSet()
}.getOrDefault(emptySet())

/** Bridges Room restriction rules to Mindful's existing Android tracker and overlay service. */
internal class NativeRestrictionController(context: Context) {
    private val appContext = context.applicationContext
    private val connection = SafeServiceConnection(appContext, MindfulTrackerService::class.java)

    fun apply(
        appRows: List<NativeAppRestrictionEntity>,
        groupRows: List<NativeRestrictionGroupEntity>
    ): Boolean {
        if (!hasUsageAccess() || !Settings.canDrawOverlays(appContext)) return false
        val installedPackages = installedPackageNames()
        val filteredRows = filterNativeRestrictionInputs(
            appRows = appRows,
            groupRows = groupRows,
            installedPackageNames = installedPackages,
            protectedPackageNames = protectedSystemPackageNames(appContext)
        )
        val rules = buildNativeRestrictionRules(filteredRows.apps, filteredRows.groups)
        ManagedAppSuspensionController(appContext).reconcileConfiguredPackages(
            rules.apps.keys + rules.groups.values.flatMap { it.distractingApps }
        )
        if (rules.apps.isEmpty() && rules.groups.isEmpty()) {
            if (connection.isActive) {
                connection.service?.getRestrictionManager?.updateRestrictions(hashMapOf(), hashMapOf())
                // The service shuts down its tracker when its final rule is removed.
                // Unbind as well so a later rule save creates a fresh service instance.
                connection.unBindService()
            } else {
                // The tracker may outlive this Activity because it is a started foreground
                // service. Bind once to clear stale in-memory rules, then release the binding
                // so the service can finish stopping cleanly.
                connection.setOnConnectedCallback { service ->
                    service.getRestrictionManager.updateRestrictions(hashMapOf(), hashMapOf())
                    connection.unBindService()
                }
                connection.bindService()
            }
            return true
        }
        if (connection.isActive) {
            connection.service?.let { service ->
                service.getRestrictionManager.updateRestrictions(rules.apps, rules.groups)
                service.getLaunchTrackingManager.reInvokeLastLaunchEvent()
            }
        } else {
            connection.setOnConnectedCallback { service ->
                service.getRestrictionManager.updateRestrictions(rules.apps, rules.groups)
                service.getLaunchTrackingManager.reInvokeLastLaunchEvent()
            }
            connection.startAndBind()
        }
        return true
    }

    fun close() = connection.unBindService()

    @Suppress("DEPRECATION")
    private fun installedPackageNames(): Set<String> =
        appContext.packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .mapTo(mutableSetOf()) { it.packageName }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
