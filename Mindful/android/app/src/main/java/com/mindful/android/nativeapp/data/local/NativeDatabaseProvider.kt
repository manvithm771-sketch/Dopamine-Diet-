package com.mindful.android.nativeapp.data.local

import android.content.Context
import androidx.room.Room

/**
 * Process-wide access point for the isolated native database.
 *
 * This database is intentionally separate from Flutter's Mindful.sqlite. Creating this
 * instance never opens, renames, copies, or modifies the legacy Drift database.
 */
object NativeDatabaseProvider {
    internal const val DATABASE_NAME = "mindful_native.db"

    @Volatile
    private var instance: MindfulNativeDatabase? = null

    fun get(context: Context): MindfulNativeDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MindfulNativeDatabase::class.java,
                DATABASE_NAME
            ).build().also { instance = it }
        }
    }

    /**
     * Closes the process-wide Room instance before a controlled database-file swap.
     *
     * The caller must first stop all native database work and must not delete or replace
     * the database until this method returns. Room close releases its SQLite connections
     * so the caller can then handle the database and its -wal/-shm sidecars.
     * A subsequent get() creates a fresh Room instance against the database file.
     */
    fun closeForRestore() {
        synchronized(this) {
            val current = instance ?: return
            current.close()
            instance = null
        }
    }

    /** Internal test hook. Production code must use closeForRestore() for file replacement. */
    internal fun closeForTests() {
        closeForRestore()
    }
}
