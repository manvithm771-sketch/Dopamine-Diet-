package com.reeltracker.model

/** Counts for the current day. Event timestamps are retained for calendar grouping only. */
data class DayStats(
    val count: Int,
    val shortsCount: Int = 0,
    val lastAt: Long? = null,
) {
    companion object {
        val EMPTY = DayStats(0, 0, null)
    }
}

/** One local calendar day's count, derived from the persisted reel event history. */
data class DailyReelCount(
    val day: String,
    val total: Int,
)

/** Platform totals for one calendar period. */
data class PeriodCounts(
    val instagramReels: Int = 0,
    val youtubeShorts: Int = 0,
) {
    val total: Int get() = instagramReels + youtubeShorts
}

/** Monday-to-Sunday, current-month, and all-time counts. */
data class UsageOverview(
    val thisWeek: PeriodCounts = PeriodCounts(),
    val thisMonth: PeriodCounts = PeriodCounts(),
    val total: PeriodCounts = PeriodCounts(),
) {
    companion object {
        val EMPTY = UsageOverview()
    }
}
