package com.mindful.android.nativeapp.data.local

import androidx.room.Room
import com.mindful.android.nativeapp.data.local.entity.NativeAppUsageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeUsageDaoTest {
    private var database: MindfulNativeDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun retentionCleanupDeletesOnlyRowsOlderThanCutoff() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeUsageDao()
        dao.replaceDay(1_000L, listOf(NativeAppUsageEntity("com.example.old", 1_000L)))
        dao.replaceDay(2_000L, listOf(NativeAppUsageEntity("com.example.keep", 2_000L)))
        dao.replaceDay(3_000L, listOf(NativeAppUsageEntity("com.example.new", 3_000L)))

        assertEquals(1, dao.deleteBefore(2_000L))
        assertEquals(emptyList<NativeAppUsageEntity>(), dao.getForDay(1_000L))
        assertEquals("com.example.keep", dao.getForDay(2_000L).single().packageName)
        assertEquals("com.example.new", dao.getForDay(3_000L).single().packageName)
    }

    @Test
    fun replacingOneDayKeepsOtherDaysAndReplacesOldRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeUsageDao()

        dao.replaceDay(
            1_000L,
            listOf(
                NativeAppUsageEntity("com.example.one", 1_000L, screenTime = 100L),
                NativeAppUsageEntity("com.example.two", 1_000L, screenTime = 200L)
            )
        )
        dao.replaceDay(
            2_000L,
            listOf(NativeAppUsageEntity("com.example.history", 2_000L, screenTime = 300L))
        )

        dao.replaceDay(
            1_000L,
            listOf(NativeAppUsageEntity("com.example.one", 1_000L, screenTime = 150L))
        )

        assertEquals(1, dao.getForDay(1_000L).size)
        assertEquals(150L, dao.getForDay(1_000L).single().screenTime)
        assertEquals("com.example.history", dao.getForDay(2_000L).single().packageName)
    }

    @Test
    fun emptyRefreshDeletesOnlyTheRequestedDay() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeUsageDao()
        dao.replaceDay(1_000L, listOf(NativeAppUsageEntity("com.example.today", 1_000L)))
        dao.replaceDay(2_000L, listOf(NativeAppUsageEntity("com.example.old", 2_000L)))

        dao.replaceDay(1_000L, emptyList())

        assertEquals(emptyList<NativeAppUsageEntity>(), dao.getForDay(1_000L))
        assertEquals("com.example.old", dao.getForDay(2_000L).single().packageName)
    }

    @Test
    fun rejectsRowsWithDifferentDayBeforeChangingExistingRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeUsageDao()
        dao.replaceDay(1_000L, listOf(NativeAppUsageEntity("com.example.today", 1_000L)))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dao.replaceDay(
                    1_000L,
                    listOf(NativeAppUsageEntity("com.example.other", 2_000L))
                )
            }
        }
        assertEquals("com.example.today", dao.getForDay(1_000L).single().packageName)
    }
}
