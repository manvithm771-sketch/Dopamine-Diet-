package com.mindful.android.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusModeEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeMindfulSettingsEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import kotlinx.coroutines.flow.Flow

/** Read-only queries used by the first native Compose screens. */
@Dao
interface NativeDashboardDao {
    @Query("SELECT * FROM app_restriction_table ORDER BY app_package COLLATE NOCASE")
    fun observeAppRestrictions(): Flow<List<NativeAppRestrictionEntity>>

    @Query(
        """
        SELECT
            package_name AS packageName,
            SUM(screen_time) AS totalScreenTime,
            SUM(mobile_data) AS totalMobileData,
            SUM(wifi_data) AS totalWifiData
        FROM app_usage_table
        WHERE date >= :startEpochMillis AND date < :endEpochMillis
        GROUP BY package_name
        ORDER BY totalScreenTime DESC
        """
    )
    fun observeUsageForRange(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Flow<List<NativeAppUsageSummary>>

    @Query(
        """
        SELECT
            date AS dateEpochMillis,
            SUM(screen_time) AS totalScreenTime,
            SUM(mobile_data) AS totalMobileData,
            SUM(wifi_data) AS totalWifiData
        FROM app_usage_table
        WHERE date >= :startEpochMillis AND date < :endEpochMillis
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun observeDailyUsageRange(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Flow<List<NativeDailyUsageSummary>>

    @Query(
        """
        SELECT * FROM app_usage_table
        WHERE package_name = :packageName
          AND date >= :startEpochMillis AND date < :endEpochMillis
        ORDER BY date DESC
        """
    )
    fun observeAppUsage(
        packageName: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Flow<List<com.mindful.android.nativeapp.data.local.entity.NativeAppUsageEntity>>

    @Query("SELECT * FROM focus_profile_table ORDER BY session_type")
    fun observeFocusProfiles(): Flow<List<com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity>>

    @Query("SELECT * FROM focus_mode_table WHERE id = 0 LIMIT 1")
    fun observeFocusMode(): Flow<NativeFocusModeEntity?>

    @Query(
        """
        SELECT * FROM focus_sessions_table
        WHERE start_date_time >= :startEpochMillis AND start_date_time < :endEpochMillis
        ORDER BY start_date_time DESC
        """
    )
    fun observeFocusSessions(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Flow<List<NativeFocusSessionEntity>>

    @Query("SELECT * FROM restriction_groups_table ORDER BY group_name COLLATE NOCASE")
    fun observeRestrictionGroups(): Flow<List<NativeRestrictionGroupEntity>>

    @Query("SELECT * FROM wellbeing_table WHERE id = 0 LIMIT 1")
    fun observeWellbeing(): Flow<NativeWellbeingEntity?>

    @Query("SELECT excluded_apps FROM shared_unique_data_table WHERE id = 0 LIMIT 1")
    fun observeExcludedAppsJson(): Flow<String?>

    @Query("SELECT * FROM mindful_settings_table WHERE id = 0 LIMIT 1")
    fun observeSettings(): Flow<NativeMindfulSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: NativeMindfulSettingsEntity)
}

data class NativeAppUsageSummary(
    val packageName: String,
    val totalScreenTime: Long,
    val totalMobileData: Long,
    val totalWifiData: Long
)

data class NativeDailyUsageSummary(
    val dateEpochMillis: Long,
    val totalScreenTime: Long,
    val totalMobileData: Long,
    val totalWifiData: Long
)
