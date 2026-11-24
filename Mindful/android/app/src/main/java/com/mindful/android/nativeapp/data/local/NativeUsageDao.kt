package com.mindful.android.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mindful.android.nativeapp.data.local.entity.NativeAppUsageEntity

/**
 * Write boundary for native usage collection. A day refresh replaces only that day's
 * rows in one Room transaction and leaves historical dates untouched.
 */
@Dao
interface NativeUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<NativeAppUsageEntity>)

    @Query("DELETE FROM app_usage_table WHERE date = :dayStartEpochMillis")
    suspend fun deleteForDay(dayStartEpochMillis: Long)

    @Query("SELECT * FROM app_usage_table WHERE date = :dayStartEpochMillis ORDER BY package_name")
    suspend fun getForDay(dayStartEpochMillis: Long): List<NativeAppUsageEntity>

    @Query("DELETE FROM app_usage_table WHERE date < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int

    @Transaction
    suspend fun replaceDay(
        dayStartEpochMillis: Long,
        records: List<NativeAppUsageEntity>
    ) {
        require(records.all { it.dateEpochMillis == dayStartEpochMillis }) {
            "Every usage row must belong to the day being replaced."
        }
        deleteForDay(dayStartEpochMillis)
        if (records.isNotEmpty()) insertAll(records)
    }
}
