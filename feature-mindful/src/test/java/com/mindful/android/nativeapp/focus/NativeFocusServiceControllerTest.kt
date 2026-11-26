package com.mindful.android.nativeapp.focus

import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeFocusServiceControllerTest {
    @Test
    fun focusPackagesExcludeUninstalledAndProtectedSystemApps() {
        assertEquals(
            setOf("com.example.social"),
            filterNativeFocusPackages(
                packages = setOf("com.example.social", "com.example.launcher", "com.example.removed"),
                installedPackageNames = setOf("com.example.social", "com.example.launcher"),
                protectedPackageNames = setOf("com.example.launcher")
            )
        )
    }

    @Test
    fun rejectsProfileWithoutDistractingAppsBeforeCheckingPermissions() {
        val controller = NativeFocusServiceController(RuntimeEnvironment.getApplication())
        val profile = NativeFocusProfileEntity(
            sessionType = 0L,
            distractingAppsJson = "[]"
        )

        assertEquals(
            NativeFocusServiceStartResult.NO_DISTRACTING_APPS,
            controller.validate(profile)
        )
    }

    @Test
    fun rejectsProfileWhenRequiredRuntimePermissionsAreMissing() {
        val controller = NativeFocusServiceController(
            context = RuntimeEnvironment.getApplication(),
            installedPackageNames = { setOf("com.example.social") },
            protectedPackageNames = { emptySet() }
        )
        val profile = NativeFocusProfileEntity(
            sessionType = 0L,
            distractingAppsJson = """["com.example.social"]"""
        )

        assertEquals(
            NativeFocusServiceStartResult.REQUIRED_PERMISSIONS_MISSING,
            controller.validate(profile)
        )
    }
}
