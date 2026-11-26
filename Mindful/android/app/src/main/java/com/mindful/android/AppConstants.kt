package com.mindful.android

/**
 * Constants still used by the retained native tracking and focus features.
 * Obsolete Flutter channel and removed emergency-service constants are
 * intentionally not restored.
 */
object AppConstants {
    const val REMOVED_APP_NAME: String = "Removed Apps"
    const val REMOVED_PACKAGE: String = "com.android.removed"
    const val TETHERING_APP_NAME: String = "Tethering & Hotspot"
    const val TETHERING_PACKAGE: String = "com.android.tethering"

    const val INTENT_EXTRA_IS_SELF_RESTART: String =
        "com.mindful.android.intent.extra.isSelfRestart"

    const val TRACKER_SERVICE_NOTIFICATION_ID: Int = 101
    const val FOCUS_SESSION_SERVICE_NOTIFICATION_ID: Int = 104

    const val SETTINGS_PACKAGE: String = "com.android.settings"
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    const val INSTAGRAM_PACKAGE: String = "com.instagram.android"
    const val YOUTUBE_PACKAGE: String = "com.google.android.youtube"
    const val YOUTUBE_CLIENT_PACKAGE_SUFFIX: String = ".android.youtube"
    const val SNAPCHAT_PACKAGE: String = "com.snapchat.android"
    const val FACEBOOK_PACKAGE: String = "com.facebook.katana"
    const val REDDIT_PACKAGE: String = "com.reddit.frontpage"

    const val ONE_DAY_IN_MS: Long = 24L * 60L * 60L * 1000L
}
