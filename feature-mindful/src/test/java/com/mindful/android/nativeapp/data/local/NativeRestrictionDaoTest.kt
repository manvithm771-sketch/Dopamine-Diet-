package com.mindful.android.nativeapp.data.local

import androidx.room.Room
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeRestrictionDaoTest {
    private var database: MindfulNativeDatabase? = null

    @After fun tearDown() { database?.close() }

    @Test fun savingGroupSynchronizesMembershipAndPreservesExistingAppRuleFields() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeRestrictionDao()

        val groupId = dao.saveRestrictionGroupWithApps(
            NativeRestrictionGroupEntity(
                groupName = "Social",
                timerSec = 900L,
                distractingAppsJson = """["com.example.one","com.example.two"]"""
            ),
            appPackages = listOf("com.example.one", "com.example.two"),
            previousAppPackages = emptyList()
        )
        dao.saveRestriction(NativeAppRestrictionEntity(
            appPackage = "com.example.one",
            timerSec = 600L,
            launchLimit = 4L,
            associatedGroupId = groupId,
            reminderType = "notification"
        ))

        dao.saveRestrictionGroupWithApps(
            NativeRestrictionGroupEntity(
                id = groupId,
                groupName = "Social",
                timerSec = 1200L,
                distractingAppsJson = """["com.example.two","com.example.three"]"""
            ),
            appPackages = listOf("com.example.two", "com.example.three"),
            previousAppPackages = listOf("com.example.one", "com.example.two")
        )

        val removed = dao.getRestriction("com.example.one")
        val retained = dao.getRestriction("com.example.two")
        val added = dao.getRestriction("com.example.three")
        assertEquals(600L, removed?.timerSec)
        assertEquals(4L, removed?.launchLimit)
        assertEquals("notification", removed?.reminderType)
        assertNull(removed?.associatedGroupId)
        assertEquals(groupId, retained?.associatedGroupId)
        assertEquals(groupId, added?.associatedGroupId)
        assertEquals(listOf("com.example.two", "com.example.three"),
            org.json.JSONArray(dao.getRestrictionGroup(groupId)?.distractingAppsJson ?: "[]").let { array ->
                (0 until array.length()).map { array.getString(it) }
            })
    }

    @Test fun savesUpdatesAndDeletesAnAppRule() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeRestrictionDao()

        dao.saveRestriction(NativeAppRestrictionEntity("com.example.app", timerSec = 600L))
        assertEquals(600L, dao.getRestriction("com.example.app")?.timerSec)

        dao.saveRestriction(NativeAppRestrictionEntity("com.example.app", timerSec = 900L, launchLimit = 3L))
        val updated = dao.getRestriction("com.example.app")
        assertEquals(900L, updated?.timerSec)
        assertEquals(3L, updated?.launchLimit)

        assertTrue(dao.deleteRestriction("com.example.app") > 0)
        assertNull(dao.getRestriction("com.example.app"))
    }

    @Test fun deletingGroupAtomicallyDetachesItsAppsAndPreservesOtherRuleFields() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val dao = db.nativeRestrictionDao()

        val groupId = dao.saveRestrictionGroupWithApps(
            NativeRestrictionGroupEntity(
                groupName = "Social",
                timerSec = 900L,
                distractingAppsJson = """["com.example.social"]"""
            ),
            appPackages = listOf("com.example.social"),
            previousAppPackages = emptyList()
        )
        dao.saveRestriction(
            NativeAppRestrictionEntity(
                appPackage = "com.example.social",
                timerSec = 600L,
                launchLimit = 4L,
                associatedGroupId = groupId,
                reminderType = "notification"
            )
        )

        assertEquals(1, dao.deleteRestrictionGroupAndDetachApps(groupId))

        assertNull(dao.getRestrictionGroup(groupId))
        val remainingRule = dao.getRestriction("com.example.social")
        assertEquals(600L, remainingRule?.timerSec)
        assertEquals(4L, remainingRule?.launchLimit)
        assertEquals("notification", remainingRule?.reminderType)
        assertNull(remainingRule?.associatedGroupId)
    }
}
