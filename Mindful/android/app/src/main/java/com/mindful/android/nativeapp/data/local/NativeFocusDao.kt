package com.mindful.android.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mindful.android.nativeapp.data.local.entity.NativeFocusModeEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NativeFocusDao {
    @Query("SELECT * FROM focus_sessions_table WHERE state = 0 ORDER BY id DESC LIMIT 1")
    fun observeActiveSession(): Flow<NativeFocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions_table WHERE state = 0 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): NativeFocusSessionEntity?

    @Query("SELECT * FROM focus_profile_table WHERE session_type = :sessionType LIMIT 1")
    suspend fun getProfile(sessionType: Long): NativeFocusProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: NativeFocusProfileEntity)

    @Query("SELECT * FROM focus_mode_table WHERE id = 0 LIMIT 1")
    suspend fun getFocusMode(): NativeFocusModeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFocusMode(mode: NativeFocusModeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: NativeFocusSessionEntity): Long

    /**
     * Serializes the active-session check and both writes in one SQLite transaction.
     * Concurrent callers cannot create overlapping active focus sessions or update
     * the selected focus mode for a session that failed to start.
     */
    @Transaction
    suspend fun startSessionIfNone(
        session: NativeFocusSessionEntity,
        mode: NativeFocusModeEntity
    ): Long {
        check(getActiveSession() == null) { "A focus session is already active." }
        saveFocusMode(mode)
        return insertSession(session)
    }

    @Query("""
        UPDATE focus_sessions_table
        SET reflection = :reflection
        WHERE id = :sessionId AND state = 0
    """)
    suspend fun updateActiveSessionReflection(sessionId: Long, reflection: String): Int

    @Query("""
        UPDATE focus_sessions_table
        SET state = :state, duration_secs = :durationSecs, reflection = :reflection
        WHERE id = :sessionId AND state = 0
    """)
    suspend fun finishSession(sessionId: Long, state: Long, durationSecs: Long, reflection: String): Int
}
