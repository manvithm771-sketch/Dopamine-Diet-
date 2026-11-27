package com.mindful.android.nativeapp.restrictions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.mindful.android.admin.DopamineDeviceAdminReceiver

/**
 * Optional device-owner integration for hiding blocked apps from Android launchers.
 *
 * This controller never attempts to provision device-owner access. The device must
 * already have been explicitly provisioned by its owner.
 */
internal class ManagedAppSuspensionController(context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(appContext, DopamineDeviceAdminReceiver::class.java)
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val isDeviceOwner: Boolean
        get() = devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    /**
     * Hides one app only when Dopamine Diet is the provisioned device owner.
     * Returns true only when Android confirms that the package was hidden.
     */
    fun hidePackage(packageName: String): Boolean {
        if (!isEligiblePackage(packageName) || !isDeviceOwner) return false
        return runCatching {
            val hidden = devicePolicyManager.setApplicationHidden(
                adminComponent,
                packageName,
                true
            )
            if (hidden) {
                rememberHidden(packageName)
                true
            } else {
                Log.w(TAG, "Android refused to hide $packageName")
                false
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not hide $packageName", error)
        }.getOrDefault(false)
    }

    /** Makes a package hidden by this controller available again. */
    fun restorePackage(packageName: String): Boolean {
        if (!isDeviceOwner) return false
        val tracked = trackedPackages()
        if (packageName !in tracked) return true
        return runCatching {
            val restored = devicePolicyManager.setApplicationHidden(
                adminComponent,
                packageName,
                false
            )
            if (restored) {
                rememberHidden(packageName, hidden = false)
                true
            } else {
                Log.w(TAG, "Android refused to restore $packageName")
                false
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not restore $packageName", error)
        }.getOrDefault(false)
    }

    /**
     * Restore apps whose rules have been removed. Still-configured apps remain
     * hidden until their restriction expires or the user removes the rule.
     */
    fun reconcileConfiguredPackages(configuredPackages: Set<String>) {
        if (!isDeviceOwner) return
        trackedPackages()
            .filterNot { it in configuredPackages }
            .forEach(::restorePackage)
    }

    /** Daily restriction reset makes previously blocked apps eligible again. */
    fun restoreAll() {
        if (!isDeviceOwner) return
        trackedPackages().forEach(::restorePackage)
    }

    private fun trackedPackages(): Set<String> =
        preferences.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())
            ?.toSet()
            ?: emptySet()

    private fun rememberHidden(packageName: String, hidden: Boolean = true) {
        val updated = trackedPackages().toMutableSet()
        if (hidden) updated.add(packageName) else updated.remove(packageName)
        preferences.edit().putStringSet(KEY_SUSPENDED_PACKAGES, updated).apply()
    }

    private fun isEligiblePackage(packageName: String): Boolean =
        packageName.isNotBlank() &&
            packageName != appContext.packageName &&
            packageName !in protectedSystemPackageNames(appContext)

    private companion object {
        const val TAG = "DopamineAppHiding"
        const val PREFERENCES_NAME = "managed_app_hiding"
        const val KEY_SUSPENDED_PACKAGES = "suspended_by_dopamine_diet"
    }
}
