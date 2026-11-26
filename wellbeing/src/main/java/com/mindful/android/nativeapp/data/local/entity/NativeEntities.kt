package com.mindful.android.nativeapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Exact v14 Drift column names and SQLite-compatible types.
 * Drift DateTime values are stored as Unix epoch milliseconds (INTEGER).
 * JSON-backed list columns remain unmodified TEXT.
 */
@Entity(tableName = "app_usage_table", primaryKeys = ["package_name", "date"])
data class NativeAppUsageEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "date", defaultValue = "-62167219200000") val dateEpochMillis: Long,
    @ColumnInfo(name = "screen_time", defaultValue = "0") val screenTime: Long = 0,
    @ColumnInfo(name = "mobile_data", defaultValue = "0") val mobileData: Long = 0,
    @ColumnInfo(name = "wifi_data", defaultValue = "0") val wifiData: Long = 0
)

@Entity(tableName = "app_restriction_table")
data class NativeAppRestrictionEntity(
    @PrimaryKey @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "timer_sec", defaultValue = "0") val timerSec: Long = 0,
    @ColumnInfo(name = "launch_limit", defaultValue = "0") val launchLimit: Long = 0,
    @ColumnInfo(name = "associated_group_id", defaultValue = "NULL") val associatedGroupId: Long? = null,
    @ColumnInfo(name = "reminder_type", defaultValue = "'toast'") val reminderType: String = "toast"
)

@Entity(tableName = "crash_logs_table")
data class NativeCrashLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "app_version", defaultValue = "''") val appVersion: String = "",
    @ColumnInfo(name = "time_stamp", defaultValue = "-62167219200000") val timeStampEpochMillis: Long = -62167219200000L,
    @ColumnInfo(defaultValue = "''") val error: String = "",
    @ColumnInfo(name = "stack_trace", defaultValue = "''") val stackTrace: String = ""
)

@Entity(tableName = "focus_mode_table")
data class NativeFocusModeEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "0") val id: Long = 0,
    @ColumnInfo(name = "session_type", defaultValue = "0") val sessionType: Long = 0,
    @ColumnInfo(name = "longest_streak", defaultValue = "0") val longestStreak: Long = 0,
    @ColumnInfo(name = "current_streak", defaultValue = "0") val currentStreak: Long = 0,
    @ColumnInfo(name = "last_time_streak_updated", defaultValue = "-62167219200000") val lastTimeStreakUpdatedEpochMillis: Long = -62167219200000L
)

@Entity(tableName = "focus_profile_table")
data class NativeFocusProfileEntity(
    @PrimaryKey @ColumnInfo(name = "session_type") val sessionType: Long,
    @ColumnInfo(name = "session_duration", defaultValue = "0") val sessionDuration: Long = 0,
    @ColumnInfo(name = "enforce_session", defaultValue = "0") val enforceSession: Boolean = false,
    @ColumnInfo(name = "should_start_dnd", defaultValue = "0") val shouldStartDnd: Boolean = false,
    @ColumnInfo(name = "distracting_apps", defaultValue = "'[]'") val distractingAppsJson: String = "[]"
)

@Entity(tableName = "focus_sessions_table")
data class NativeFocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val type: Long = 0,
    @ColumnInfo(defaultValue = "0") val state: Long = 0,
    @ColumnInfo(name = "start_date_time", defaultValue = "-62167219200000") val startDateTimeEpochMillis: Long = -62167219200000L,
    @ColumnInfo(name = "duration_secs", defaultValue = "0") val durationSecs: Long = 0,
    @ColumnInfo(defaultValue = "''") val reflection: String = ""
)

@Entity(tableName = "mindful_settings_table")
data class NativeMindfulSettingsEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "0") val id: Long = 0,
    @ColumnInfo(name = "theme_mode", defaultValue = "0") val themeMode: Long = 0,
    @ColumnInfo(name = "accent_color", defaultValue = "'Indigo'") val accentColor: String = "Indigo",
    @ColumnInfo(defaultValue = "'Achiever'") val username: String = "Achiever",
    @ColumnInfo(name = "locale_code", defaultValue = "'en'") val localeCode: String = "en",
    @ColumnInfo(name = "use_amoled_dark", defaultValue = "0") val useAmoledDark: Boolean = false,
    @ColumnInfo(name = "use_dynamic_colors", defaultValue = "0") val useDynamicColors: Boolean = false,
    @ColumnInfo(name = "default_home_tab", defaultValue = "0") val defaultHomeTab: Long = 0,
    @ColumnInfo(name = "usage_history_weeks", defaultValue = "52") val usageHistoryWeeks: Long = 52,
    @ColumnInfo(name = "is_onboarding_done", defaultValue = "0") val isOnboardingDone: Boolean = false,
    @ColumnInfo(name = "app_version", defaultValue = "''") val appVersion: String = ""
)

@Entity(tableName = "restriction_groups_table")
data class NativeRestrictionGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_name", defaultValue = "'Social'") val groupName: String = "Social",
    @ColumnInfo(name = "timer_sec", defaultValue = "0") val timerSec: Long = 0,
    @ColumnInfo(name = "distracting_apps", defaultValue = "'[]'") val distractingAppsJson: String = "[]"
)

@Entity(tableName = "shared_unique_data_table")
data class NativeSharedUniqueDataEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "0") val id: Long = 0,
    @ColumnInfo(name = "excluded_apps", defaultValue = "'[]'") val excludedAppsJson: String = "[]"
)

@Entity(tableName = "wellbeing_table")
data class NativeWellbeingEntity(
    @PrimaryKey @ColumnInfo(defaultValue = "0") val id: Long = 0,
    @ColumnInfo(name = "allowed_shorts_time_sec", defaultValue = "1800") val allowedShortsTimeSec: Long = 1800,
    @ColumnInfo(name = "blocked_features", defaultValue = "'[]'") val blockedFeaturesJson: String = "[]",
    @ColumnInfo(name = "block_nsfw_sites", defaultValue = "0") val blockNsfwSites: Boolean = false,
    @ColumnInfo(name = "blocked_websites", defaultValue = "'[]'") val blockedWebsitesJson: String = "[]",
    @ColumnInfo(name = "nsfw_websites", defaultValue = "'[]'") val nsfwWebsitesJson: String = "[]"
)

@Entity(tableName = "native_migration_state")
data class NativeMigrationStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "legacy_schema_version") val legacySchemaVersion: Int,
    @ColumnInfo(name = "imported_at_epoch_millis") val importedAtEpochMillis: Long,
    @ColumnInfo(name = "source_file_length") val sourceFileLength: Long,
    @ColumnInfo(name = "row_counts_json") val rowCountsJson: String,
    @ColumnInfo(name = "table_digests_json") val tableDigestsJson: String
)
