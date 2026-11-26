package com.mindful.android.nativeapp.data.migration

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyPreferencesMigratorTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun copiesTypedValuesAtomicallyAndLeavesLegacyPreferencesUntouched() = runBlocking {
        context.nativeUniqueDataStore.edit { it.clear() }
        context.nativeCrashLogDataStore.edit { it.clear() }
        val uniqueLegacy = context.getSharedPreferences("UniquePrefs", Context.MODE_PRIVATE)
        uniqueLegacy.edit()
            .putString("profile_name", "Manvith")
            .putBoolean("first_launch", false)
            .putInt("daily_goal", 7)
            .putLong("last_sync", 1_735_689_600L)
            .putFloat("scale", 0.75f)
            .putStringSet("hidden_packages", setOf("com.example.one", "com.example.two"))
            .commit()

        val crashLegacy = context.getSharedPreferences("CrashLogPrefs", Context.MODE_PRIVATE)
        crashLegacy.edit()
            .putString("last_error", "sample")
            .putInt("crash_count", 3)
            .putBoolean("report_enabled", true)
            .commit()

        val first = LegacyPreferencesMigrator(context).migrateIfNeeded()
        assertEquals(6, first.uniquePreferencesCopied)
        assertEquals(3, first.crashLogPreferencesCopied)
        assertFalse(first.alreadyMigrated)

        val uniqueNative = context.nativeUniqueDataStore.data.first()
        assertEquals("Manvith", uniqueNative[stringPreferencesKey("profile_name")])
        assertEquals(false, uniqueNative[booleanPreferencesKey("first_launch")])
        assertEquals(7, uniqueNative[intPreferencesKey("daily_goal")])
        assertEquals(1_735_689_600L, uniqueNative[longPreferencesKey("last_sync")])
        assertEquals(0.75f, uniqueNative[floatPreferencesKey("scale")]!!, 0.0001f)
        assertEquals(setOf("com.example.one", "com.example.two"),
            uniqueNative[stringSetPreferencesKey("hidden_packages")])
        assertTrue(uniqueNative[booleanPreferencesKey("__mindful_legacy_shared_prefs_migrated_v1")] == true)

        val crashNative = context.nativeCrashLogDataStore.data.first()
        assertEquals("sample", crashNative[stringPreferencesKey("last_error")])
        assertEquals(3, crashNative[intPreferencesKey("crash_count")])
        assertEquals(true, crashNative[booleanPreferencesKey("report_enabled")])
        assertTrue(crashNative[booleanPreferencesKey("__mindful_legacy_shared_prefs_migrated_v1")] == true)

        uniqueLegacy.edit().putString("profile_name", "Changed later").commit()
        val second = LegacyPreferencesMigrator(context).migrateIfNeeded()
        assertEquals(0, second.uniquePreferencesCopied)
        assertEquals(0, second.crashLogPreferencesCopied)
        assertTrue(second.alreadyMigrated)
        assertEquals("Manvith",
            context.nativeUniqueDataStore.data.first()[stringPreferencesKey("profile_name")])

        assertEquals("Changed later", uniqueLegacy.getString("profile_name", null))
        assertEquals("sample", crashLegacy.getString("last_error", null))
    }

    @Test
    fun retriesSecondStoreAfterFirstStoreWasCommittedAndDoesNotOverwriteFirst() = runBlocking {
        context.nativeUniqueDataStore.edit { it.clear() }
        context.nativeCrashLogDataStore.edit { it.clear() }
        val uniqueLegacy = context.getSharedPreferences("UniquePrefs", Context.MODE_PRIVATE)
        uniqueLegacy.edit().clear().putString("stable_value", "first snapshot").commit()

        val crashLegacy = context.getSharedPreferences("CrashLogPrefs", Context.MODE_PRIVATE)
        crashLegacy.edit().clear()
            .putString("__mindful_legacy_shared_prefs_migrated_v1", "reserved-key-conflict")
            .putString("crash_value", "retry me")
            .commit()

        var failed = false
        try {
            LegacyPreferencesMigrator(context).migrateIfNeeded()
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Reserved-key validation should fail the second store migration.", failed)

        val uniqueAfterFailure = context.nativeUniqueDataStore.data.first()
        assertEquals("first snapshot", uniqueAfterFailure[stringPreferencesKey("stable_value")])
        assertTrue(uniqueAfterFailure[booleanPreferencesKey("__mindful_legacy_shared_prefs_migrated_v1")] == true)

        val crashAfterFailure = context.nativeCrashLogDataStore.data.first()
        assertFalse(crashAfterFailure.contains(stringPreferencesKey("crash_value")))
        assertFalse(crashAfterFailure.contains(booleanPreferencesKey("__mindful_legacy_shared_prefs_migrated_v1")))

        crashLegacy.edit().remove("__mindful_legacy_shared_prefs_migrated_v1").commit()
        val retry = LegacyPreferencesMigrator(context).migrateIfNeeded()

        assertEquals(0, retry.uniquePreferencesCopied)
        assertEquals(1, retry.crashLogPreferencesCopied)
        assertFalse(retry.alreadyMigrated)
        assertEquals("first snapshot",
            context.nativeUniqueDataStore.data.first()[stringPreferencesKey("stable_value")])
        assertEquals("retry me",
            context.nativeCrashLogDataStore.data.first()[stringPreferencesKey("crash_value")])
        assertTrue(context.nativeCrashLogDataStore.data.first()[
            booleanPreferencesKey("__mindful_legacy_shared_prefs_migrated_v1")] == true)

        assertEquals("first snapshot", uniqueLegacy.getString("stable_value", null))
        assertEquals("retry me", crashLegacy.getString("crash_value", null))
    }
}
