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
        "myfiles",
        "documentsui",
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
        "voicenote",
        "soundrecorder",
        "recorder",
        "weather",
        // Google productivity and developer tools requested as non-blockable.
        "drive",
        "sheets",
        "spreadsheet",
        "playconsole",
        "vending"
    )

    private val ineligibleAppNamePhrases = setOf(
        "google drive",
        "google sheets",
        "play console",
        "google play console",
        "google play store",
        "play store"
    )

    private val ineligiblePackageNames = setOf(
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.playconsole",
        "com.android.vending"
    )

    /**
     * Returns true for the eleven requested app categories. Matching both the
     * visible label and package name supports different Android manufacturers.
     */
    fun isIneligibleForBlocking(appName: String, packageName: String): Boolean {
        val normalizedName = normalizePhrase(appName)
        val nameTokens = tokenize(appName)
        val packageTokens = tokenize(packageName)
        val normalizedPackage = packageName.lowercase(Locale.ROOT)

        return normalizedPackage in ineligiblePackageNames ||
            ineligibleAppNamePhrases.any { phrase ->
                normalizedName == phrase || normalizedName.contains(phrase)
            } ||
            ineligibleAppIdentifiers.any { identifier ->
                identifier in nameTokens || identifier in packageTokens
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

    private fun tokenize(value: String): Set<String> =
        value.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()

    private fun normalizePhrase(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

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
