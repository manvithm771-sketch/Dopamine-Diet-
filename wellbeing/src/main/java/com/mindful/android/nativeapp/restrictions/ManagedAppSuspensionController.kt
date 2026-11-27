package com.mindful.android.nativeapp.restrictions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    /**
     * Suspends one app only when Dopamine Diet is the provisioned device owner.
     * Returns true only when Android confirms that the package was suspended.
     */
    fun suspendPackage(packageName: String): Boolean {
        if (!isEligiblePackage(packageName) || !isDeviceOwner) return false
        return runCatching {
            val failed = devicePolicyManager.setPackagesSuspended(
                adminComponent,
                arrayOf(packageName),
                true
            )
            if (failed.isEmpty()) {
                rememberSuspended(packageName)
                true
            } else {
                Log.w(TAG, "Android refused to suspend $packageName: $failed")
                false
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not suspend $packageName", error)
        }.getOrDefault(false)
    }

    /** Restores a package previously suspended by this controller. */
    fun unsuspendPackage(packageName: String): Boolean {
        if (!isDeviceOwner) return false
        val tracked = trackedPackages()
        if (packageName !in tracked) return true
        return runCatching {
            val failed = devicePolicyManager.setPackagesSuspended(
                adminComponent,
                arrayOf(packageName),
                false
            )
            if (failed.isEmpty()) {
                rememberSuspended(packageName, suspended = false)
                true
            } else {
                Log.w(TAG, "Android refused to unsuspend $packageName: $failed")
                false
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not restore $packageName", error)
        }.getOrDefault(false)
    }

    /**
     * Restore apps whose rules have been removed. Still-configured apps remain
     * suspended until their restriction expires or the user removes the rule.
     */
    fun reconcileConfiguredPackages(configuredPackages: Set<String>) {
        if (!isDeviceOwner) return
        trackedPackages()
            .filterNot { it in configuredPackages }
            .forEach(::unsuspendPackage)
    }

    /** Daily restriction reset makes previously blocked apps eligible again. */
    fun restoreAll() {
        if (!isDeviceOwner) return
        trackedPackages().forEach(::unsuspendPackage)
    }

    private fun trackedPackages(): Set<String> =
        preferences.getStringSet(KEY_SUSPENDED_PACKAGES, emptySet())
            ?.toSet()
            ?: emptySet()

    private fun rememberSuspended(packageName: String, suspended: Boolean = true) {
        val updated = trackedPackages().toMutableSet()
        if (suspended) updated.add(packageName) else updated.remove(packageName)
        preferences.edit().putStringSet(KEY_SUSPENDED_PACKAGES, updated).apply()
    }

    private fun isEligiblePackage(packageName: String): Boolean =
        packageName.isNotBlank() &&
            packageName != appContext.packageName &&
            packageName !in protectedSystemPackageNames(appContext)

    private companion object {
        const val TAG = "DopamineAppSuspension"
        const val PREFERENCES_NAME = "managed_app_suspension"
        const val KEY_SUSPENDED_PACKAGES = "suspended_by_dopamine_diet"
    }
}
