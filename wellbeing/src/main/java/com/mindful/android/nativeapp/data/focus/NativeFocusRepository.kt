package com.mindful.android.nativeapp.data.focus

import com.mindful.android.nativeapp.data.local.NativeFocusDao
import com.mindful.android.nativeapp.data.local.entity.NativeFocusModeEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import java.util.Calendar

internal enum class ExpectedFocusCompletion { COMPLETED, NOT_DUE, STALE }

/** Native focus state machine preserving Drift enum values: active=0, successful=1, failed=2. */
internal class NativeFocusRepository(
    private val dao: NativeFocusDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun start(sessionType: Long): Long {
        require(sessionType >= 0L) { "Session type must not be negative." }
        val profile = dao.getProfile(sessionType)
            ?: NativeFocusProfileEntity(sessionType = sessionType)
        val now = clock()
        val mode = (dao.getFocusMode() ?: NativeFocusModeEntity()).copy(sessionType = sessionType)
        return dao.startSessionIfNone(
            NativeFocusSessionEntity(
                type = sessionType,
                state = STATE_ACTIVE,
                startDateTimeEpochMillis = now,
                durationSecs = profile.sessionDuration.coerceAtLeast(0L)
            ),
            mode
        )
    }

    suspend fun updateActiveReflection(reflection: String): Boolean {
        val active = dao.getActiveSession() ?: return false
        return dao.updateActiveSessionReflection(active.id, reflection) == 1
    }

    suspend fun finish(successful: Boolean, reflection: String = ""): Boolean {
        val active = dao.getActiveSession() ?: return false
        return finishActive(active, successful, reflection)
    }

    /**
     * Completes only the session that scheduled the worker. A stale worker is
     * terminal (not retryable); a session that has not expired is retryable.
     */
    suspend fun completeExpectedSessionIfExpired(expectedSessionId: Long): ExpectedFocusCompletion {
        val active = dao.getActiveSession() ?: return ExpectedFocusCompletion.STALE
        if (active.id != expectedSessionId || active.durationSecs <= 0L) {
            return ExpectedFocusCompletion.STALE
        }
        val now = clock()
        val elapsed = ((now - active.startDateTimeEpochMillis) / 1_000L).coerceAtLeast(0L)
        if (elapsed < active.durationSecs) return ExpectedFocusCompletion.NOT_DUE
        return if (finishActive(active, successful = true, reflection = "")) {
            ExpectedFocusCompletion.COMPLETED
        } else {
            ExpectedFocusCompletion.STALE
        }
    }

    suspend fun completeIfExpired(): Boolean {
        val active = dao.getActiveSession() ?: return false
        if (active.durationSecs <= 0L) return false
        val now = clock()
        val elapsed = ((now - active.startDateTimeEpochMillis) / 1_000L).coerceAtLeast(0L)
        return elapsed >= active.durationSecs &&
            finishActive(active, successful = true, reflection = "")
    }

    private suspend fun finishActive(
        active: NativeFocusSessionEntity,
        successful: Boolean,
        reflection: String
    ): Boolean {
        val now = clock()
        val elapsed = ((now - active.startDateTimeEpochMillis) / 1_000L).coerceAtLeast(0L)
        val planned = active.durationSecs.coerceAtLeast(0L)
        val profile = dao.getProfile(active.type)
        if (!successful && profile?.enforceSession == true && planned > 0L && elapsed < planned) {
            throw IllegalStateException("This focus profile does not allow ending the session early.")
        }
        if (successful && planned > 0L && elapsed < planned) {
            throw IllegalStateException("This timed focus session has not reached its duration.")
        }
        val actual = if (planned > 0L) elapsed.coerceAtMost(planned) else elapsed
        val state = if (successful) STATE_SUCCESSFUL else STATE_FAILED
        if (dao.finishSession(active.id, state, actual, reflection) == 0) return false
        if (successful) updateStreak(now)
        return true
    }

    private suspend fun updateStreak(now: Long) {
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMillis = today.timeInMillis
        val mode = dao.getFocusMode() ?: NativeFocusModeEntity()
        if (mode.lastTimeStreakUpdatedEpochMillis >= todayMillis) return
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val lastDay = Calendar.getInstance().apply {
            timeInMillis = mode.lastTimeStreakUpdatedEpochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val streak = if (lastDay == yesterday) mode.currentStreak + 1L else 1L
        dao.saveFocusMode(
            mode.copy(
                currentStreak = streak,
                longestStreak = maxOf(mode.longestStreak, streak),
                lastTimeStreakUpdatedEpochMillis = todayMillis
            )
        )
    }

    private companion object {
        const val STATE_ACTIVE = 0L
        const val STATE_SUCCESSFUL = 1L
        const val STATE_FAILED = 2L
    }
}
