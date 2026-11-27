package com.mindful.android.nativeapp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOnboardingScreenTest {
    @Test
    fun essentialPermissionsAreRequired() {
        assertFalse(isNativeOnboardingReady(NativePermissionStatus(usageAccess = false, overlayAccess = true, exactAlarms = true), 35))
        assertFalse(isNativeOnboardingReady(NativePermissionStatus(usageAccess = true, overlayAccess = false, exactAlarms = true), 35))
        assertFalse(isNativeOnboardingReady(NativePermissionStatus(usageAccess = true, overlayAccess = true, exactAlarms = false), 35))
        assertTrue(isNativeOnboardingReady(NativePermissionStatus(usageAccess = true, overlayAccess = true, exactAlarms = true), 35))
    }

    @Test
    fun defaultHomeRoleIsRequiredForAppIconHidingSetup() {
        assertFalse(
            isNativeOnboardingReady(
                NativePermissionStatus(usageAccess = true, overlayAccess = true, exactAlarms = true),
                35
            )
        )
        assertTrue(
            isNativeOnboardingReady(
                NativePermissionStatus(usageAccess = true, overlayAccess = true, exactAlarms = true),
                35
            )
        )
    }

    @Test
    fun exactAlarmPermissionIsNotRequiredBeforeAndroid12() {
        assertTrue(isNativeOnboardingReady(NativePermissionStatus(usageAccess = true, overlayAccess = true, exactAlarms = false), 30))
    }
}
