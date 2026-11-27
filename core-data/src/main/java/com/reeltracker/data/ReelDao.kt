package com.reeltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reeltracker.model.DailyReelCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ReelEvent)

    @Query("SELECT COUNT(*) FROM reel_events WHERE wallClock >= :since")
    fun countSince(since: Long): Flow<Int>

    /** Instagram events, including records created before platform-specific counting was added. */
    @Query("SELECT COUNT(*) FROM reel_events WHERE wallClock >= :since AND strategyId != :youtubeStrategy")
    fun instagramCountSince(since: Long, youtubeStrategy: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_events WHERE wallClock >= :since AND strategyId = :youtubeStrategy")
    fun youtubeShortsCountSince(since: Long, youtubeStrategy: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_events WHERE strategyId != :youtubeStrategy")
    fun instagramTotalCount(youtubeStrategy: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_events WHERE strategyId = :youtubeStrategy")
    fun youtubeShortsTotalCount(youtubeStrategy: String): Flow<Int>

    /** All recorded days since installation, grouped using the device's local calendar. */
    @Query("""
        SELECT strftime('%Y-%m-%d', wallClock / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS total
        FROM reel_events
        GROUP BY strftime('%Y-%m-%d', wallClock / 1000, 'unixepoch', 'localtime')
        ORDER BY day ASC
    """)
    fun dailyCounts(): Flow<List<DailyReelCount>>

    @Query("SELECT MAX(wallClock) FROM reel_events")
    fun lastEventAt(): Flow<Long?>
}
