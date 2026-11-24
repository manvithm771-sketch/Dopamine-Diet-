package com.mindful.android.nativeapp.data.local

import android.content.Context
import com.mindful.android.nativeapp.data.local.entity.NativeMigrationStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeDatabaseProviderTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        NativeDatabaseProvider.closeForTests()
        context.deleteDatabase("mindful_native.db")

        // Remove only the legacy fixture created by this test.
        val legacyDirectory = File(context.filesDir, "app_flutter")
        File(legacyDirectory, "Mindful.sqlite").delete()
        legacyDirectory.delete()
    }

    @Test
    fun returnsOneProcessWideDatabaseInstance() {
        val first = NativeDatabaseProvider.get(context)
        val second = NativeDatabaseProvider.get(context)

        assertSame(first, second)
        assertNotNull(first.openHelper.writableDatabase)
    }

    @Test
    fun createsExpectedNativeTablesAndCanPersistMigrationState() = runBlocking {
        val database = NativeDatabaseProvider.get(context)
        val sqlite = database.openHelper.writableDatabase

        val expectedTables = setOf(
            "app_usage_table",
            "app_restriction_table",
            "crash_logs_table",
            "focus_mode_table",
            "focus_profile_table",
            "focus_sessions_table",
            "mindful_settings_table",
            "restriction_groups_table",
            "shared_unique_data_table",
            "wellbeing_table",
            "native_migration_state"
        )
        val actualTables = mutableSetOf<String>()
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = ?",
            arrayOf("table")
        ).use { cursor ->
            while (cursor.moveToNext()) actualTables += cursor.getString(0)
        }
        assertEquals(expectedTables, actualTables.intersect(expectedTables))

        database.nativeDataDao().insertMigrationState(
            NativeMigrationStateEntity(
                legacySchemaVersion = 14,
                importedAtEpochMillis = 1_735_689_600_000L,
                sourceFileLength = 1234L,
                rowCountsJson = "{}",
                tableDigestsJson = "{}"
            )
        )
        assertEquals(14, database.nativeDataDao().getMigrationState()?.legacySchemaVersion)
    }

    @Test
    fun openingNativeDatabaseDoesNotCreateOrChangeLegacyDatabase() {
        val legacyDirectory = File(context.filesDir, "app_flutter").apply { mkdirs() }
        val legacyFile = File(legacyDirectory, "Mindful.sqlite")
        legacyFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        val originalBytes = legacyFile.readBytes()

        NativeDatabaseProvider.get(context).openHelper.writableDatabase

        assertEquals(originalBytes.toList(), legacyFile.readBytes().toList())
        assertEquals(originalBytes.size.toLong(), legacyFile.length())
    }
}
