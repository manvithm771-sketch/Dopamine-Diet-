package com.mindful.android.nativeapp.data.focus

import androidx.room.Room
import com.mindful.android.nativeapp.data.local.MindfulNativeDatabase
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeFocusRepositoryTest {
    private var database: MindfulNativeDatabase? = null

    @After fun tearDown() { database?.close() }

    @Test fun startsAndCompletesInfiniteSessionAndUpdatesStreak() = runBlocking {
        val db = newDatabase()
        val now = 1_800_000_000_000L
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }
        assertTrue(repository.start(0L) > 0L)
        assertTrue(repository.finish(successful = true))
        assertEquals(null, db.nativeFocusDao().getActiveSession())
        assertEquals(1L, db.nativeFocusDao().getFocusMode()?.currentStreak)
        assertEquals(1L, db.nativeFocusDao().getFocusMode()?.longestStreak)
    }

    @Test fun updatesReflectionOnlyForTheActiveSession() = runBlocking {
        val db = newDatabase()
        val repository = NativeFocusRepository(db.nativeFocusDao()) { 1_800_000_000_000L }
        val sessionId = repository.start(0L)

        assertTrue(repository.updateActiveReflection("Made steady progress."))
        assertEquals("Made steady progress.", db.nativeFocusDao().getActiveSession()?.reflection)

        assertTrue(repository.finish(successful = false))
        assertTrue(repository.updateActiveReflection("This must not be written.").not())
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    @Test fun refusesSecondActiveSession() = runBlocking {
        val db = newDatabase()
        val repository = NativeFocusRepository(db.nativeFocusDao()) { 1_800_000_000_000L }
        repository.start(0L)
        var duplicateRejected = false
        try {
            repository.start(1L)
        } catch (_: IllegalStateException) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
    }

    @Test fun concurrentStartsCanCreateOnlyOneActiveSession() = runBlocking {
        val db = newDatabase()
        val firstRepository = NativeFocusRepository(db.nativeFocusDao()) { 1_800_000_000_000L }
        val secondRepository = NativeFocusRepository(db.nativeFocusDao()) { 1_800_000_000_000L }
        val outcomes = coroutineScope {
            val gate = CompletableDeferred<Unit>()
            val attempts = listOf(firstRepository, secondRepository).map { repository ->
                async(Dispatchers.IO) {
                    gate.await()
                    runCatching { repository.start(0L) }
                }
            }
            gate.complete(Unit)
            attempts.awaitAll()
        }

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.isFailure })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
        assertTrue(db.nativeFocusDao().getActiveSession() != null)
    }

    @Test fun staleCompletionCannotFinishAReplacementSession() = runBlocking {
        val db = newDatabase()
        val start = 1_800_000_000_000L
        db.nativeFocusDao().saveProfile(NativeFocusProfileEntity(sessionType = 0L, sessionDuration = 10L))
        var now = start
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }

        val firstSessionId = repository.start(0L)
        now += 1_000L
        assertTrue(repository.finish(successful = false))
        now += 1_000L
        val replacementSessionId = repository.start(0L)
        now += 10_000L

        assertTrue(replacementSessionId != firstSessionId)
        assertEquals(
            ExpectedFocusCompletion.STALE,
            repository.completeExpectedSessionIfExpired(firstSessionId)
        )
        assertEquals(replacementSessionId, db.nativeFocusDao().getActiveSession()?.id)
        assertEquals(
            ExpectedFocusCompletion.COMPLETED,
            repository.completeExpectedSessionIfExpired(replacementSessionId)
        )
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    @Test fun expectedCompletionDistinguishesNotDueFromStaleSession() = runBlocking {
        val db = newDatabase()
        val start = 1_800_000_000_000L
        db.nativeFocusDao().saveProfile(
            NativeFocusProfileEntity(sessionType = 0L, sessionDuration = 60L)
        )
        var now = start
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }
        val sessionId = repository.start(0L)

        now += 30_000L
        assertEquals(
            ExpectedFocusCompletion.NOT_DUE,
            repository.completeExpectedSessionIfExpired(sessionId)
        )
        assertEquals(sessionId, db.nativeFocusDao().getActiveSession()?.id)

        now += 30_000L
        assertEquals(
            ExpectedFocusCompletion.COMPLETED,
            repository.completeExpectedSessionIfExpired(sessionId)
        )
        assertEquals(
            ExpectedFocusCompletion.STALE,
            repository.completeExpectedSessionIfExpired(sessionId)
        )
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    @Test fun timedSessionRejectsEarlySuccessAndCompletesAfterDuration() = runBlocking {
        val db = newDatabase()
        val start = 1_800_000_000_000L
        db.nativeFocusDao().saveProfile(NativeFocusProfileEntity(sessionType = 0L, sessionDuration = 60L))
        var now = start
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }
        repository.start(0L)
        now += 30_000L
        var earlySuccessRejected = false
        try {
            repository.finish(successful = true)
        } catch (_: IllegalStateException) {
            earlySuccessRejected = true
        }
        assertTrue(earlySuccessRejected)
        assertTrue(repository.finish(successful = false))
        now += 1_000L
        repository.start(0L)
        now += 60_000L
        assertTrue(repository.completeIfExpired())
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    @Test fun enforcedProfileRejectsEarlyManualEnd() = runBlocking {
        val db = newDatabase()
        val start = 1_800_000_000_000L
        db.nativeFocusDao().saveProfile(
            NativeFocusProfileEntity(sessionType = 0L, sessionDuration = 60L, enforceSession = true)
        )
        var now = start
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }
        repository.start(0L)
        now += 30_000L
        var earlyEndRejected = false
        try {
            repository.finish(successful = false)
        } catch (_: IllegalStateException) {
            earlyEndRejected = true
        }
        assertTrue(earlyEndRejected)
        assertTrue(repository.completeIfExpired().not())
        now += 30_000L
        assertTrue(repository.completeIfExpired())
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    @Test fun consecutiveSuccessfulCalendarDaysIncrementFocusStreak() = runBlocking {
        val db = newDatabase()
        val dayOne = localTime(2026, Calendar.JANUARY, 10, 12)
        var now = dayOne
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }

        repository.start(0L)
        assertTrue(repository.finish(successful = true))
        now = localTime(2026, Calendar.JANUARY, 11, 12)
        repository.start(0L)
        assertTrue(repository.finish(successful = true))

        val mode = db.nativeFocusDao().getFocusMode()
        assertEquals(2L, mode?.currentStreak)
        assertEquals(2L, mode?.longestStreak)
    }

    @Test fun missedCalendarDayResetsCurrentStreakButPreservesLongest() = runBlocking {
        val db = newDatabase()
        var now = localTime(2026, Calendar.JANUARY, 10, 12)
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }

        repository.start(0L)
        assertTrue(repository.finish(successful = true))
        now = localTime(2026, Calendar.JANUARY, 11, 12)
        repository.start(0L)
        assertTrue(repository.finish(successful = true))
        now = localTime(2026, Calendar.JANUARY, 13, 12)
        repository.start(0L)
        assertTrue(repository.finish(successful = true))

        val mode = db.nativeFocusDao().getFocusMode()
        assertEquals(1L, mode?.currentStreak)
        assertEquals(2L, mode?.longestStreak)
    }

    @Test fun zeroDurationSessionDoesNotAutoComplete() = runBlocking {
        val db = newDatabase()
        var now = 1_800_000_000_000L
        val repository = NativeFocusRepository(db.nativeFocusDao()) { now }
        repository.start(0L)
        now += 24 * 60 * 60 * 1_000L

        assertTrue(repository.completeIfExpired().not())
        assertTrue(repository.finish(successful = false))
        assertEquals(null, db.nativeFocusDao().getActiveSession())
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    private fun newDatabase(): MindfulNativeDatabase {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        return db
    }
}
