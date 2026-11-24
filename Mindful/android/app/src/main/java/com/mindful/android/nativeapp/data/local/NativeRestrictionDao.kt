package com.mindful.android.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity

@Dao
interface NativeRestrictionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRestriction(restriction: NativeAppRestrictionEntity)

    @Query("SELECT * FROM app_restriction_table WHERE app_package = :packageName LIMIT 1")
    suspend fun getRestriction(packageName: String): NativeAppRestrictionEntity?

    @Query("DELETE FROM app_restriction_table WHERE app_package = :packageName")
    suspend fun deleteRestriction(packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRestrictionGroup(group: NativeRestrictionGroupEntity): Long

    @Query("SELECT * FROM restriction_groups_table WHERE id = :groupId LIMIT 1")
    suspend fun getRestrictionGroup(groupId: Long): NativeRestrictionGroupEntity?

    @Transaction
    suspend fun saveRestrictionGroupWithApps(
        group: NativeRestrictionGroupEntity,
        appPackages: List<String>,
        previousAppPackages: List<String>
    ): Long {
        val groupId = saveRestrictionGroup(group).let { insertedId ->
            if (group.id > 0L) group.id else insertedId
        }
        val selectedPackages = appPackages.toSet()
        previousAppPackages.distinct().filterNot { it in selectedPackages }.forEach { packageName ->
            val existing = getRestriction(packageName)
            if (existing?.associatedGroupId == groupId) {
                saveRestriction(existing.copy(associatedGroupId = null))
            }
        }
        appPackages.distinct().forEach { packageName ->
            val existing = getRestriction(packageName)
            saveRestriction(
                existing?.copy(associatedGroupId = groupId)
                    ?: NativeAppRestrictionEntity(
                        appPackage = packageName,
                        associatedGroupId = groupId
                    )
            )
        }
        return groupId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWellbeing(settings: NativeWellbeingEntity)

    @Query("UPDATE app_restriction_table SET associated_group_id = NULL WHERE associated_group_id = :groupId")
    suspend fun detachAppsFromGroup(groupId: Long): Int

    @Query("DELETE FROM restriction_groups_table WHERE id = :groupId")
    suspend fun deleteRestrictionGroup(groupId: Long): Int

    @Transaction
    suspend fun deleteRestrictionGroupAndDetachApps(groupId: Long): Int {
        detachAppsFromGroup(groupId)
        return deleteRestrictionGroup(groupId)
    }
}
