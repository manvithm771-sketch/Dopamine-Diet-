package com.mindful.android.services.accessibility

import android.content.Context
import com.mindful.android.helpers.storage.SharedPrefsHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsPlatformManagerPersistenceTest {
    @Test
    fun serviceShutdownFlushPersistsLatestCounterAndDay() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("UniquePrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val expectedUsageMs = 47_500L
        val expectedDay = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        SharedPrefsHelper.getSetShortsScreenTimeMs(context, expectedUsageMs)
        SharedPrefsHelper.getSetShortsScreenTimeDay(context, expectedDay)

        val manager = ShortsPlatformManager(context) {}
        manager.flushShortsScreenTime()

        assertEquals(
            expectedUsageMs,
            SharedPrefsHelper.getSetShortsScreenTimeMs(context, null)
        )
        assertEquals(
            expectedDay,
            SharedPrefsHelper.getSetShortsScreenTimeDay(context, null)
        )
    }
}
