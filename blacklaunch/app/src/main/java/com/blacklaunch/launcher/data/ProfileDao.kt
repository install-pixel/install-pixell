package com.blacklaunch.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY position, name")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM pinned_apps ORDER BY profileId, position")
    fun observePinned(): Flow<List<PinnedAppEntity>>

    @Query("SELECT * FROM notification_rules ORDER BY profileId, packageName")
    fun observeRules(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM profiles")
    suspend fun maxPosition(): Int

    /** Стандартні профілі мають фіксовані id — повторна вставка нічого не дублює. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(profiles: List<ProfileEntity>)

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pinned_apps WHERE profileId = :profileId")
    suspend fun clearPinned(profileId: String)

    @Insert
    suspend fun insertPinned(apps: List<PinnedAppEntity>)

    @Query("DELETE FROM notification_rules WHERE profileId = :profileId")
    suspend fun clearRules(profileId: String)

    @Insert
    suspend fun insertRules(rules: List<NotificationRuleEntity>)
}
