package com.blacklaunch.supps.data

/*
 * Статистика: порівняння оцінок у дні з прийомом і без, дотримання схеми, витрати. Чиста логіка — покрита тестами.
 */

/** Середня оцінка питання [question] у дні з прийомом і без (n — скільки днів пораховано). */
data class Compare(val question: Int, val withAvg: Double?, val withN: Int, val withoutAvg: Double?, val withoutN: Int) {
    /** Різниця «з прийомом − без» або null, якщо однієї з груп немає. */
    val delta: Double? get() = if (withAvg != null && withoutAvg != null) withAvg - withoutAvg else null

    /** Достатньо днів в обох групах, щоб говорити про тенденцію. */
    val enough: Boolean get() = withN >= MIN_DAYS && withoutN >= MIN_DAYS

    companion object {
        const val MIN_DAYS = 7
    }
}

object Stats {
    /**
     * Порівняння для добавки: дні з відміткою прийому проти днів без неї (серед днів з оцінкою). [questions] — які
     * питання (ранок/вечір, номер). Вечірня добавка впливає на ранок НАСТУПНОГО дня, тож для ранкових питань у
     * вечірньої добавки [eveningSupplement] береться прийом напередодні.
     */
    fun compare(takenDays: Set<Long>, eveningSupplement: Boolean, checkIns: List<CheckIn>, questions: List<Pair<Part, Int>>): List<Compare> =
        questions.mapIndexed { qi, (part, index) ->
            var withSum = 0.0
            var withN = 0
            var withoutSum = 0.0
            var withoutN = 0
            checkIns.forEach { c ->
                val v = c.answer(part, index) ?: return@forEach
                val day = if (part == Part.Morning && eveningSupplement) c.day - 1 else c.day
                if (day in takenDays) {
                    withSum += v
                    withN++
                } else {
                    withoutSum += v
                    withoutN++
                }
            }
            Compare(qi, if (withN > 0) withSum / withN else null, withN, if (withoutN > 0) withoutSum / withoutN else null, withoutN)
        }

    /** Середня оцінка питання за дні [from]..[to] включно (null — немає оцінок). */
    fun average(checkIns: List<CheckIn>, part: Part, index: Int, from: Long, to: Long): Double? {
        val values = checkIns.filter { it.day in from..to }.mapNotNull { it.answer(part, index) }
        return if (values.isEmpty()) null else values.average()
    }

    /** Середнє всіх відповідей дня (для графіка) або null. */
    fun dayScore(c: CheckIn?, questions: List<Pair<Part, Int>>): Double? {
        c ?: return null
        val values = questions.mapNotNull { (part, i) -> c.answer(part, i) }
        return if (values.isEmpty()) null else values.average()
    }

    /**
     * Дотримання за дні [from]..[to]: (прийнято за планом, заплановано). «За потреби» не рахуються. Добавки, додані
     * пізніше, теж рахуються від першого дня періоду — тому показуємо лише недавні періоди.
     */
    fun adherence(
        supplements: List<Supplement>,
        groups: List<AltGroup>,
        intakesByDay: Map<Long, Set<Long>>,
        from: Long,
        to: Long,
    ): Pair<Int, Int> {
        var taken = 0
        var planned = 0
        val active = supplements.filter { !it.archived && it.planE != Plan.AsNeeded }
        var day = from
        while (day <= to) {
            val ids = intakesByDay[day].orEmpty()
            active.forEach { s ->
                if (Schedule.status(s, day, groups, supplements).phase == Phase.Active) {
                    planned++
                    if (s.id in ids) taken++
                }
            }
            day++
        }
        return taken to planned
    }

    /** Ціна одиниці: з картки добавки, а якщо там невідомо — з останньої покупки з відомою ціною й кількістю. */
    fun unitPrice(s: Supplement, purchases: List<Purchase>): Double? =
        s.unitPrice ?: purchases.lastOrNull { it.supplementId == s.id && it.price != null && (it.units ?: 0) > 0 }
            ?.let { it.price!! / it.units!! }

    /** Скільки коштуватимуть прийоми за [days] днів від [from] (null — ціна невідома). */
    fun forecastCost(s: Supplement, from: Long, days: Int, groups: List<AltGroup>, all: List<Supplement>, purchases: List<Purchase>): Double? {
        val price = unitPrice(s, purchases) ?: return null
        if (s.planE == Plan.AsNeeded) return null
        return Schedule.activeDays(s, from, days, groups, all) * s.unitsPerDose * price
    }

    /** Вартість відмічених прийомів за дні [from]..[to] — для добавок із відомою ціною. */
    fun intakeCost(intakes: List<Intake>, supplements: List<Supplement>, purchases: List<Purchase>, from: Long, to: Long): Double {
        val prices = supplements.associate { it.id to unitPrice(it, purchases) }
        return intakes.filter { it.day in from..to }.sumOf { i -> (prices[i.supplementId] ?: 0.0) * i.units }
    }

    /** Сума покупок за дні [from]..[to] (лише з відомою ціною). */
    fun spent(purchases: List<Purchase>, from: Long, to: Long): Double =
        purchases.filter { it.day in from..to }.sumOf { it.price ?: 0.0 }
}
