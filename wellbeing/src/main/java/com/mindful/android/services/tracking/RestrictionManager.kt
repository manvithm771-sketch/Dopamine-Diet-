package com.mindful.android.services.tracking

import android.app.Service.USAGE_STATS_SERVICE
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.mindful.android.enums.RestrictionType
import com.mindful.android.helpers.usages.ScreenUsageHelper
import com.mindful.android.models.AppRestriction
import com.mindful.android.models.RestrictionGroup
import com.mindful.android.models.RestrictionState
import java.util.Calendar

class RestrictionManager(
    private val context: Context,
    private val stopIfNoUsage: () -> Unit,
    private val usageStatsManager: UsageStatsManager = context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager,
    private val screenUsageProvider: () -> Map<String, Long> = {
        ScreenUsageHelper.fetchAppUsageTodayTillNow(usageStatsManager)
    },
) {
    private val TAG = "Mindful.RestrictionManager"

    companion object {
        private const val LAUNCH_COUNT_DAY_KEY = "local_day"
        private const val LAUNCH_COUNT_PREFIX = "count:"
    }

    val isIdle: Boolean
        get() = focusedApps.isEmpty()
                && appsRestrictions.isEmpty()
                && restrictionGroups.isEmpty()

    // Restrictions
    private var appsRestrictions = HashMap<String, AppRestriction>()
    private var restrictionGroups = HashMap<Int, RestrictionGroup>()

    // Focus
    private var focusedApps = setOf<String>()

    //  Cache
    private val appsLaunchCount = HashMap<String, Int>(0)
    val getAppsLaunchCount: HashMap<String, Int> get() = appsLaunchCount
    private val launchCountPrefs = context.getSharedPreferences("app_blocking_launch_counts", Context.MODE_PRIVATE)
    private var launchCountDayKey: String = ""

    init {
        restoreLaunchCountsForToday()
    }

    private val alreadyRestrictedApps = HashMap<String, RestrictionState>(0)
    private val alreadyRestrictedGroups = HashMap<Int, RestrictionState>(0)

    fun resetCache() {
        alreadyRestrictedApps.clear()
        alreadyRestrictedGroups.clear()
        appsLaunchCount.clear()
        launchCountDayKey = currentLocalDayKey()
        launchCountPrefs.edit().clear().putString(LAUNCH_COUNT_DAY_KEY, launchCountDayKey).commit()
    }

    private fun currentLocalDayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun restoreLaunchCountsForToday() {
        val today = currentLocalDayKey()
        launchCountDayKey = today
        if (launchCountPrefs.getString(LAUNCH_COUNT_DAY_KEY, null) != today) {
            launchCountPrefs.edit().clear().putString(LAUNCH_COUNT_DAY_KEY, today).commit()
            return
        }

        launchCountPrefs.all.forEach { (key, value) ->
            if (key.startsWith(LAUNCH_COUNT_PREFIX)) {
                val packageName = key.removePrefix(LAUNCH_COUNT_PREFIX)
                val count = value as? Int
                if (packageName.isNotBlank() && count != null && count > 0) {
                    appsLaunchCount[packageName] = count
                }
            }
        }
    }

    private fun ensureLaunchCountsForToday() {
        val today = currentLocalDayKey()
        if (today == launchCountDayKey) return
        appsLaunchCount.clear()
        alreadyRestrictedApps.clear()
        alreadyRestrictedGroups.clear()
        launchCountDayKey = today
        launchCountPrefs.edit().clear().putString(LAUNCH_COUNT_DAY_KEY, today).commit()
    }

    fun updateRestrictions(
        appsRestrictionsMap: HashMap<String, AppRestriction>?,
        restrictionGroupsMap: HashMap<Int, RestrictionGroup>?,
    ) {
        appsRestrictionsMap?.let {
            appsRestrictions = it
            alreadyRestrictedApps.clear()
            Log.d(TAG, "updateRestrictions: Apps restrictions updated")
        }

        restrictionGroupsMap?.let {
            restrictionGroups = it
            alreadyRestrictedGroups.clear()
            Log.d(TAG, "updateRestrictions: Restriction groups updated")
        }
        stopIfNoUsage.invoke()
    }

    fun updateFocusedApps(apps: Set<String>?) {
        focusedApps = apps ?: emptySet()
        if (apps == null) stopIfNoUsage.invoke()
        Log.d(TAG, "updateFocusedApps: Focus apps updated: $focusedApps")
    }


    // A real foreground transition counts as a launch. Timer, unlock, and midnight
    // callbacks must re-evaluate limits without consuming another launch.
    fun isAppRestricted(packageName: String): RestrictionState? =
        evaluateAppRestriction(packageName, countLaunch = true)

    fun recheckAppRestriction(packageName: String): RestrictionState? =
        evaluateAppRestriction(packageName, countLaunch = false)

    private fun evaluateAppRestriction(
        packageName: String,
        countLaunch: Boolean,
    ): RestrictionState? {
        ensureLaunchCountsForToday()

        // If already restricted by focus or cached
        val alreadyRestrictedState = evaluateIfAlreadyRestricted(packageName)
        if (alreadyRestrictedState != null) return alreadyRestrictedState

        // If no restrictions
        val restriction = appsRestrictions[packageName] ?: return null

        val launchCount = if (countLaunch) {
            (appsLaunchCount[packageName] ?: 0) + 1
        } else {
            appsLaunchCount[packageName] ?: 0
        }
        if (countLaunch) {
            appsLaunchCount[packageName] = launchCount
            launchCountPrefs.edit()
                .putString(LAUNCH_COUNT_DAY_KEY, launchCountDayKey)
                .putInt("$LAUNCH_COUNT_PREFIX$packageName", launchCount)
                .apply()
        }
        if (restriction.launchLimit > 0 && launchCount > restriction.launchLimit) {
            return RestrictionState(type = RestrictionType.LAUNCH_COUNT).also {
                alreadyRestrictedApps[packageName] = it
            }
        }

        val futureStates: MutableSet<RestrictionState> = mutableSetOf()
        evaluateScreenTimeLimit(restriction, futureStates)?.let { return it }

        return futureStates.minByOrNull { it.timeLeftMillis }
    }

    private fun evaluateIfAlreadyRestricted(packageName: String): RestrictionState? {
        return when {
            focusedApps.contains(packageName) -> RestrictionState(
                type = RestrictionType.FOCUS
            )

            alreadyRestrictedApps.containsKey(packageName) -> alreadyRestrictedApps[packageName]
            else -> null
        }
    }


    private fun evaluateScreenTimeLimit(
        restriction: AppRestriction,
        futureStates: MutableSet<RestrictionState>,
    ): RestrictionState? {
        // Usage map
        val screenUsage = screenUsageProvider.invoke()

        /// Check for app timer
        if (restriction.timerSec > 0) {
            val screenTimeSec: Long = screenUsage[restriction.appPackage] ?: 0

            /// App timer ran out
            if (screenTimeSec >= restriction.timerSec) {
                Log.d(TAG, "evaluateScreenTimeLimit: App's timer is over")
                val state = RestrictionState(
                    type = RestrictionType.APP_TIMER,
                    screenTimeUsed = screenTimeSec,
                    screenTimeLimit = restriction.timerSec.toLong(),
                    reminderType = restriction.reminderType,
                )

                alreadyRestrictedApps[restriction.appPackage] = state
                return state
            } else {
                /// App timer left so add expiration timestamp
                val leftAppLimitMs = (restriction.timerSec - screenTimeSec) * 1000L
                futureStates.add(
                    RestrictionState(
                        type = RestrictionType.APP_TIMER,
                        timeLeftMillis = leftAppLimitMs,
                        screenTimeUsed = screenTimeSec,
                        screenTimeLimit = restriction.timerSec.toLong(),
                        reminderType = restriction.reminderType,
                    )
                )
            }
        }


        /// Check group's timer ran out
        restrictionGroups[restriction.associatedGroupId]?.let { group ->
            // If group is already restricted
            if (alreadyRestrictedGroups.containsKey(group.id)) {
                return alreadyRestrictedGroups[group.id]
            }

            if (group.timerSec > 0) {
                val groupScreenTimeSec = group.distractingApps.sumOf { screenUsage[it] ?: 0 }

                /// group timer ran out
                if (groupScreenTimeSec >= group.timerSec) {
                    Log.d(TAG, "evaluateScreenTimeLimit: App's timer is over")
                    val state = RestrictionState(
                        type = RestrictionType.GROUP_TIMER,
                        screenTimeUsed = groupScreenTimeSec,
                        screenTimeLimit = group.timerSec.toLong(),
                        reminderType = restriction.reminderType,
                        groupName = group.groupName,
                    )

                    alreadyRestrictedGroups[group.id] = state
                    return state
                } else {
                    /// group timer left so add expiration timestamp
                    val leftAppLimitMs = (group.timerSec - groupScreenTimeSec) * 1000L
                    futureStates.add(
                        RestrictionState(
                            type = RestrictionType.GROUP_TIMER,
                            timeLeftMillis = leftAppLimitMs,
                            screenTimeUsed = groupScreenTimeSec,
                            screenTimeLimit = group.timerSec.toLong(),
                            reminderType = restriction.reminderType,
                            groupName = group.groupName,
                        )
                    )
                }
            }
        }

        return null
    }
}
