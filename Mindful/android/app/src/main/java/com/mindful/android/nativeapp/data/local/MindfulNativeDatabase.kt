package com.mindful.android.nativeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mindful.android.nativeapp.data.local.entity.*

@Database(
    entities = [
        NativeAppUsageEntity::class,
        NativeAppRestrictionEntity::class,
        NativeCrashLogEntity::class,
        NativeFocusModeEntity::class,
        NativeFocusProfileEntity::class,
        NativeFocusSessionEntity::class,
        NativeMindfulSettingsEntity::class,
        NativeRestrictionGroupEntity::class,
        NativeSharedUniqueDataEntity::class,
        NativeWellbeingEntity::class,
        NativeMigrationStateEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MindfulNativeDatabase : RoomDatabase() {
    abstract fun nativeDataDao(): NativeDataDao
    abstract fun nativeDashboardDao(): NativeDashboardDao
    abstract fun nativeUsageDao(): NativeUsageDao
    abstract fun nativeFocusDao(): NativeFocusDao
    abstract fun nativeRestrictionDao(): NativeRestrictionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE mindful_settings_table_new (
                        id INTEGER NOT NULL DEFAULT 0 PRIMARY KEY,
                        theme_mode INTEGER NOT NULL DEFAULT 0,
                        accent_color TEXT NOT NULL DEFAULT 'Indigo',
                        username TEXT NOT NULL DEFAULT 'Achiever',
                        locale_code TEXT NOT NULL DEFAULT 'en',
                        use_amoled_dark INTEGER NOT NULL DEFAULT 0,
                        use_dynamic_colors INTEGER NOT NULL DEFAULT 0,
                        default_home_tab INTEGER NOT NULL DEFAULT 0,
                        usage_history_weeks INTEGER NOT NULL DEFAULT 52,
                        is_onboarding_done INTEGER NOT NULL DEFAULT 0,
                        app_version TEXT NOT NULL DEFAULT ''
                    )"""
                )
                db.execSQL(
                    """INSERT INTO mindful_settings_table_new (
                        id, theme_mode, accent_color, username, locale_code,
                        use_amoled_dark, use_dynamic_colors, default_home_tab,
                        usage_history_weeks, is_onboarding_done, app_version
                    )
                    SELECT
                        id, theme_mode, accent_color, username, locale_code,
                        use_amoled_dark, use_dynamic_colors, default_home_tab,
                        usage_history_weeks, is_onboarding_done, app_version
                    FROM mindful_settings_table"""
                )
                db.execSQL("DROP TABLE mindful_settings_table")
                db.execSQL("ALTER TABLE mindful_settings_table_new RENAME TO mindful_settings_table")
            }
        }
    }
}
