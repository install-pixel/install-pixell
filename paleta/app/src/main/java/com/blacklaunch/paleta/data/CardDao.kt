package com.blacklaunch.paleta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY createdAt DESC, id DESC")
    fun all(): Flow<List<ProductCard>>

    @Insert
    suspend fun insert(card: ProductCard): Long

    @Delete
    suspend fun delete(card: ProductCard)
}
