package com.mindful.android.helpers.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.mindful.android.AppConstants
import com.mindful.android.AppConstants.REMOVED_APP_NAME
import com.mindful.android.AppConstants.REMOVED_PACKAGE
import com.mindful.android.AppConstants.TETHERING_APP_NAME
import com.mindful.android.AppConstants.TETHERING_PACKAGE
import com.mindful.android.utils.AppUtils

object DeviceAppsHelper {

    /**
     * Retrieves launchable apps that are eligible for app blocking.
     * Core phone utilities and the other protected categories are omitted.
     */
    fun getDeviceAppInfos(
        context: Context,
        onSuccess: (data: Any) -> Unit,
    ) {
        Thread {
            val packageManager = context.packageManager

            // Exclude the app itself, Settings, default launcher/dialer, and all
            // installed apps matching the eleven ineligible app categories.
            val ineligiblePackages = ImpSystemAppsHelper.fetchImpApps(packageManager).apply {
                add(context.packageName)
                add(AppConstants.SETTINGS_PACKAGE)
            }

            val launchableApps = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            )

            val deviceAppsMapList: MutableList<Map<String, Any>> = launchableApps
                .filterNot { appInfo ->
                    appInfo.activityInfo.packageName in ineligiblePackages ||
                        ImpSystemAppsHelper.isIneligibleForBlocking(
                            appInfo.loadLabel(packageManager).toString(),
                            appInfo.activityInfo.packageName
                        )
                }
                .distinctBy { it.activityInfo.packageName }
                .map { appInfo ->
                    getAppInfoMap(
                        name = appInfo.loadLabel(packageManager).toString(),
                        packageName = appInfo.activityInfo.packageName,
                        isImpSysApp = false,
                        appIcon = AppUtils.getEncodedAppIcon(
                            packageManager.getApplicationIcon(appInfo.activityInfo.applicationInfo)
                        ),
                    )
                }.toMutableList()

            // Get placeholder icon
            val placeholderIcon = AppUtils.getEncodedAppIcon(
                packageManager.getApplicationIcon(ApplicationInfo())
            )

            // Additional pseudo-app entries used for network usage
            deviceAppsMapList.add(
                getAppInfoMap(
                    name = TETHERING_APP_NAME,
                    packageName = TETHERING_PACKAGE,
                    isImpSysApp = true,
                    appIcon = placeholderIcon,
                )
            )

            deviceAppsMapList.add(
                getAppInfoMap(
                    name = REMOVED_APP_NAME,
                    packageName = REMOVED_PACKAGE,
                    isImpSysApp = true,
                    appIcon = placeholderIcon,
                )
            )

            onSuccess(deviceAppsMapList)
        }.start()
    }

    private fun getAppInfoMap(
        name: String,
        packageName: String,
        isImpSysApp: Boolean,
        appIcon: String,
    ): Map<String, Any> = mapOf(
        "appName" to name,
        "packageName" to packageName,
        "isImpSysApp" to isImpSysApp,
        "appIcon" to appIcon,
    )
}
