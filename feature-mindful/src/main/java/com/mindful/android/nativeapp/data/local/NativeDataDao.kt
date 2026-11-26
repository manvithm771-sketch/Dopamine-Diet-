package com.mindful.android.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.mindful.android.nativeapp.data.local.entity.NativeMigrationStateEntity

@Dao
interface NativeDataDao {
    @Query("SELECT * FROM native_migration_state WHERE id = 1")
    suspend fun getMigrationState(): NativeMigrationStateEntity?

    @Query("SELECT * FROM native_migration_state WHERE id = 1")
    fun observeMigrationState(): Flow<NativeMigrationStateEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMigrationState(state: NativeMigrationStateEntity)
}
