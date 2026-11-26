package com.mindful.android.nativeapp.data.migration

import android.content.Context
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.local.entity.NativeMigrationStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Explicit entry point for the complete legacy-to-native data copy.
 *
 * NativeStartupCoordinator invokes this before production components read or write
 * native Room data. The legacy database is copied read-only and is never deleted.
 */
class NativeMigrationCoordinator(context: Context) {

    private val databaseImporter = LegacyDriftImporter(context.applicationContext)
    private val preferencesMigrator = LegacyPreferencesMigrator(context.applicationContext)

    sealed class Result {
        data class Completed(
            val database: LegacyDriftImporter.Result,
            val uniquePreferencesCopied: Int,
            val crashLogPreferencesCopied: Int,
            val preferencesAlreadyMigrated: Boolean
        ) : Result()

        object LegacyDatabaseMissing : Result()
    }

    suspend fun migrateAll(): Result {
        // The database is the authoritative prerequisite. Never copy preferences while
        // silently proceeding without the user's existing database.
        val databaseResult = databaseImporter.importIfNeeded()
        if (databaseResult == LegacyDriftImporter.Result.LegacyDatabaseMissing) {
            return Result.LegacyDatabaseMissing
        }

        val preferencesResult = preferencesMigrator.migrateIfNeeded()
        return Result.Completed(
            database = databaseResult,
            uniquePreferencesCopied = preferencesResult.uniquePreferencesCopied,
            crashLogPreferencesCopied = preferencesResult.crashLogPreferencesCopied,
            preferencesAlreadyMigrated = preferencesResult.alreadyMigrated
        )
    }
}


/**
 * Serializes first-access recovery and legacy import across the launcher, accessibility
 * service, and WorkManager. Every production entry point must prepare before opening Room.
 */
internal object NativeStartupCoordinator {
    private val migrationMutex = Mutex()

    suspend fun prepare(context: Context) {
        // Serialize first-time migration across the launcher, accessibility service,
        // and WorkManager so two entry points cannot import the legacy database at once.
        migrationMutex.withLock {
            withContext(Dispatchers.IO) {
                val migrationResult = NativeMigrationCoordinator(context.applicationContext).migrateAll()
                if (migrationResult == NativeMigrationCoordinator.Result.LegacyDatabaseMissing) {
                    // Fresh native installations have no Flutter database to import. Persist
                    // an explicit native-first-install marker so services use Room as their
                    // source of truth instead of waiting forever for a legacy migration.
                    val database = NativeDatabaseProvider.get(context.applicationContext)
                    if (database.nativeDataDao().getMigrationState() == null) {
                        database.nativeDataDao().insertMigrationState(
                            NativeMigrationStateEntity(
                                legacySchemaVersion = 0,
                                importedAtEpochMillis = System.currentTimeMillis(),
                                sourceFileLength = 0L,
                                rowCountsJson = "{}",
                                tableDigestsJson = "{}"
                            )
                        )
                    }
                }
            }
        }
    }
}
