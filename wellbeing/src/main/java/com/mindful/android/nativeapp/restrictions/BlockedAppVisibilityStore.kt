package com.mindful.android.nativeapp.restrictions

import android.content.Context

/**
 * Shared visibility state consumed by Dopamine Diet's optional consumer launcher.
 * This does not disable packages; it removes blocked packages from the app list
 * presented by Dopamine Diet when the user selects it as their Home app.
 */
object BlockedAppVisibilityStore {
    private const val PREFERENCES_NAME = "dopamine_blocked_app_visibility"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"

    fun markBlocked(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val updated = blockedPackages(context).toMutableSet().apply { add(packageName) }
        preferences(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
    }

    fun markUnblocked(context: Context, packageName: String) {
        val updated = blockedPackages(context).toMutableSet().apply { remove(packageName) }
        preferences(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
    }

    fun blockedPackages(context: Context): Set<String> =
        preferences(context).getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun clear(context: Context) {
        preferences(context).edit().remove(KEY_BLOCKED_PACKAGES).apply()
    }

    fun reconcileConfiguredPackages(context: Context, configuredPackages: Set<String>) {
        val updated = blockedPackages(context).intersect(configuredPackages)
        preferences(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
