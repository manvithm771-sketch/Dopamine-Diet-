package com.reeltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReelEvent::class], version = 2, exportSchema = false)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun dao(): ReelDao

    companion object {
        // Drop the old timing table while preserving all recorded reel events.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sessions")
            }
        }
    }
}

object ReelGraph {
    @Volatile
    private var repo: ReelRepository? = null

    fun repository(context: Context): ReelRepository =
        repo ?: synchronized(this) {
            repo ?: ReelRepository(
                Room.databaseBuilder(context.applicationContext, ReelDatabase::class.java, "reels.db")
                    .addMigrations(ReelDatabase.MIGRATION_1_2)
                    .build()
                    .dao()
            ).also { repo = it }
        }
}
