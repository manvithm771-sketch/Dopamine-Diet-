package com.mindful.android.nativeapp.data.migration

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeMigrationCoordinatorTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        NativeDatabaseProvider.closeForTests()
        context.deleteDatabase("mindful_native.db")
        File(context.filesDir, "app_flutter/Mindful.sqlite").delete()
        context.getSharedPreferences("UniquePrefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("CrashLogPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking {
            context.nativeUniqueDataStore.edit { it.clear() }
            context.nativeCrashLogDataStore.edit { it.clear() }
        }
    }

    @Test
    fun doesNotMigratePreferencesWhenLegacyDatabaseIsMissing() = runBlocking {
        val legacyDatabase = File(context.filesDir, "app_flutter/Mindful.sqlite")
        legacyDatabase.delete()

        context.nativeUniqueDataStore.edit { it.clear() }
        context.nativeCrashLogDataStore.edit { it.clear() }

        val legacyUnique = context.getSharedPreferences("UniquePrefs", Context.MODE_PRIVATE)
        legacyUnique.edit().clear().putString("preserved_value", "legacy").commit()

        NativeStartupCoordinator.prepare(context)

        assertTrue(context.nativeUniqueDataStore.data.first().asMap().isEmpty())
        assertTrue(context.nativeCrashLogDataStore.data.first().asMap().isEmpty())
        assertEquals("legacy", legacyUnique.getString("preserved_value", null))
        val migrationState = NativeDatabaseProvider.get(context).nativeDataDao().getMigrationState()
        assertEquals("A fresh installation must explicitly select native Room.", 0,
            migrationState?.legacySchemaVersion ?: -1)
        assertTrue("No legacy preferences should be copied without a legacy database.",
            context.nativeUniqueDataStore.data.first().asMap().isEmpty())
    }
}
