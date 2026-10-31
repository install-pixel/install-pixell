package com.blacklaunch.qrcodes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {
    /** Усі збережені — найновіші зверху. */
    @Query("SELECT * FROM codes ORDER BY createdAt DESC")
    fun all(): Flow<List<QrCode>>

    /** Вибрані — у тому порядку, в якому їх додавали. */
    @Query("SELECT * FROM codes WHERE favoriteAt IS NOT NULL ORDER BY favoriteAt ASC")
    fun favorites(): Flow<List<QrCode>>

    @Query("SELECT * FROM codes WHERE content = :content LIMIT 1")
    suspend fun byContent(content: String): QrCode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(code: QrCode): Long

    @Update
    suspend fun update(code: QrCode)

    @Query("UPDATE codes SET favoriteAt = :at WHERE id = :id")
    suspend fun setFavoriteAt(id: Long, at: Long?)

    /** Новий порядок вибраних: перший у списку — перший на головному. */
    @androidx.room.Transaction
    suspend fun reorderFavorites(ids: List<Long>) {
        val base = System.currentTimeMillis()
        ids.forEachIndexed { i, id -> setFavoriteAt(id, base + i) }
    }

    @Query("DELETE FROM codes WHERE id = :id")
    suspend fun delete(id: Long)
}
