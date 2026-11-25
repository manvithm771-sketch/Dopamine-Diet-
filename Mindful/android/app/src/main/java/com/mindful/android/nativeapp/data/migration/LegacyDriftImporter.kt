package com.mindful.android.nativeapp.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Base64
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mindful.android.nativeapp.data.local.MindfulNativeDatabase
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * One-shot, read-only import from Flutter/Drift v14 into a separate Room file.
 *
 * Source: filesDir/app_flutter/Mindful.sqlite
 * Target: mindful_native.db
 *
 * The legacy file is never opened writable, altered, renamed, or deleted.
 * All target inserts and the completion marker share one Room transaction.
 */
class LegacyDriftImporter(private val context: Context) {

    sealed class Result {
        data class Imported(val rowCounts: Map<String, Int>) : Result()
        object AlreadyImported : Result()
        object LegacyDatabaseMissing : Result()
    }

    /**
     * A read-only report for Phase 3 preflight. This method never opens Room,
     * creates the native database, or writes to the legacy database.
     */
    data class DryRunReport(
        val legacySchemaVersion: Int,
        val sourceFileLength: Long,
        val tableRowCounts: Map<String, Int>,
        val tableDigests: Map<String, String>,
        val nativeTargetAlreadyExists: Boolean
    )

    suspend fun dryRun(): DryRunReport {
        val sourceFile = File(context.filesDir, "app_flutter/Mindful.sqlite")
        check(sourceFile.isFile) { "Legacy Mindful.sqlite was not found; dry-run stopped." }

        val source = try {
            SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: SQLiteException) {
            throw IllegalStateException("Legacy Mindful.sqlite exists but cannot be opened read-only.", error)
        }

        try {
            validateSource(source)
            val counts = linkedMapOf<String, Int>()
            val digests = linkedMapOf<String, String>()
            for (spec in tables) {
                val cursor = source.rawQuery("SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}", null)
                counts[spec.name] = cursor.count
                digests[spec.name] = digest(cursor)
            }
            return DryRunReport(
                legacySchemaVersion = source.version,
                sourceFileLength = sourceFile.length(),
                tableRowCounts = counts,
                tableDigests = digests,
                nativeTargetAlreadyExists = context.getDatabasePath("mindful_native.db").exists()
            )
        } finally {
            source.close()
        }
    }

    private data class TableSpec(val name: String, val columns: Set<String>, val orderBy: String)

    private val tables = listOf(
        TableSpec("app_restriction_table", setOf("app_package", "timer_sec", "launch_limit", "associated_group_id", "reminder_type"), "app_package"),
        TableSpec("app_usage_table", setOf("package_name", "date", "screen_time", "mobile_data", "wifi_data"), "package_name, date"),
        TableSpec("crash_logs_table", setOf("id", "app_version", "time_stamp", "error", "stack_trace"), "id"),
        TableSpec("focus_mode_table", setOf("id", "session_type", "longest_streak", "current_streak", "last_time_streak_updated"), "id"),
        TableSpec("focus_profile_table", setOf("session_type", "session_duration", "enforce_session", "should_start_dnd", "distracting_apps"), "session_type"),
        TableSpec("focus_sessions_table", setOf("id", "type", "state", "start_date_time", "duration_secs", "reflection"), "id"),
        TableSpec("mindful_settings_table", setOf("id", "theme_mode", "accent_color", "username", "locale_code", "use_amoled_dark", "use_dynamic_colors", "default_home_tab", "usage_history_weeks", "is_onboarding_done", "app_version"), "id"),
        TableSpec("restriction_groups_table", setOf("id", "group_name", "timer_sec", "distracting_apps"), "id"),
        TableSpec("shared_unique_data_table", setOf("id", "excluded_apps"), "id"),
        TableSpec("wellbeing_table", setOf("id", "allowed_shorts_time_sec", "blocked_features", "block_nsfw_sites", "blocked_websites", "nsfw_websites"), "id")
    )

    suspend fun importIfNeeded(): Result =
        importFromFile(
            File(context.filesDir, "app_flutter/Mindful.sqlite"),
            "mindful_native.db"
        )

