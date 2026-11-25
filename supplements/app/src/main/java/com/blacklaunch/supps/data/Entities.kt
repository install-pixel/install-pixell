package com.blacklaunch.supps.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Форма добавки — від неї залежить підпис одиниці («капс.», «табл.», «г»…). */
enum class Form { Capsule, Tablet, Powder, Drops, Other }

/** Коли приймати. Прив'язано до сну й їжі, а не до годинника, — бо графік змінний. */
enum class Slot { Wake, Day, Dinner, Bed }

/** Схема прийому. */
enum class Plan { Daily, Cycle, Group, AsNeeded }

/** Ранок (після пробудження й удень) чи вечір (вечеря й перед сном). */
enum class Part { Morning, Evening }

val Slot.part: Part get() = if (this == Slot.Wake || this == Slot.Day) Part.Morning else Part.Evening

inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

/**
 * Добавка. Усе, чого користувач не знає (мг, залишок, ціна), — null: тоді просто немає відповідного розрахунку,
 * а не хибні цифри.
 */
@Entity(tableName = "supplement")
data class Supplement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: String = Form.Capsule.name,
    /** Мг діючої речовини в одній одиниці (капсулі, таблетці, грамі). */
    val mgPerUnit: Double? = null,
    /** Скільки одиниць за один прийом. */
    val unitsPerDose: Double = 1.0,
    val slot: String = Slot.Wake.name,
    val plan: String = Plan.Daily.name,
    /** Цикл: днів прийому; у чергуванні — довжина свого блоку. */
    val onDays: Int = 0,
    /** Цикл: днів перерви. */
    val offDays: Int = 0,
    /** Цикл: з якого дня рахується (epochDay). */
    val startDay: Long = 0,
    /** Чергування: група і місце в черзі. */
    val groupId: Long? = null,
    val groupOrder: Int = 0,
    /** Скільки одиниць лишилось (null — невідомо). */
    val unitsLeft: Double? = null,
    /** Скільки одиниць у банці (null — невідомо). */
    val packUnits: Int? = null,
    /** Ціна банки (null — невідомо). */
    val packPrice: Double? = null,
    val note: String = "",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** Своя категорія для порядку в аптечці («Мозок», «Сон», «Тіло»); порожньо — без категорії. */
    @ColumnInfo(defaultValue = "") val category: String = "",
) {
    val formE: Form get() = enumOr(form, Form.Capsule)
    val slotE: Slot get() = enumOr(slot, Slot.Wake)
    val planE: Plan get() = enumOr(plan, Plan.Daily)

    /** Ціна однієї одиниці, якщо відомі і ціна, і кількість у банці. */
    val unitPrice: Double?
        get() {
            val price = packPrice ?: return null
            val units = packUnits ?: return null
            return if (units > 0 && price >= 0) price / units else null
        }
}

/** Група чергування: учасники йдуть по черзі блоками, між блоками — необов'язкова перерва. */
@Entity(tableName = "alt_group")
data class AltGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val breakDays: Int = 0,
    /** З якого дня рахується черга (epochDay). */
    val startDay: Long = 0,
)

/** Відмітка «прийняв» — одна на добавку за день. */
@Entity(tableName = "intake", indices = [Index(value = ["supplementId", "day"], unique = true), Index("day")])
data class Intake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val day: Long,
    val units: Double,
    val at: Long = System.currentTimeMillis(),
)

/**
 * Оцінка дня: ранкові (m1–m3, після пробудження) і вечірні (e1–e3, перед сном) відповіді 1–5 та коли їх дали
 * ([morningAt], [eveningAt] — мілісекунди; null — невідомо). Усе необов'язкове.
 */
@Entity(tableName = "check_in")
data class CheckIn(
    @PrimaryKey val day: Long,
    val m1: Int? = null,
    val m2: Int? = null,
    val m3: Int? = null,
    val e1: Int? = null,
    val e2: Int? = null,
    val e3: Int? = null,
    val morningAt: Long? = null,
    val eveningAt: Long? = null,
    val note: String = "",
) {
    fun answer(part: Part, i: Int): Int? = when (part) {
        Part.Morning -> when (i) { 0 -> m1; 1 -> m2; else -> m3 }
        Part.Evening -> when (i) { 0 -> e1; 1 -> e2; else -> e3 }
    }

    fun withAnswer(part: Part, i: Int, v: Int?): CheckIn = when (part) {
        Part.Morning -> when (i) { 0 -> copy(m1 = v); 1 -> copy(m2 = v); else -> copy(m3 = v) }
        Part.Evening -> when (i) { 0 -> copy(e1 = v); 1 -> copy(e2 = v); else -> copy(e3 = v) }
    }

    fun answered(part: Part, count: Int): Boolean = (0 until count).all { answer(part, it) != null }

    val isEmpty: Boolean get() = listOf(m1, m2, m3, e1, e2, e3).all { it == null } && note.isBlank()
}

/** Покупка банки: для історії витрат. Кількість і ціна — необов'язкові. */
@Entity(tableName = "purchase", indices = [Index("supplementId")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val day: Long,
    val units: Int? = null,
    val price: Double? = null,
)
