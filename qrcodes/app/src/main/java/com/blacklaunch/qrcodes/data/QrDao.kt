package com.blacklaunch.qrcodes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {
    /** Усі збережені — у своєму порядку (нові й підняті — зверху). */
    @Query("SELECT * FROM codes ORDER BY sortOrder DESC")
    fun all(): Flow<List<QrCode>>

    @Query("SELECT * FROM folders ORDER BY sortOrder DESC")
    fun folders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders")
    suspend fun foldersSnapshot(): List<Folder>

    @Insert
    suspend fun insertFolder(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE folders SET sortOrder = :order WHERE id = :id")
    suspend fun setFolderOrder(id: Long, order: Long)

    @Query("UPDATE codes SET sortOrder = :order WHERE id = :id")
    suspend fun setCodeOrder(id: Long, order: Long)

    @Query("UPDATE codes SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFolder(ids: List<Long>, folderId: Long?)

    /** Розпустити папку: коди лишаються, переходять у загальний список. */
    @androidx.room.Transaction
    suspend fun ungroup(folderId: Long) {
        releaseFolder(folderId)
        deleteFolderRow(folderId)
    }

    @Query("UPDATE codes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun releaseFolder(folderId: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    /** Нова папка з кодами [ids] на місці [order]. */
    @androidx.room.Transaction
    suspend fun createFolder(name: String, ids: List<Long>, order: Long): Long {
        val id = insertFolder(Folder(name = name, sortOrder = order))
        setFolder(ids, id)
        return id
    }

    /**
     * Новий порядок: [keys] — «f<id>» (папка) чи «c<id>» (код) зверху вниз. Більший sortOrder — вище; верхній
     * отримує «зараз», тож нові коди й далі з'являтимуться над ними.
     */
    @androidx.room.Transaction
    suspend fun reorder(keys: List<String>) {
        val base = System.currentTimeMillis()
        keys.forEachIndexed { i, key ->
            val id = key.drop(1).toLongOrNull() ?: return@forEachIndexed
            if (key.startsWith("f")) setFolderOrder(id, base - i) else setCodeOrder(id, base - i)
        }
    }

    /** Вибрані — у тому порядку, в якому їх додавали. */
    @Query("SELECT * FROM codes WHERE favoriteAt IS NOT NULL ORDER BY favoriteAt ASC")
    fun favorites(): Flow<List<QrCode>>

    @Query("SELECT * FROM codes ORDER BY createdAt ASC")
    suspend fun snapshot(): List<QrCode>

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
