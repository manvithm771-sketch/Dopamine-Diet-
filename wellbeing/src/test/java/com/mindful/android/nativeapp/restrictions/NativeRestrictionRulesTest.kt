package com.mindful.android.nativeapp.restrictions

import com.mindful.android.enums.ReminderType
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeRestrictionRulesTest {
    @Test fun mapsNativeRowsToExistingAndroidRestrictionModels() {
        val rules = buildNativeRestrictionRules(
            listOf(NativeAppRestrictionEntity(
                appPackage = "com.example.social",
                timerSec = 3600L,
                launchLimit = 5L,
                associatedGroupId = 7L,
                reminderType = "toast"
            )),
            listOf(NativeRestrictionGroupEntity(
                id = 7L,
                groupName = "Social apps",
                timerSec = 5400L,
                distractingAppsJson = """["com.example.social","com.example.video"]"""
            ))
        )
        val app = rules.apps.getValue("com.example.social")
        assertEquals(3600, app.timerSec)
        assertEquals(5, app.launchLimit)
        assertEquals(7, app.associatedGroupId)
        assertEquals(ReminderType.TOAST, app.reminderType)
        val group = rules.groups.getValue(7)
        assertEquals("Social apps", group.groupName)
        assertEquals(5400, group.timerSec)
        assertTrue(group.distractingApps.contains("com.example.video"))
    }

    @Test fun installedAppAndActiveGroupFilteringMatchesFlutterTrackerRules() {
        val filtered = filterNativeRestrictionInputs(
            appRows = listOf(
                NativeAppRestrictionEntity("com.example.active", timerSec = 600L),
                NativeAppRestrictionEntity("com.example.grouped", associatedGroupId = 4L),
                NativeAppRestrictionEntity("com.example.disabled"),
                NativeAppRestrictionEntity("com.example.uninstalled", timerSec = 900L)
            ),
            groupRows = listOf(
                NativeRestrictionGroupEntity(
                    id = 4L,
                    timerSec = 1200L,
                    distractingAppsJson = """["com.example.grouped","com.example.uninstalled"]"""
                ),
                NativeRestrictionGroupEntity(
                    id = 5L,
                    timerSec = 0L,
                    distractingAppsJson = """["com.example.grouped"]"""
                ),
                NativeRestrictionGroupEntity(
                    id = 6L,
                    timerSec = 1200L,
                    distractingAppsJson = """["com.example.uninstalled"]"""
                )
            ),
            installedPackageNames = setOf("com.example.active", "com.example.grouped")
        )

        assertEquals(
            setOf("com.example.active", "com.example.grouped"),
            filtered.apps.map { it.appPackage }.toSet()
        )
        assertEquals(listOf(4L), filtered.groups.map { it.id })
    }

    @Test fun protectedSystemAppsAreRemovedFromAppAndGroupEnforcementInputs() {
        val filtered = filterNativeRestrictionInputs(
            appRows = listOf(
                NativeAppRestrictionEntity("com.example.safe", timerSec = 600L),
                NativeAppRestrictionEntity("com.example.launcher", timerSec = 900L),
                NativeAppRestrictionEntity("com.example.dialer", associatedGroupId = 2L)
            ),
            groupRows = listOf(
                NativeRestrictionGroupEntity(
                    id = 2L,
                    timerSec = 1200L,
                    distractingAppsJson = """["com.example.safe","com.example.launcher","com.example.dialer"]"""
                )
            ),
            installedPackageNames = setOf(
                "com.example.safe", "com.example.launcher", "com.example.dialer"
            ),
            protectedPackageNames = setOf("com.example.launcher", "com.example.dialer")
        )

        assertEquals(listOf("com.example.safe"), filtered.apps.map { it.appPackage })
        val groupPackages = org.json.JSONArray(filtered.groups.single().distractingAppsJson)
        assertEquals(1, groupPackages.length())
        assertEquals("com.example.safe", groupPackages.getString(0))
    }

    @Test fun emptyNativeRowsProduceEmptyRulesForClearingTheTracker() {
        val rules = buildNativeRestrictionRules(emptyList(), emptyList())
        assertTrue(rules.apps.isEmpty())
        assertTrue(rules.groups.isEmpty())
    }

    @Test fun malformedGroupJsonAndUnknownReminderAreHandledSafely() {
        val rules = buildNativeRestrictionRules(
            listOf(NativeAppRestrictionEntity("com.example.app", reminderType = "unknown")),
            listOf(NativeRestrictionGroupEntity(id = 1L, distractingAppsJson = "not-json"))
        )
        assertEquals(ReminderType.NONE, rules.apps.getValue("com.example.app").reminderType)
        assertTrue(rules.groups.getValue(1).distractingApps.isEmpty())
    }
}
