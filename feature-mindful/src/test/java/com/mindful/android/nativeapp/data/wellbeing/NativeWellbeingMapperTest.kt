package com.mindful.android.nativeapp.data.wellbeing

import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeWellbeingMapperTest {
    @Test fun mapsWebsiteListsAndNsfwFlag() {
        val result = toNativeWellbeingModel(
            NativeWellbeingEntity(
                blockNsfwSites = true,
                blockedWebsitesJson = """["Example.com","blocked.test"]""",
                nsfwWebsitesJson = """["adult.test"]"""
            )
        )
        assertTrue(result.blockNsfwSites)
        assertEquals(setOf("example.com", "blocked.test"), result.blockedWebsites)
        assertEquals(setOf("adult.test"), result.nsfwWebsites)
    }

    @Test fun mapsShortsAllowanceAndKnownBlockedFeatures() {
        val result = toNativeWellbeingModel(
            NativeWellbeingEntity(
                allowedShortsTimeSec = 900L,
                blockedFeaturesJson = """["instagramReels","youtubeShorts","unknownFeature"]"""
            )
        )
        assertEquals(900_000L, result.allowedShortsTimeMs)
        assertEquals(
            setOf(
                com.mindful.android.enums.PlatformFeatures.INSTAGRAM_REELS,
                com.mindful.android.enums.PlatformFeatures.YOUTUBE_SHORTS
            ),
            result.blockedFeatures
        )
    }

    @Test fun invalidJsonFallsBackToEmptyWebsiteSets() {
        val result = toNativeWellbeingModel(
            NativeWellbeingEntity(
                blockedWebsitesJson = "invalid",
                nsfwWebsitesJson = "invalid"
            )
        )
        assertFalse(result.blockNsfwSites)
        assertTrue(result.blockedWebsites.isEmpty())
        assertTrue(result.nsfwWebsites.isEmpty())
    }

    @Test fun missingNativeWellbeingUsesSafeDefaults() {
        val result = toNativeWellbeingModel(null)
        assertFalse(result.blockNsfwSites)
        assertTrue(result.blockedWebsites.isEmpty())
        assertTrue(result.nsfwWebsites.isEmpty())
    }
}
