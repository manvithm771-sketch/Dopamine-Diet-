package com.mindful.android.services.tracking

import android.app.usage.UsageStatsManager
import android.content.Context
import com.mindful.android.enums.ReminderType
import com.mindful.android.enums.RestrictionType
import com.mindful.android.models.AppRestriction
import com.mindful.android.models.RestrictionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestrictionManagerTest {
    @Before
    fun clearPersistedLaunchCounts() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("app_blocking_launch_counts", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun manager(usage: () -> Map<String, Long>): RestrictionManager {
        val context = RuntimeEnvironment.getApplication()
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return RestrictionManager(context, {}, usageStats, usage)
    }

    @Test
    fun appTimerBlocksAtLimit() {
        val tracker = manager { mapOf("com.example.video" to 600L) }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                timerSec = 600,
                reminderType = ReminderType.TOAST
            )),
            hashMapOf()
        )
        val state = tracker.isAppRestricted("com.example.video")
        assertNotNull(state)
        assertEquals(RestrictionType.APP_TIMER, state?.type)
        assertEquals(600L, state?.screenTimeUsed)
        assertEquals(600L, state?.screenTimeLimit)
    }

    @Test
    fun appTimerReturnsRemainingTimeBelowLimit() {
        val tracker = manager { mapOf("com.example.video" to 240L) }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                timerSec = 600
            )),
            hashMapOf()
        )
        assertEquals(360_000L, tracker.isAppRestricted("com.example.video")?.timeLeftMillis)
    }

    @Test
    fun launchLimitBlocksOnlyAfterConfiguredNumberOfLaunches() {
        val tracker = manager { emptyMap() }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 2
            )),
            hashMapOf()
        )
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertEquals(RestrictionType.LAUNCH_COUNT, tracker.isAppRestricted("com.example.video")?.type)
        assertEquals(RestrictionType.LAUNCH_COUNT, tracker.isAppRestricted("com.example.video")?.type)
    }

    @Test
    fun recheckingCurrentAppDoesNotConsumeAnotherLaunch() {
        val tracker = manager { emptyMap() }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 1
            )),
            hashMapOf()
        )

        assertNull(tracker.isAppRestricted("com.example.video"))
        assertEquals(1, tracker.getAppsLaunchCount["com.example.video"])

        assertNull(tracker.recheckAppRestriction("com.example.video"))
        assertEquals(1, tracker.getAppsLaunchCount["com.example.video"])

        assertEquals(
            RestrictionType.LAUNCH_COUNT,
            tracker.isAppRestricted("com.example.video")?.type
        )
    }

    @Test
    fun dailyLaunchCountsSurviveTrackerRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val firstTracker = RestrictionManager(context, {}, usageStats) { emptyMap() }
        firstTracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 2
            )),
            hashMapOf()
        )
        assertNull(firstTracker.isAppRestricted("com.example.video"))
        assertEquals(1, firstTracker.getAppsLaunchCount["com.example.video"])

        val recreatedTracker = RestrictionManager(context, {}, usageStats) { emptyMap() }
        recreatedTracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 2
            )),
            hashMapOf()
        )
        assertEquals(1, recreatedTracker.getAppsLaunchCount["com.example.video"])
        assertNull(recreatedTracker.isAppRestricted("com.example.video"))
        assertEquals(2, recreatedTracker.getAppsLaunchCount["com.example.video"])
        assertEquals(
            RestrictionType.LAUNCH_COUNT,
            recreatedTracker.isAppRestricted("com.example.video")?.type
        )
    }

    @Test
    fun groupTimerSumsUsageAcrossAllMembers() {
        val tracker = manager {
            mapOf("com.example.video" to 300L, "com.example.social" to 250L)
        }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                associatedGroupId = 9
            )),
            hashMapOf(9 to RestrictionGroup(
                id = 9,
                groupName = "Social",
                timerSec = 500,
                distractingApps = setOf("com.example.video", "com.example.social")
            ))
        )
        val state = tracker.isAppRestricted("com.example.video")
        assertNotNull(state)
        assertEquals(RestrictionType.GROUP_TIMER, state?.type)
        assertEquals("Social", state?.groupName)
        assertEquals(550L, state?.screenTimeUsed)
    }

    @Test
    fun midnightResetClearsLaunchCountAndCachedRestriction() {
        val tracker = manager { emptyMap() }
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 1
            )),
            hashMapOf()
        )
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertEquals(RestrictionType.LAUNCH_COUNT, tracker.isAppRestricted("com.example.video")?.type)
        tracker.resetCache()
        assertNull(tracker.isAppRestricted("com.example.video"))
    }
}
