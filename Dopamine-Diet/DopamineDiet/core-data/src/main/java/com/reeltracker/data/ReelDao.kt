package com.reeltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT MAX(wallClock) FROM reel_events")
    fun lastEventAt(): Flow<Long?>
}
