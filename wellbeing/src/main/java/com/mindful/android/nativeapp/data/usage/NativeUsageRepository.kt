package com.mindful.android.nativeapp.data.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.mindful.android.AppConstants
import com.mindful.android.helpers.usages.NetworkUsageHelper
import com.mindful.android.helpers.usages.ScreenUsageHelper
import com.mindful.android.nativeapp.data.local.NativeUsageDao
import com.mindful.android.nativeapp.data.local.entity.NativeAppUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class NativeLaunchableApp(
    val packageName: String,
    val uid: Int
)

internal sealed class NativeUsageRefreshResult {
    data class Updated(val rowsWritten: Int) : NativeUsageRefreshResult()
    object UsageAccessRequired : NativeUsageRefreshResult()
}

/**
 * Reads Android UsageStats/NetworkStats for one local calendar day and persists
 * the result into the isolated native Room database.
 *
 * The caller supplies local-midnight epoch milliseconds. Screen time is stored
 * in seconds and network traffic in KB, matching the original Flutter model.
 */
internal class NativeUsageRepository(
    context: Context,
    private val usageDao: NativeUsageDao
) {
    private val appContext = context.applicationContext

    suspend fun refreshDay(
        dayStartEpochMillis: Long,
        endEpochMillis: Long = System.currentTimeMillis()
    ): NativeUsageRefreshResult = withContext(Dispatchers.IO) {
        require(dayStartEpochMillis >= 0L) { "Day start must be a valid epoch timestamp." }
        require(endEpochMillis > dayStartEpochMillis) { "Usage interval must have a positive duration." }

        if (!hasUsageAccess()) return@withContext NativeUsageRefreshResult.UsageAccessRequired

        val usageStatsManager = appContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val networkStatsManager = appContext.getSystemService(
            Context.NETWORK_STATS_SERVICE
        ) as NetworkStatsManager

        val screenUsage = ScreenUsageHelper.fetchUsageForInterval(
            usageStatsManager,
            dayStartEpochMillis,
            endEpochMillis,
            strict = true
        )
        val mobileUsage = NetworkUsageHelper.fetchMobileUsageForInterval(
            networkStatsManager,
            dayStartEpochMillis,
            endEpochMillis,
            strict = true
        )
        val wifiUsage = NetworkUsageHelper.fetchWifiUsageForInterval(
            networkStatsManager,
            dayStartEpochMillis,
            endEpochMillis,
            strict = true
        )

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = appContext.packageManager.queryIntentActivities(
            launcherIntent,
            0
        ).mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            NativeLaunchableApp(
                packageName = activityInfo.packageName,
                uid = activityInfo.applicationInfo.uid
            )
        }

        val rows = buildNativeUsageRows(
            dayStartEpochMillis = dayStartEpochMillis,
            launchableApps = launchableApps,
            screenUsageSeconds = screenUsage,
            mobileUsageKb = mobileUsage,
            wifiUsageKb = wifiUsage
        )
        usageDao.replaceDay(dayStartEpochMillis, rows)
        NativeUsageRefreshResult.Updated(rows.size)
    }


    /**
     * Backfills Android's retained daily UsageStats into the local history table.
     * Android controls how far back UsageStats remain available; missing days are
     * deliberately left untouched rather than represented as fabricated zero usage.
     */
    suspend fun refreshHistory(weeks: Int = 52): NativeUsageRefreshResult = withContext(Dispatchers.IO) {
        require(weeks in 1..260) { "History range must be between 1 and 260 weeks." }
        if (!hasUsageAccess()) return@withContext NativeUsageRefreshResult.UsageAccessRequired

        val endEpochMillis = System.currentTimeMillis()
        val startCalendar = java.util.Calendar.getInstance().apply {
            timeInMillis = endEpochMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(weeks * 7 - 1))
        }
        val startEpochMillis = startCalendar.timeInMillis
        val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dayCalendar = java.util.Calendar.getInstance().apply {
            timeInMillis = startEpochMillis
        }

        var rowsWritten = 0
        repeat(weeks * 7) {
            val dayStart = dayCalendar.timeInMillis
            if (dayStart >= endEpochMillis) return@repeat
            val dayEnd = (dayCalendar.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis.coerceAtMost(endEpochMillis)

            // Query one local day at a time. A single 52-week INTERVAL_DAILY
            // request can be collapsed by Android into a small number of
            // aggregate records; assigning those records by lastTimeUsed
            // incorrectly puts the whole history on one day.
            val dailyStats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                dayStart,
                dayEnd
            )
            val screenTimeByPackage = dailyStats
                .asSequence()
                .filter { it.totalTimeInForeground > 0L }
                .groupBy { it.packageName }
                .mapValues { (_, packageStats) ->
                    packageStats.sumOf { it.totalTimeInForeground } / 1_000L
                }
                .filterValues { it > 0L }

            if (screenTimeByPackage.isNotEmpty()) {
                val existingByPackage = usageDao.getForDay(dayStart).associateBy { it.packageName }
                val records = screenTimeByPackage.map { (packageName, screenTimeSeconds) ->
                    val existing = existingByPackage[packageName]
                    NativeAppUsageEntity(
                        packageName = packageName,
                        dateEpochMillis = dayStart,
                        screenTime = screenTimeSeconds,
                        mobileData = existing?.mobileData ?: 0L,
                        wifiData = existing?.wifiData ?: 0L
                    )
                }
                usageDao.insertAll(records)
                rowsWritten += records.size
            }
            dayCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        NativeUsageRefreshResult.Updated(rowsWritten)
    }


    /**
     * Backfills one historical day from Android's persisted daily UsageStats.
     * Existing mobile and Wi-Fi values are preserved.
     */
    suspend fun refreshHistoricalScreenTimeDay(
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long
    ): NativeUsageRefreshResult = withContext(Dispatchers.IO) {
        require(dayStartEpochMillis >= 0L) { "Day start must be a valid epoch timestamp." }
        require(dayEndEpochMillis > dayStartEpochMillis) { "Usage interval must have a positive duration." }
        if (!hasUsageAccess()) return@withContext NativeUsageRefreshResult.UsageAccessRequired

        val manager = appContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val screenUsage = ScreenUsageHelper.fetchAggregatedUsageForInterval(
            manager,
            dayStartEpochMillis,
            dayEndEpochMillis,
            strict = true
        )

        // Empty can mean no recorded use or that Android no longer retains the day.
        // Do not erase rows already collected by the app in either case.
        if (screenUsage.isEmpty()) {
            return@withContext NativeUsageRefreshResult.Updated(0)
        }

        val existing = usageDao.getForDay(dayStartEpochMillis)
        val existingByPackage = existing.associateBy { it.packageName }
        val usageRows = screenUsage.map { (packageName, seconds) ->
            val previous = existingByPackage[packageName]
            NativeAppUsageEntity(
                packageName = packageName,
                dateEpochMillis = dayStartEpochMillis,
                screenTime = seconds,
                mobileData = previous?.mobileData ?: 0L,
                wifiData = previous?.wifiData ?: 0L
            )
        }
        val preservedRows = existing
            .filter { it.packageName !in screenUsage }
            .map { it.copy(screenTime = 0L) }

        val rows = usageRows + preservedRows
        usageDao.replaceDay(dayStartEpochMillis, rows)
        NativeUsageRefreshResult.Updated(usageRows.size)
    }

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

