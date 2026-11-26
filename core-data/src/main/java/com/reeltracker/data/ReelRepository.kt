package com.reeltracker.data

import com.reeltracker.model.DayStats
import com.reeltracker.model.PeriodCounts
import com.reeltracker.model.UsageOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class ReelRepository internal constructor(private val dao: ReelDao) {
    val todayCount: Flow<Int> = today().flatMapLatest { dao.countSince(it) }
    val lastEventAt: Flow<Long?> = dao.lastEventAt()

    /** Current daily count for Android widget callers. */
    suspend fun todayCountNow(): Int = todayCount.first()

    val todayStats: Flow<DayStats> = today().flatMapLatest { start ->
        combine(
            dao.instagramCountSince(start, YOUTUBE_STRATEGY),
            dao.youtubeShortsCountSince(start, YOUTUBE_STRATEGY),
            dao.lastEventAt(),
        ) { instagramCount, shortsCount, lastAt ->
            DayStats(instagramCount, shortsCount, lastAt)
        }
    }

    /** Recomputes at the next local Monday/month boundary and whenever recorded events change. */
    val usageOverview: Flow<UsageOverview> = periodStarts().flatMapLatest { starts ->
        val week = combine(
            dao.instagramCountSince(starts.week, YOUTUBE_STRATEGY),
            dao.youtubeShortsCountSince(starts.week, YOUTUBE_STRATEGY),
        ) { instagram, youtube -> PeriodCounts(instagram, youtube) }
        val month = combine(
            dao.instagramCountSince(starts.month, YOUTUBE_STRATEGY),
            dao.youtubeShortsCountSince(starts.month, YOUTUBE_STRATEGY),
        ) { instagram, youtube -> PeriodCounts(instagram, youtube) }
        val total = combine(
            dao.instagramTotalCount(YOUTUBE_STRATEGY),
            dao.youtubeShortsTotalCount(YOUTUBE_STRATEGY),
        ) { instagram, youtube -> PeriodCounts(instagram, youtube) }
        combine(week, month, total) { weekCounts, monthCounts, totalCounts ->
            UsageOverview(weekCounts, monthCounts, totalCounts)
        }
    }

    /** feedVisitId prevents duplicate indexes within one visit; it does not measure duration. */
    suspend fun record(feedVisitId: Long, key: Int, strategyId: String) {
        dao.insertEvent(ReelEvent(sessionId = feedVisitId, key = key, wallClock = now(), strategyId = strategyId))
    }

    private fun now() = System.currentTimeMillis()

    private fun today(): Flow<Long> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            emit(date.atStartOfDay(zone).toInstant().toEpochMilli())
            val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((next - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        }
    }.distinctUntilChanged()

    private data class PeriodStarts(val week: Long, val month: Long)

    private fun periodStarts(): Flow<PeriodStarts> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val monthStart = today.withDayOfMonth(1)
            val nextWeek = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val nextMonth = monthStart.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            emit(
                PeriodStarts(
                    week = weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                    month = monthStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            )
            val nextBoundary = minOf(nextWeek, nextMonth)
            delay((nextBoundary - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        }
    }

    private companion object {
        const val YOUTUBE_STRATEGY = "index-v1@com.google.android.youtube"
    }
}
