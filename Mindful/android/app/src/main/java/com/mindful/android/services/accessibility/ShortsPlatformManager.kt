package com.mindful.android.services.accessibility

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mindful.android.AppConstants.FACEBOOK_PACKAGE
import com.mindful.android.AppConstants.INSTAGRAM_PACKAGE
import com.mindful.android.AppConstants.REDDIT_PACKAGE
import com.mindful.android.AppConstants.SNAPCHAT_PACKAGE
import com.mindful.android.AppConstants.YOUTUBE_CLIENT_PACKAGE_SUFFIX
import com.mindful.android.AppConstants.YOUTUBE_PACKAGE
import com.mindful.android.enums.PlatformFeatures
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.models.Wellbeing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Counts time spent on the selected short-form surfaces across supported social apps and browsers.
 * Detection markers and timing thresholds preserve the original Mindful behavior.
 */
class ShortsPlatformManager(
    private val context: Context,
    private val blockedContentGoBack: () -> Unit,
) {
    private var lastTimeShortsEvent = 0L
    private var lastTimeSaved = 0L
    private var shortContentScreenTime = SharedPrefsHelper.getSetShortsScreenTimeMs(context, null)
    private var currentDayKey = SharedPrefsHelper.getSetShortsScreenTimeDay(context, null)

    init {
        val today = currentLocalDayKey()
        if (currentDayKey == null) {
            // Preserve the legacy counter on first upgrade; the old app reset it at midnight.
            currentDayKey = today
            SharedPrefsHelper.getSetShortsScreenTimeDay(context, today)
        } else if (currentDayKey != today) {
            resetForNewDay(today)
        }
    }

    /**
     * Persists the latest accumulated usage when the accessibility service is shutting down.
     * Normal updates are throttled; without this final flush, the last partial interval could
     * be lost whenever Android disconnects or restarts the service.
     */
    @Synchronized
    fun flushShortsScreenTime() {
        SharedPrefsHelper.getSetShortsScreenTimeMs(context, shortContentScreenTime)
        currentDayKey?.let { SharedPrefsHelper.getSetShortsScreenTimeDay(context, it) }
        lastTimeSaved = System.currentTimeMillis()
    }

    @Synchronized
    fun resetShortsScreenTime() {
        shortContentScreenTime = 0L
        lastTimeShortsEvent = 0L
        lastTimeSaved = 0L
        currentDayKey = currentLocalDayKey()
        SharedPrefsHelper.getSetShortsScreenTimeMs(context, 0L)
        SharedPrefsHelper.getSetShortsScreenTimeDay(context, currentDayKey)
    }

    @Synchronized
    fun blockDistraction(
        packageName: String,
        node: AccessibilityNodeInfo,
        wellbeing: Wellbeing,
    ) {
        ensureCurrentDay()
        val resolvedPackage =
            if (packageName.contains(YOUTUBE_CLIENT_PACKAGE_SUFFIX)) YOUTUBE_PACKAGE
            else packageName

        val isFeatureOpen = when (resolvedPackage) {
            INSTAGRAM_PACKAGE -> isInstagramFeatureOpen(node, wellbeing.blockedFeatures)
            SNAPCHAT_PACKAGE -> isSnapchatFeatureOpen(node, wellbeing.blockedFeatures)
            FACEBOOK_PACKAGE -> isFacebookFeatureOpen(node, wellbeing.blockedFeatures)
            REDDIT_PACKAGE -> isRedditFeatureOpen(node, wellbeing.blockedFeatures)
            YOUTUBE_PACKAGE -> isYoutubeFeatureOpen(node, wellbeing.blockedFeatures)
            else -> false
        }

        if (isFeatureOpen) {
            maxAllowedDuration[resolvedPackage]?.let {
                updateShortsScreenTime(wellbeing.allowedShortsTimeMs, it)
            }
        }
    }

    @Synchronized
    fun checkAndBlockShortsOnBrowser(wellbeing: Wellbeing, url: String): Boolean {
        ensureCurrentDay()
        if (!isBlockedShortsUrl(url, wellbeing.blockedFeatures)) return false
        updateShortsScreenTime(wellbeing.allowedShortsTimeMs)
        return true
    }

    @Synchronized
    private fun updateShortsScreenTime(
        allowedShortContentTimeMs: Long,
        maxAllowedDuration: Long = DEFAULT_MAX_ALLOWED_DURATION_MS,
    ) {
        val blockThreshold = if (allowedShortContentTimeMs > Long.MAX_VALUE - SAVING_INTERVAL_MS) {
            Long.MAX_VALUE
        } else {
            allowedShortContentTimeMs + SAVING_INTERVAL_MS
        }
        if (allowedShortContentTimeMs < 0L || shortContentScreenTime > blockThreshold) {
            blockedContentGoBack.invoke()
            return
        }

        val currentTime = System.currentTimeMillis()
        val elapsedTime = if (lastTimeShortsEvent != 0L) currentTime - lastTimeShortsEvent else 0L
        if (elapsedTime in 0..maxAllowedDuration) {
            shortContentScreenTime = if (shortContentScreenTime > Long.MAX_VALUE - elapsedTime) {
                Long.MAX_VALUE
            } else {
                shortContentScreenTime + elapsedTime
            }
        }
        lastTimeShortsEvent = currentTime

        if (currentTime - lastTimeSaved > SAVING_INTERVAL_MS) {
            SharedPrefsHelper.getSetShortsScreenTimeMs(context, shortContentScreenTime)
            lastTimeSaved = currentTime
            Log.d(TAG, "Short-form usage saved: ${shortContentScreenTime / 1000L} seconds")
        }
    }

    private fun ensureCurrentDay() {
        val today = currentLocalDayKey()
        if (currentDayKey != today) resetForNewDay(today)
    }

    private fun resetForNewDay(dayKey: String) {
        shortContentScreenTime = 0L
        lastTimeShortsEvent = 0L
        lastTimeSaved = 0L
        currentDayKey = dayKey
        SharedPrefsHelper.getSetShortsScreenTimeMs(context, 0L)
        SharedPrefsHelper.getSetShortsScreenTimeDay(context, dayKey)
    }

    private fun currentLocalDayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    companion object {
        private const val TAG = "Mindful.ShortsPlatformManager"
        private const val SAVING_INTERVAL_MS = 30_000L
        private const val DEFAULT_MAX_ALLOWED_DURATION_MS = 30_000L

        private val maxAllowedDuration = mapOf(
            INSTAGRAM_PACKAGE to 90_000L,
            SNAPCHAT_PACKAGE to 60_000L,
            FACEBOOK_PACKAGE to 90_000L,
            REDDIT_PACKAGE to 60_000L,
            YOUTUBE_PACKAGE to 180_000L,
        )

        private val instagramReelViewIds = setOf(
            "com.instagram.android:id/clips_video_container"
        )
        private val instagramExploreViewIds = setOf(
            "com.instagram.android:id/action_bar_search_edit_text"
        )
        private val youtubeShortsViewIdSuffix = ":id/reel_player_underlay"
        private val snapchatSpotlightViewIds = setOf(
            "com.snapchat.android:id/spotlight_card_static_thumbnail"
        )
        private val snapchatDiscoverViewIds = setOf(
            "com.snapchat.android:id/df_large_story"
        )
        private val facebookReelsNodeTexts = listOf("Add a comment", "कमेंट जोड़ें…")

        internal fun isBlockedShortsUrl(
            url: String,
            blockedFeatures: Set<PlatformFeatures>
        ): Boolean {
            val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            val host = parsed.host?.lowercase(Locale.ROOT) ?: return false
            val path = parsed.path?.lowercase(Locale.ROOT) ?: return false

            fun matches(domain: String, requiredPath: String): Boolean =
                (host == domain || host.endsWith(".$domain")) &&
                    path.startsWith(requiredPath)

            return (PlatformFeatures.INSTAGRAM_REELS in blockedFeatures &&
                (matches("instagram.com", "/reel/") ||
                    matches("instagram.com", "/reels/"))) ||
                (PlatformFeatures.INSTAGRAM_EXPLORE in blockedFeatures &&
                    matches("instagram.com", "/explore/")) ||
                (PlatformFeatures.YOUTUBE_SHORTS in blockedFeatures &&
                    matches("youtube.com", "/shorts/")) ||
                (PlatformFeatures.FACEBOOK_REELS in blockedFeatures &&
                    matches("facebook.com", "/reel/")) ||
                (PlatformFeatures.SNAPCHAT_SPOTLIGHT in blockedFeatures &&
                    matches("snapchat.com", "/spotlight/")) ||
                (PlatformFeatures.SNAPCHAT_DISCOVER in blockedFeatures &&
                    matches("snapchat.com", "/discover/"))
        }

        private fun isInstagramFeatureOpen(
            node: AccessibilityNodeInfo,
            blockedFeatures: Set<PlatformFeatures>,
        ): Boolean =
            (PlatformFeatures.INSTAGRAM_REELS in blockedFeatures &&
                instagramReelViewIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) ||
                (PlatformFeatures.INSTAGRAM_EXPLORE in blockedFeatures &&
                    instagramExploreViewIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() })

        private fun isYoutubeFeatureOpen(
            node: AccessibilityNodeInfo,
            blockedFeatures: Set<PlatformFeatures>,
        ): Boolean =
            PlatformFeatures.YOUTUBE_SHORTS in blockedFeatures &&
                node.findAccessibilityNodeInfosByViewId(
                    "${node.packageName}$youtubeShortsViewIdSuffix"
                ).isNotEmpty()

        private fun isSnapchatFeatureOpen(
            node: AccessibilityNodeInfo,
            blockedFeatures: Set<PlatformFeatures>,
        ): Boolean =
            (PlatformFeatures.SNAPCHAT_SPOTLIGHT in blockedFeatures &&
                snapchatSpotlightViewIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) ||
                (PlatformFeatures.SNAPCHAT_DISCOVER in blockedFeatures &&
                    snapchatDiscoverViewIds.any { node.findAccessibilityNodeInfosByViewId(it).isNotEmpty() })

        private fun isFacebookFeatureOpen(
            node: AccessibilityNodeInfo,
            blockedFeatures: Set<PlatformFeatures>,
        ): Boolean =
            PlatformFeatures.FACEBOOK_REELS in blockedFeatures &&
                facebookReelsNodeTexts.any { node.findAccessibilityNodeInfosByText(it).isNotEmpty() }

        private fun isRedditFeatureOpen(
            node: AccessibilityNodeInfo,
            blockedFeatures: Set<PlatformFeatures>,
        ): Boolean =
            PlatformFeatures.REDDIT_SHORTS in blockedFeatures &&
                node.viewIdResourceName == "feed_vertical_pager"
    }
}
