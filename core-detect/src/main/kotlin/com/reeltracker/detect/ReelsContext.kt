package com.reeltracker.detect

/**
 * Identifiers for the short-form video feeds supported by the counter.
 *
 * Instagram's identifiers were observed in the original M0 capture.
 * YouTube Shorts uses its Shorts recycler/root identifiers. App updates can change
 * these internal IDs, so keep the identifiers isolated here.
 */
object ReelsContext {
    const val PACKAGE = "com.instagram.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    private const val ID = "$PACKAGE:id/"
    const val PAGER_ID = "${ID}clips_viewer_view_pager"
    const val REELS_TAB_ID = "${ID}clips_tab"

    const val YOUTUBE_PAGER_ID = "$YOUTUBE_PACKAGE:id/reel_recycler"

    private val youtubeShortsPagerIds = setOf(
        YOUTUBE_PAGER_ID,
        "$YOUTUBE_PACKAGE:id/reel_watch_fragment_root",
    )

    /** Bottom-nav tab ids look like `feed_tab`, `search_tab`, `profile_tab`. */
    private val tabId = Regex("""^${Regex.escape(ID)}\w+_tab$""")

    /** Windows inside Instagram that don't mean we've left Reels. */
    private val hostWindows = setOf("com.instagram.mainactivity.InstagramMainActivity")

    /** Windows from other packages that float over either tracked app. */
    private val overlayPackages = setOf("com.android.systemui", "com.reeltracker")

    fun isTrackedPackage(pkg: String?): Boolean =
        pkg == PACKAGE || pkg == YOUTUBE_PACKAGE

    fun packageFromSource(sourceId: String?): String? = when {
        sourceId?.startsWith(ID) == true -> PACKAGE
        sourceId?.startsWith("$YOUTUBE_PACKAGE:id/") == true -> YOUTUBE_PACKAGE
        else -> null
    }

    fun isPager(sourceId: String?, sourceClass: String? = null, deltaY: Int = 0): Boolean {
        if (sourceId == PAGER_ID || sourceId in youtubeShortsPagerIds) return true
        if (!isYouTubeSource(sourceId)) return false
        val id = sourceId.orEmpty().lowercase()
        val cls = sourceClass.orEmpty().lowercase()
        // YouTube changes internal resource IDs between app versions. Keep detection
        // scoped to its short-form player/reel views and vertically scrolling containers.
        val shortView = id.contains("reel") || id.contains("short")
        val scrollContainer = cls.contains("recyclerview") ||
            cls.contains("viewpager") || cls.contains("scrollview")
        return shortView && (scrollContainer || deltaY != 0)
    }

    fun isYouTubeSource(sourceId: String?): Boolean =
        sourceId?.startsWith("$YOUTUBE_PACKAGE:id/") == true

    fun isReelsTab(sourceId: String?): Boolean = sourceId == REELS_TAB_ID

    fun isOtherTab(sourceId: String?): Boolean =
        sourceId != null && sourceId != REELS_TAB_ID && tabId.matches(sourceId)

    fun isHostWindow(cls: String?): Boolean = cls in hostWindows

    /** ManvithX's own screen replaces the tracked app; its overlay does not. */
    private const val OWN_ACTIVITY = "com.reeltracker.ui.MainActivity"

    fun isOverlay(pkg: String, cls: String?): Boolean = pkg in overlayPackages && cls != OWN_ACTIVITY
}
