package com.blacklaunch.supps.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Форма добавки — від неї залежить підпис одиниці («капс.», «табл.», «г»…). */
enum class Form { Capsule, Tablet, Powder, Drops, Other }

/** Коли приймати. Прив'язано до сну й їжі, а не до годинника, — бо графік змінний. */
enum class Slot { Wake, Day, Dinner, Bed }

/** Схема прийому. */
enum class Plan { Daily, Cycle, Group, AsNeeded }

/** Тип дня для щоденної оцінки (зміни сильно впливають на сон і настрій). */
enum class DayType { Shift1, Shift2, Off, Other }

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

/** Щоденна оцінка: до чотирьох відповідей 1–5 і тип дня. Усе необов'язкове. */
@Entity(tableName = "check_in")
data class CheckIn(
    @PrimaryKey val day: Long,
    val a1: Int? = null,
    val a2: Int? = null,
    val a3: Int? = null,
    val a4: Int? = null,
    val dayType: String? = null,
    val note: String = "",
) {
    fun answer(i: Int): Int? = when (i) {
        0 -> a1
        1 -> a2
        2 -> a3
        else -> a4
    }

    fun withAnswer(i: Int, v: Int?): CheckIn = when (i) {
        0 -> copy(a1 = v)
        1 -> copy(a2 = v)
        2 -> copy(a3 = v)
        else -> copy(a4 = v)
    }

    val dayTypeE: DayType? get() = DayType.entries.firstOrNull { it.name == dayType }
    val isEmpty: Boolean get() = a1 == null && a2 == null && a3 == null && a4 == null && dayType == null && note.isBlank()
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