    /**
     * Imports a validated Drift v14 database file into a separately named Room database.
     * Used for restoring a Flutter v1 backup into an isolated staging database; the caller
     * owns the source file and must not pass the installed legacy database as a target.
     */
    suspend fun importFromFile(sourceFile: File, targetDatabaseName: String): Result {
        require(targetDatabaseName.isNotBlank() &&
            File(targetDatabaseName).name == targetDatabaseName &&
            !targetDatabaseName.contains(File.separatorChar)) {
            "Target database name must be a simple private filename."
        }
        require(sourceFile.canonicalFile != context.getDatabasePath(targetDatabaseName).canonicalFile) {
            "Legacy source and native target must be different files."
        }
        if (!sourceFile.isFile) return Result.LegacyDatabaseMissing

        // Validate the source before Room is allowed to create/open the target.
        val source = try {
            SQLiteDatabase.openDatabase(
                sourceFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (error: SQLiteException) {
            throw IllegalStateException("Legacy Mindful.sqlite exists but cannot be opened read-only.", error)
        }

        val nativeDb = try {
            validateSource(source)
            Room.databaseBuilder(
                context.applicationContext,
                MindfulNativeDatabase::class.java,
                targetDatabaseName
            ).addMigrations(MindfulNativeDatabase.MIGRATION_1_2).build()
        } catch (error: Throwable) {
            source.close()
            throw error
        }

        try {
            val importedCounts = linkedMapOf<String, Int>()
            var wasAlreadyImported = false
            nativeDb.withTransaction {
                val target = nativeDb.openHelper.writableDatabase
                val stateCursor = target.query(
                    "SELECT row_counts_json, table_digests_json FROM native_migration_state WHERE id = 1"
                )
                val existingMarker = try {
                    if (stateCursor.moveToFirst()) {
                        JSONObject(stateCursor.getString(0)) to JSONObject(stateCursor.getString(1))
                    } else null
                } finally {
                    stateCursor.close()
                }
                if (existingMarker != null) {
                    wasAlreadyImported = true
                    val expectedCounts = existingMarker.first
                    val expectedDigests = existingMarker.second
                    for (spec in tables) {
                        val sourceCursor = source.rawQuery(
                            "SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}", null
                        )
                        val sourceCount = sourceCursor.count
                        val targetColumns = tableColumns(target, spec.name)
                        val sourceDigest = digest(sourceCursor, targetColumns)

                        val targetCursor = target.query(
                            "SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}"
                        )
                        val targetCount = targetCursor.count
                        val targetDigest = digest(targetCursor, targetColumns)

                        check(expectedCounts.getInt(spec.name) == sourceCount &&
                            expectedCounts.getInt(spec.name) == targetCount) {
                            "Previously imported row count no longer matches for ${spec.name}."
                        }
                        check(expectedDigests.getString(spec.name) == sourceDigest &&
                            expectedDigests.getString(spec.name) == targetDigest) {
                            "Previously imported content no longer matches for ${spec.name}."
                        }
                    }
                    return@withTransaction
                }

                for (spec in tables) {
                    val existing = target.query("SELECT COUNT(*) FROM `${spec.name}`")
                    val existingCount = try { if (existing.moveToFirst()) existing.getInt(0) else 0 } finally { existing.close() }
                    check(existingCount == 0) {
                        "Native target table ${spec.name} is not empty; refusing to merge legacy and native records."
                    }
                }

                val sourceDigests = linkedMapOf<String, String>()
                val rowCounts = linkedMapOf<String, Int>()
                for (spec in tables) {
                    val targetColumns = tableColumns(target, spec.name)
                    val cursor = source.rawQuery("SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}", null)
                    val count = try {
                        var copied = 0
                        while (cursor.moveToNext()) {
                            val values = cursor.toContentValues(targetColumns)
                            val inserted = target.insert(spec.name, SQLiteDatabase.CONFLICT_ABORT, values)
                            check(inserted != -1L) { "Insert failed for ${spec.name}." }
                            copied++
                        }
                        copied
                    } finally {
                        cursor.close()
                    }
                    rowCounts[spec.name] = count
                    sourceDigests[spec.name] = digest(
                        source.rawQuery("SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}", null),
                        targetColumns
                    )
                }

                val targetDigests = linkedMapOf<String, String>()
                for (spec in tables) {
                    val cursor = target.query("SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}")
                    val count = try {
                        var rows = 0
                        while (cursor.moveToNext()) rows++
                        rows
                    } finally {
                        cursor.close()
                    }
                    check(count == rowCounts.getValue(spec.name)) {
                        "Row-count verification failed for ${spec.name}: expected ${rowCounts[spec.name]}, found $count."
                    }
                    targetDigests[spec.name] = digest(
                        target.query("SELECT * FROM `${spec.name}` ORDER BY ${spec.orderBy}"),
                        tableColumns(target, spec.name)
                    )
                    check(sourceDigests[spec.name] == targetDigests[spec.name]) {
                        "Content verification failed for ${spec.name}; native import rolled back."
                    }
                }

                val marker = ContentValues().apply {
                    put("id", 1)
                    put("legacy_schema_version", 14)
                    put("imported_at_epoch_millis", System.currentTimeMillis())
                    put("source_file_length", sourceFile.length())
                    put("row_counts_json", JSONObject(rowCounts as Map<*, *>).toString())
                    put("table_digests_json", JSONObject(targetDigests as Map<*, *>).toString())
                }
                check(target.insert("native_migration_state", SQLiteDatabase.CONFLICT_ABORT, marker) != -1L) {
                    "Could not write native migration completion marker."
                }
                importedCounts.putAll(rowCounts)
            }
            return if (wasAlreadyImported) Result.AlreadyImported else Result.Imported(importedCounts)
        } finally {
            nativeDb.close()
            source.close()
        }
    }

    private fun validateSource(source: SQLiteDatabase) {
        val integrity = source.rawQuery("PRAGMA integrity_check", null)
        val integrityOk = try { integrity.moveToFirst() && integrity.getString(0) == "ok" } finally { integrity.close() }
        check(integrityOk) { "Legacy database failed SQLite integrity_check; import stopped." }

        val version = source.rawQuery("PRAGMA user_version", null)
        val schemaVersion = try { if (version.moveToFirst()) version.getInt(0) else -1 } finally { version.close() }
        check(schemaVersion == 14) {
            "Expected legacy Drift schema version 14, found $schemaVersion. No import was attempted."
        }

        for (spec in tables) {
            val cursor = source.rawQuery("PRAGMA table_info(`${spec.name}`)", null)
            val actualColumns = try {
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            } finally {
                cursor.close()
            }
            check(actualColumns.containsAll(spec.columns)) {
                "Legacy schema mismatch in ${spec.name}. Required ${spec.columns.sorted()}, found ${actualColumns.sorted()}."
            }
        }
    }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): Set<String> {
        val cursor = database.query("PRAGMA table_info(`$table`)")
        return try {
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun Cursor.toContentValues(allowedColumns: Set<String>): ContentValues {
        val values = ContentValues(columnCount)
        for (index in 0 until columnCount) {
            val name = getColumnName(index)
            if (name !in allowedColumns) continue
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> values.put(name, getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> values.put(name, getDouble(index))
                Cursor.FIELD_TYPE_STRING -> values.put(name, getString(index))
                Cursor.FIELD_TYPE_BLOB -> values.put(name, getBlob(index))
            }
        }
        return values
    }

    private fun digest(cursor: Cursor, includedColumns: Set<String>? = null): String {
        cursor.use { rows ->
            val sha = MessageDigest.getInstance("SHA-256")
            val order = rows.columnNames.indices
                .filter { includedColumns == null || rows.getColumnName(it) in includedColumns }
                .sortedBy { rows.getColumnName(it) }
            while (rows.moveToNext()) {
                for (index in order) {
                    val name = rows.getColumnName(index)
                    sha.update(name.toByteArray(Charsets.UTF_8))
                    sha.update(0.toByte())
                    when (rows.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> sha.update("NULL".toByteArray())
                        Cursor.FIELD_TYPE_INTEGER -> sha.update("I:${rows.getLong(index)}".toByteArray())
                        Cursor.FIELD_TYPE_FLOAT -> sha.update("F:${rows.getDouble(index)}".toByteArray())
                        Cursor.FIELD_TYPE_STRING -> sha.update("S:${rows.getString(index)}".toByteArray(Charsets.UTF_8))
                        Cursor.FIELD_TYPE_BLOB -> {
                            sha.update("B:".toByteArray())
                            sha.update(rows.getBlob(index))
                        }
                    }
                    sha.update(0.toByte())
                }
                sha.update(10.toByte())
            }
            return Base64.encodeToString(sha.digest(), Base64.NO_WRAP)
        }
    }
}
