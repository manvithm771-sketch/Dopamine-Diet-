package com.reeltracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayStatsTest {
    @Test
    fun `empty day starts with zero counts`() {
        val stats = DayStats.EMPTY
        assertEquals(0, stats.count)
        assertEquals(0, stats.shortsCount)
        assertNull(stats.lastAt)
    }

    @Test
    fun `period totals add Instagram and YouTube separately and together`() {
        val counts = PeriodCounts(instagramReels = 12, youtubeShorts = 8)
        assertEquals(12, counts.instagramReels)
        assertEquals(8, counts.youtubeShorts)
        assertEquals(20, counts.total)
    }

    @Test
    fun `overview contains week month and lifetime totals`() {
        val overview = UsageOverview(
            thisWeek = PeriodCounts(3, 4),
            thisMonth = PeriodCounts(10, 20),
            total = PeriodCounts(100, 200),
        )
        assertEquals(7, overview.thisWeek.total)
        assertEquals(30, overview.thisMonth.total)
        assertEquals(300, overview.total.total)
    }
}
