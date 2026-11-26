package com.mindful.android.enums

/** Short-form content surfaces that can be monitored by the accessibility service. */
enum class PlatformFeatures(val jsonName: String) {
    INSTAGRAM_REELS("instagramReels"),
    INSTAGRAM_EXPLORE("instagramExplore"),
    SNAPCHAT_SPOTLIGHT("snapchatSpotlight"),
    SNAPCHAT_DISCOVER("snapchatDiscover"),
    FACEBOOK_REELS("facebookReels"),
    REDDIT_SHORTS("redditShorts"),
    YOUTUBE_SHORTS("youtubeShorts");

    companion object {
        fun fromName(name: String): PlatformFeatures? =
            values().firstOrNull { it.jsonName == name }
    }
}
