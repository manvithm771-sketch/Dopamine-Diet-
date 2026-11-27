/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  * This source code is licensed under the GPL-2.0 license.
 *
 */
package com.mindful.android.helpers.device

import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Identifies device apps that must never be offered as blockable distractions.
 * App labels and package names are both checked because OEM package names differ.
 */
object ImpSystemAppsHelper {
    private val ineligibleAppIdentifiers = setOf(
        "calendar",
        "calculator",
        "clock",
        "camera",
        "file",
        "files",
        "filemanager",
        "contacts",
        "contact",
        "phone",
        "dialer",
        "message",
        "messages",
        "messaging",
        "settings",
        "truecaller",
        "voicerecorder",
        "soundrecorder",
        "recorder"
    )

    /**
     * Returns true for the eleven requested app categories. Matching both the
     * visible label and package name supports different Android manufacturers.
     */
    fun isIneligibleForBlocking(appName: String, packageName: String): Boolean {
        val normalizedName = normalizeIdentifier(appName)
        val normalizedPackage = normalizeIdentifier(packageName)
        return ineligibleAppIdentifiers.any { identifier ->
            normalizedName.contains(identifier) || normalizedPackage.contains(identifier)
        }
    }

    /**
     * Fetches protected apps: default launcher/dialer, the app itself, Settings,
     * and installed apps in the ineligible categories.
     */
    @Suppress("DEPRECATION")
    fun fetchImpApps(packageManager: PackageManager): HashSet<String> {
        return HashSet<String>(16).apply {
            getDefaultLauncherPackageName(packageManager)?.let { add(it) }
            getDefaultDialerPackageName(packageManager)?.let { add(it) }

            packageManager.getInstalledApplications(PackageManager.MATCH_ALL).forEach { app ->
                val label = runCatching {
                    packageManager.getApplicationLabel(app).toString()
                }.getOrDefault("")
                if (isIneligibleForBlocking(label, app.packageName)) {
                    add(app.packageName)
                }
            }
        }
    }

    private fun normalizeIdentifier(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun getDefaultLauncherPackageName(packageManager: PackageManager): String? =
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

    private fun getDefaultDialerPackageName(packageManager: PackageManager): String? =
        packageManager.resolveActivity(
            Intent(Intent.ACTION_DIAL).addCategory(Intent.CATEGORY_DEFAULT),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
}
