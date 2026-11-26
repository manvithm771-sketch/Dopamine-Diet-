package com.mindful.android.nativeapp.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeDashboardDaoTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private var database: MindfulNativeDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun dashboardQueriesReturnOnlyRequestedUsageRangeAndRestrictions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            context,
            MindfulNativeDatabase::class.java
        ).allowMainThreadQueries().build()
        database = db
        val sql = db.openHelper.writableDatabase

        sql.execSQL(
            "INSERT INTO app_restriction_table (app_package, timer_sec, launch_limit) " +
                "VALUES ('com.example.social', 600, 3)"
        )
        sql.execSQL(
            "INSERT INTO app_usage_table (package_name, date, screen_time) " +
                "VALUES ('com.example.social', 1000, 120)"
        )
        sql.execSQL(
            "INSERT INTO app_usage_table (package_name, date, screen_time) " +
                "VALUES ('com.example.video', 2000, 240)"
        )
        sql.execSQL(
            "INSERT INTO app_usage_table (package_name, date, screen_time) " +
                "VALUES ('com.example.outside', 9000, 999)"
        )

        sql.execSQL("INSERT INTO focus_mode_table (id, current_streak, longest_streak) VALUES (0, 4, 9)")
        sql.execSQL("INSERT INTO focus_profile_table (session_type, session_duration, enforce_session) VALUES (0, 1800, 1)")
        sql.execSQL("INSERT INTO focus_sessions_table (type, state, start_date_time, duration_secs, reflection) VALUES (0, 1, 1500, 600, 'test reflection')")
        sql.execSQL("INSERT INTO restriction_groups_table (group_name, timer_sec, distracting_apps) VALUES ('Social', 300, '[\"app.one\"]')")
        sql.execSQL("INSERT INTO wellbeing_table (id, allowed_shorts_time_sec, blocked_websites) VALUES (0, 900, '[\"example.com\"]')")

        val usage = db.nativeDashboardDao().observeUsageForRange(1000, 3000).first()
        val dailyUsage = db.nativeDashboardDao().observeDailyUsageRange(1000, 3000).first()
        val restrictions = db.nativeDashboardDao().observeAppRestrictions().first()
        val appUsage = db.nativeDashboardDao().observeAppUsage("com.example.social", 1000, 3000).first()
        val focusMode = db.nativeDashboardDao().observeFocusMode().first()
        val profiles = db.nativeDashboardDao().observeFocusProfiles().first()
        val sessions = db.nativeDashboardDao().observeFocusSessions(1000, 3000).first()
        val groups = db.nativeDashboardDao().observeRestrictionGroups().first()
        val wellbeing = db.nativeDashboardDao().observeWellbeing().first()

        assertEquals(4L, focusMode?.currentStreak)
        assertEquals(9L, focusMode?.longestStreak)
        assertEquals(1800L, profiles.single().sessionDuration)
        assertEquals("test reflection", sessions.single().reflection)
        assertEquals("Social", groups.single().groupName)
        assertEquals(900L, wellbeing?.allowedShortsTimeSec)
        assertEquals(2, dailyUsage.size)
        assertEquals(120L, dailyUsage[0].totalScreenTime)
        assertEquals(240L, dailyUsage[1].totalScreenTime)
        assertEquals(1, appUsage.size)
        assertEquals(120L, appUsage.single().screenTime)
        assertEquals(2, usage.size)
        assertEquals("com.example.video", usage[0].packageName)
        assertEquals(240L, usage[0].totalScreenTime)
        assertEquals("com.example.social", usage[1].packageName)
        assertEquals(120L, usage[1].totalScreenTime)
        assertEquals(1, restrictions.size)
        assertEquals("com.example.social", restrictions.single().appPackage)
        assertEquals(600L, restrictions.single().timerSec)
    }
}
