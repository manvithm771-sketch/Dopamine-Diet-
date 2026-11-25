package com.mindful.android.nativeapp.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.mindful.android.nativeapp.data.local.MindfulNativeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyDriftImporterTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun dryRunValidatesLegacyDataWithoutCreatingTargetOrChangingSource() = runBlocking {
        val target = context.getDatabasePath("mindful_native.db")
        target.delete()
        File(target.absolutePath + "-wal").delete()
        File(target.absolutePath + "-shm").delete()

        val source = createLegacyDatabase()
        val originalBytes = source.readBytes()

        val report = LegacyDriftImporter(context).dryRun()

        assertEquals(14, report.legacySchemaVersion)
        assertTrue(report.sourceFileLength > 0)
        assertEquals(10, report.tableRowCounts.size)
        assertEquals(10, report.tableDigests.size)
        TABLES.forEach { table -> assertEquals(1, report.tableRowCounts[table]) }
        assertFalse(report.nativeTargetAlreadyExists)
        assertFalse("Dry-run must not create the native database.", target.exists())
        assertTrue("Dry-run must not modify the legacy database.", originalBytes.contentEquals(source.readBytes()))
        assertEquals(14, readUserVersion(source))
    }

    @Test
    fun importsAllTablesAndRepeatImportVerifiesExistingData() = runBlocking {
        val source = createLegacyDatabase()
        val result = LegacyDriftImporter(context).importIfNeeded()

        assertTrue(result is LegacyDriftImporter.Result.Imported)
        val counts = (result as LegacyDriftImporter.Result.Imported).rowCounts
        assertEquals(10, counts.size)
        TABLES.forEach { table -> assertEquals(1, counts[table]) }

        val targetFile = context.getDatabasePath("mindful_native.db")
        val target = SQLiteDatabase.openDatabase(targetFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            TABLES.forEach { table -> assertEquals(1, countRows(target, table)) }
            assertEquals(1, countRows(target, "native_migration_state"))
            assertEquals(1_735_689_600_000L, readLong(target, "app_usage_table", "date"))
            assertEquals(1_735_689_601_000L, readLong(target, "crash_logs_table", "time_stamp"))
            assertEquals(1_735_689_602_000L, readLong(target, "focus_mode_table", "last_time_streak_updated"))
            assertEquals(1_735_689_603_000L, readLong(target, "focus_sessions_table", "start_date_time"))
            assertEquals("-62167219200000", readDefault(target, "app_usage_table", "date"))
            assertEquals("-62167219200000", readDefault(target, "crash_logs_table", "time_stamp"))
            assertEquals("-62167219200000", readDefault(target, "focus_mode_table", "last_time_streak_updated"))
            assertEquals("-62167219200000", readDefault(target, "focus_sessions_table", "start_date_time"))
            assertFalse(hasColumn(target, "mindful_settings_table", "left_emergency_passes"))
            assertFalse(hasColumn(target, "mindful_settings_table", "last_emergency_used"))
        } finally {
            target.close()
        }

        assertEquals(LegacyDriftImporter.Result.AlreadyImported, LegacyDriftImporter(context).importIfNeeded())
        assertTrue(source.exists())
        assertEquals(14, readUserVersion(source))
    }

    @Test
    fun detectsTargetTamperingOnRepeatImport() = runBlocking {
        createLegacyDatabase()
        LegacyDriftImporter(context).importIfNeeded()

        val targetFile = context.getDatabasePath("mindful_native.db")
        val target = SQLiteDatabase.openDatabase(targetFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            target.execSQL("UPDATE app_usage_table SET screen_time = 999 WHERE package_name = 'com.example.app'")
        } finally {
            target.close()
        }

        var failed = false
        try {
            LegacyDriftImporter(context).importIfNeeded()
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("A changed native table must invalidate the stored migration digest.", failed)
    }

    @Test
    fun failedInsertRollsBackEarlierTablesAndDoesNotWriteMarker() = runBlocking {
        // Keep the legacy fixture valid. Force the failure inside the destination
        // transaction with a test-only trigger, so this actually exercises rollback.
        val source = createLegacyDatabase()
        val native = Room.databaseBuilder(
            context,
            MindfulNativeDatabase::class.java,
            "mindful_native.db"
        ).build()
        try {
            native.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER force_legacy_import_failure
                   BEFORE INSERT ON app_usage_table
                   BEGIN
                       SELECT RAISE(ABORT, 'forced rollback test failure');
                   END"""
            )
        } finally {
            native.close()
        }

        var failed = false
        try {
            LegacyDriftImporter(context).importIfNeeded()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("The forced destination constraint should make the Room import fail.", failed)

        val targetFile = context.getDatabasePath("mindful_native.db")
        assertTrue(targetFile.isFile)
        val target = SQLiteDatabase.openDatabase(targetFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(0, countRows(target, "app_restriction_table"))
            assertEquals(0, countRows(target, "native_migration_state"))
        } finally {
            target.close()
        }
        assertTrue("The legacy source must remain present after rollback.", source.exists())
        assertEquals(14, readUserVersion(source))
    }

    @Test
    fun importsBackupDatabaseIntoIsolatedRoomFileWithoutTouchingInstalledLegacySource() = runBlocking {
        val installed = createLegacyDatabase()
        val backupSource = File(context.cacheDir, "legacy-backup-fixture.sqlite")
        installed.copyTo(backupSource, overwrite = true)
        val originalBytes = backupSource.readBytes()
        val stagingName = "mindful_restore_staging_test.db"
        val stagingFile = context.getDatabasePath(stagingName)
        stagingFile.delete()
        File(stagingFile.absolutePath + "-wal").delete()
        File(stagingFile.absolutePath + "-shm").delete()

        val result = LegacyDriftImporter(context).importFromFile(backupSource, stagingName)

        assertTrue(result is LegacyDriftImporter.Result.Imported)
        assertTrue(stagingFile.isFile)
        assertTrue(originalBytes.contentEquals(backupSource.readBytes()))
        assertTrue(installed.isFile)
        assertEquals(14, readUserVersion(installed))
        val staged = SQLiteDatabase.openDatabase(
            stagingFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            TABLES.forEach { table -> assertEquals(1, countRows(staged, table)) }
            assertEquals(1, countRows(staged, "native_migration_state"))
        } finally {
            staged.close()
        }
        stagingFile.delete()
        File(stagingFile.absolutePath + "-wal").delete()
        File(stagingFile.absolutePath + "-shm").delete()
        backupSource.delete()
        Unit
    }

    private fun createLegacyDatabase(): File {
        val directory = File(context.filesDir, "app_flutter").apply { mkdirs() }
        val file = File(directory, "Mindful.sqlite")
        file.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            CREATE_TABLES.forEach(db::execSQL)
            db.version = 14
            db.execSQL("INSERT INTO app_restriction_table (app_package) VALUES ('com.example.app')")
            db.execSQL("INSERT INTO app_usage_table VALUES ('com.example.app', 1735689600000, 120, 10, 20)")
            db.execSQL("INSERT INTO crash_logs_table (app_version, time_stamp, error, stack_trace) VALUES ('1.0', 1735689601000, 'test', 'trace')")
            db.execSQL("INSERT INTO focus_mode_table VALUES (0, 0, 0, 0, 1735689602000)")
            db.execSQL("INSERT INTO focus_profile_table VALUES (0, 1500, 0, 0, '[]')")
            db.execSQL("INSERT INTO focus_sessions_table (type, state, start_date_time, duration_secs, reflection) VALUES (0, 0, 1735689603000, 300, 'test')")
            db.execSQL("INSERT INTO mindful_settings_table (id, last_emergency_used) VALUES (0, 1735689604000)")
            db.execSQL("INSERT INTO restriction_groups_table (group_name, timer_sec, distracting_apps) VALUES ('Social', 600, '[]')")
            db.execSQL("INSERT INTO shared_unique_data_table (id, excluded_apps) VALUES (0, '[]')")
            db.execSQL("INSERT INTO wellbeing_table (id, allowed_shorts_time_sec, blocked_features, block_nsfw_sites, blocked_websites, nsfw_websites) VALUES (0, 1800, '[]', 0, '[]', '[]')")
        } finally {
            db.close()
        }
        return file
    }

    private fun countRows(db: SQLiteDatabase, table: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun readLong(db: SQLiteDatabase, table: String, column: String): Long {
        db.rawQuery("SELECT " + column + " FROM " + table + " LIMIT 1", null).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }

    private fun readDefault(db: SQLiteDatabase, table: String, column: String): String? {
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    return if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex)
                }
            }
        }
        error("Missing column $table.$column")
    }

    private fun readUserVersion(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.version
        } finally {
            db.close()
        }
    }

    companion object {
        private val TABLES = listOf(
            "app_restriction_table",
            "app_usage_table",
            "crash_logs_table",
            "focus_mode_table",
            "focus_profile_table",
            "focus_sessions_table",
            "mindful_settings_table",
            "restriction_groups_table",
            "shared_unique_data_table",
            "wellbeing_table"
        )

        private val CREATE_TABLES = listOf(
            """CREATE TABLE app_restriction_table (app_package TEXT NOT NULL PRIMARY KEY, timer_sec INTEGER NOT NULL DEFAULT 0, launch_limit INTEGER NOT NULL DEFAULT 0, associated_group_id INTEGER DEFAULT NULL, reminder_type TEXT NOT NULL DEFAULT 'toast')""",
            """CREATE TABLE app_usage_table (package_name TEXT NOT NULL, date INTEGER NOT NULL DEFAULT -62167219200000, screen_time INTEGER NOT NULL DEFAULT 0, mobile_data INTEGER NOT NULL DEFAULT 0, wifi_data INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (package_name, date))""",
            """CREATE TABLE crash_logs_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, app_version TEXT NOT NULL DEFAULT '', time_stamp INTEGER NOT NULL DEFAULT -62167219200000, error TEXT NOT NULL DEFAULT '', stack_trace TEXT NOT NULL DEFAULT '')""",
            """CREATE TABLE focus_mode_table (id INTEGER NOT NULL DEFAULT 0 PRIMARY KEY, session_type INTEGER NOT NULL DEFAULT 0, longest_streak INTEGER NOT NULL DEFAULT 0, current_streak INTEGER NOT NULL DEFAULT 0, last_time_streak_updated INTEGER NOT NULL DEFAULT -62167219200000)""",
            """CREATE TABLE focus_profile_table (session_type INTEGER NOT NULL PRIMARY KEY, session_duration INTEGER NOT NULL DEFAULT 0, enforce_session INTEGER NOT NULL DEFAULT 0, should_start_dnd INTEGER NOT NULL DEFAULT 0, distracting_apps TEXT NOT NULL DEFAULT '[]')""",
            """CREATE TABLE focus_sessions_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, type INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL DEFAULT 0, start_date_time INTEGER NOT NULL DEFAULT -62167219200000, duration_secs INTEGER NOT NULL DEFAULT 0, reflection TEXT NOT NULL DEFAULT '')""",
            """CREATE TABLE mindful_settings_table (id INTEGER NOT NULL DEFAULT 0 PRIMARY KEY, theme_mode INTEGER NOT NULL DEFAULT 0, accent_color TEXT NOT NULL DEFAULT 'Indigo', username TEXT NOT NULL DEFAULT 'Achiever', locale_code TEXT NOT NULL DEFAULT 'en', use_amoled_dark INTEGER NOT NULL DEFAULT 0, use_dynamic_colors INTEGER NOT NULL DEFAULT 0, default_home_tab INTEGER NOT NULL DEFAULT 0, usage_history_weeks INTEGER NOT NULL DEFAULT 52, left_emergency_passes INTEGER NOT NULL DEFAULT 3, last_emergency_used INTEGER NOT NULL DEFAULT -62167219200000, is_onboarding_done INTEGER NOT NULL DEFAULT 0, app_version TEXT NOT NULL DEFAULT '')""",
            """CREATE TABLE restriction_groups_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, group_name TEXT NOT NULL DEFAULT 'Social', timer_sec INTEGER NOT NULL DEFAULT 0, distracting_apps TEXT NOT NULL DEFAULT '[]')""",
            """CREATE TABLE shared_unique_data_table (id INTEGER NOT NULL DEFAULT 0 PRIMARY KEY, excluded_apps TEXT NOT NULL DEFAULT '[]')""",
            """CREATE TABLE wellbeing_table (id INTEGER NOT NULL DEFAULT 0 PRIMARY KEY, allowed_shorts_time_sec INTEGER NOT NULL DEFAULT 1800, blocked_features TEXT NOT NULL DEFAULT '[]', block_nsfw_sites INTEGER NOT NULL DEFAULT 0, blocked_websites TEXT NOT NULL DEFAULT '[]', nsfw_websites TEXT NOT NULL DEFAULT '[]')"""
        )
    }
}
