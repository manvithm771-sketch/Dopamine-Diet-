package com.mindful.android.services.tracking

import com.mindful.android.enums.RestrictionType
import com.mindful.android.models.AppRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestrictionManagerTest {
    private fun manager(): RestrictionManager =
        RestrictionManager(RuntimeEnvironment.getApplication(), {})

    @Test
    fun launchLimitBlocksOnlyAfterConfiguredNumberOfLaunches() {
        val tracker = manager()
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 2
            )),
            hashMapOf()
        )
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertEquals(
            RestrictionType.LAUNCH_COUNT,
            tracker.isAppRestricted("com.example.video")?.type
        )
    }

    @Test
    fun resetCacheClearsLaunchCountAndRestrictionCache() {
        val tracker = manager()
        tracker.updateRestrictions(
            hashMapOf("com.example.video" to AppRestriction(
                appPackage = "com.example.video",
                launchLimit = 1
            )),
            hashMapOf()
        )
        assertNull(tracker.isAppRestricted("com.example.video"))
        assertEquals(
            RestrictionType.LAUNCH_COUNT,
            tracker.isAppRestricted("com.example.video")?.type
        )
        tracker.resetCache()
        assertNull(tracker.isAppRestricted("com.example.video"))
    }
}
