package com.blacklaunch.qrcodes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Збережений код (QR або штрихкод). [name] — своя назва (наприклад «Пароль Wi‑Fi»): якщо вона є, зміст коду ніде не підписується —
 * лише назва. Немає — підписом слугує сам зміст.
 */
@Entity(tableName = "codes", indices = [Index(value = ["content"], unique = true)])
data class QrCode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val name: String? = null,
    /** Коли додано у «Вибране» (порядок на головному екрані); null — не у вибраному. */
    val favoriteAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** «QR» або «BARCODE» (див. CodeFormat). Старі записи — QR. */
    @ColumnInfo(defaultValue = "QR") val format: String = "QR",
) {
    val isFavorite: Boolean get() = favoriteAt != null

    /** Що показувати й друкувати під кодом. */
    val title: String get() = displayTitle(name, content)
}

/** Своя назва, а якщо її немає — зміст (у один рядок). */
fun displayTitle(name: String?, content: String): String =
    name?.trim()?.takeIf { it.isNotEmpty() } ?: content.trim().replace(Regex("\\s+"), " ")

