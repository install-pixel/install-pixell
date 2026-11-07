package com.blacklaunch.paleta.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Збережена картка товару — усе, щоб потім побачити її повністю й здублювати: введене, обмеження, сама розкладка
 * коробок і результат. Довжини — у мм, вага — у грамах.
 */
@Entity(tableName = "cards", indices = [Index("sku")])
data class ProductCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val sku: String,
    // Коробка (картон).
    val boxL: Int,
    val boxW: Int,
    val boxH: Int,
    val boxG: Long,
    val unitsPerBox: Int,
    // Одна штука — необов'язково (null — не заповнили).
    val unitL: Int?,
    val unitW: Int?,
    val unitH: Int?,
    val unitG: Long?,
    // Обмеження.
    val heightLimit: Int,
    val weightLimitG: Long,
    /** Вага палети, що додана до ваги (0 — не враховувалась). */
    val palletG: Long,
    /** Дозволений звис з кожного боку, мм. */
    val overhang: Int,
    val adr: Boolean,
    // Розкладка шару: «x,y,r;…» (див. Layout.encode) і чи своя (не авто).
    val layout: String,
    val customLayout: Boolean,
    // Результат.
    val perLayer: Int,
    val layers: Int,
    val boxes: Int,
    val partial: Boolean,
    val heightMm: Int,
    val weightG: Long,
    val limitedByWeight: Boolean,
    val layersByHeight: Int,
)