internal fun buildNativeUsageRows(
    dayStartEpochMillis: Long,
    launchableApps: List<NativeLaunchableApp>,
    screenUsageSeconds: Map<String, Long>,
    mobileUsageKb: Map<Int, Long>,
    wifiUsageKb: Map<Int, Long>
): List<NativeAppUsageEntity> {
    val appRows = launchableApps.distinctBy { it.packageName }.mapNotNull { app ->
        val screenTime = screenUsageSeconds[app.packageName] ?: 0L
        val mobileData = mobileUsageKb[app.uid] ?: 0L
        val wifiData = wifiUsageKb[app.uid] ?: 0L
        if (screenTime <= 0L && mobileData <= 0L && wifiData <= 0L) {
            null
        } else {
            NativeAppUsageEntity(
                packageName = app.packageName,
                dateEpochMillis = dayStartEpochMillis,
                screenTime = screenTime.coerceAtLeast(0L),
                mobileData = mobileData.coerceAtLeast(0L),
                wifiData = wifiData.coerceAtLeast(0L)
            )
        }
    }

    // Keep the two synthetic network buckets used by the original Mindful UI.
    val tethering = NativeAppUsageEntity(
        packageName = AppConstants.TETHERING_PACKAGE,
        dateEpochMillis = dayStartEpochMillis,
        mobileData = (mobileUsageKb[NetworkStats.Bucket.UID_TETHERING] ?: 0L).coerceAtLeast(0L),
        wifiData = (wifiUsageKb[NetworkStats.Bucket.UID_TETHERING] ?: 0L).coerceAtLeast(0L)
    )
    val removed = NativeAppUsageEntity(
        packageName = AppConstants.REMOVED_PACKAGE,
        dateEpochMillis = dayStartEpochMillis,
        mobileData = (mobileUsageKb[NetworkStats.Bucket.UID_REMOVED] ?: 0L).coerceAtLeast(0L),
        wifiData = (wifiUsageKb[NetworkStats.Bucket.UID_REMOVED] ?: 0L).coerceAtLeast(0L)
    )

    return appRows + tethering + removed
}
