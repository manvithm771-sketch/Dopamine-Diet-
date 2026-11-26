package com.mindful.android.nativeapp.ui

import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeFocusCompletionSnapshotTest {
    @Test fun openEndedCompletionUsesActualElapsedDuration() {
        val session = NativeFocusSessionEntity(
            type = 0L,
            durationSecs = 0L,
            startDateTimeEpochMillis = 10_000L
        )
        val completed = completedFocusSessionSnapshot(session, 1_375_000L)
        assertEquals(1_365L, completed.durationSecs)
    }

    @Test fun timedCompletionDoesNotExceedPlannedDuration() {
        val session = NativeFocusSessionEntity(
            type = 1L,
            durationSecs = 900L,
            startDateTimeEpochMillis = 10_000L
        )
        val completed = completedFocusSessionSnapshot(session, 1_500_000L)
        assertEquals(900L, completed.durationSecs)
    }

    @Test fun clockBeforeStartProducesZeroElapsedDuration() {
        val session = NativeFocusSessionEntity(
            type = 2L,
            durationSecs = 0L,
            startDateTimeEpochMillis = 10_000L
        )
        val completed = completedFocusSessionSnapshot(session, 9_000L)
        assertEquals(0L, completed.durationSecs)
    }
}
