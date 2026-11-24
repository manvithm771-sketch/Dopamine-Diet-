package com.mindful.android.nativeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true
)
abstract class MindfulNativeDatabase : RoomDatabase() {
    abstract fun nativeDataDao(): NativeDataDao
    abstract fun nativeDashboardDao(): NativeDashboardDao
    abstract fun nativeUsageDao(): NativeUsageDao
    abstract fun nativeFocusDao(): NativeFocusDao
    abstract fun nativeRestrictionDao(): NativeRestrictionDao
}
