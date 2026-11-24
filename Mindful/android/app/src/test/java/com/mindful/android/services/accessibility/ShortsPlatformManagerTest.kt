package com.mindful.android.services.accessibility

import com.mindful.android.enums.PlatformFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsPlatformManagerTest {
    @Test
    fun selectedPlatformUrlsAreRecognized() {
        assertTrue(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://www.youtube.com/shorts/abc123",
                setOf(PlatformFeatures.YOUTUBE_SHORTS)
            )
        )
        assertTrue(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://www.instagram.com/reels/abc123/",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
        // Instagram's canonical Reel permalink uses the singular /reel/ path.
        assertTrue(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://www.instagram.com/reel/abc123/",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
        assertTrue(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://m.facebook.com/reel/12345",
                setOf(PlatformFeatures.FACEBOOK_REELS)
            )
        )
    }

    @Test
    fun supportedSubdomainsAreRecognizedButLookalikeHostsAreRejected() {
        assertTrue(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://m.instagram.com/reels/abc123/",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
        assertFalse(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://evilinstagram.com/reels/abc123/",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
        assertFalse(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://instagram.com.evil.test/reels/abc123/",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
    }

    @Test
    fun unselectedOrOrdinaryUrlsAreNotRecognized() {
        assertFalse(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://www.youtube.com/shorts/abc123",
                setOf(PlatformFeatures.INSTAGRAM_REELS)
            )
        )
        assertFalse(
            ShortsPlatformManager.isBlockedShortsUrl(
                "https://www.youtube.com/watch?v=abc123",
                setOf(PlatformFeatures.YOUTUBE_SHORTS)
            )
        )
    }
}
