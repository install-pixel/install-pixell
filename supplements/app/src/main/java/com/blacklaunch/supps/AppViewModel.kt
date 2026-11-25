package com.blacklaunch.supps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.supps.data.AltGroup
import com.blacklaunch.supps.data.AppDatabase
import com.blacklaunch.supps.data.CheckIn
import com.blacklaunch.supps.data.Intake
import com.blacklaunch.supps.data.JsonIO
import com.blacklaunch.supps.data.Part
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Purchase
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Усі дані програми разом: їх небагато, тож тримаємо в пам'яті й рахуємо на льоту. */
data class AppData(
    val supplements: List<Supplement> = emptyList(),
    val groups: List<AltGroup> = emptyList(),
    val intakes: List<Intake> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val loaded: Boolean = false,
) {
    val active: List<Supplement> get() = supplements.filter { !it.archived }

    /** Хто що прийняв: день → id добавок. */
    val takenByDay: Map<Long, Set<Long>> by lazy { intakes.groupBy { it.day }.mapValues { e -> e.value.map { it.supplementId }.toSet() } }

    fun checkIn(day: Long): CheckIn? = checkIns.firstOrNull { it.day == day }

    fun taken(supplementId: Long, day: Long): Boolean = takenByDay[day]?.contains(supplementId) == true

    private val intakeByKey: Map<Pair<Long, Long>, Intake> by lazy { intakes.associateBy { it.supplementId to it.day } }

    fun intake(supplementId: Long, day: Long): Intake? = intakeByKey[supplementId to day]
}

/** Що повернути після видалення («Повернути»). */
data class Deleted(val supplement: Supplement, val intakes: List<Intake>, val purchases: List<Purchase>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val settings = AppSettings(app)

    val data: StateFlow<AppData> = combine(dao.supplements(), dao.groups(), dao.intakes(), dao.checkIns(), dao.purchases()) { s, g, i, c, p ->
        AppData(s, g, i, c, p, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    private val _today = MutableStateFlow(computeToday())

    /** Сьогоднішній день з урахуванням того, о котрій «починається» день (після нічної зміни). */
    val today: StateFlow<Long> = _today

    private val _now = MutableStateFlow(System.currentTimeMillis())

    /** Поточний час (оновлюється щопівхвилини) — щоб «ранок / вечір» на головній мінявся сам. */
    val now: StateFlow<Long> = _now

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshToday()
            }
        }
    }

    fun refreshToday() {
        _now.value = System.currentTimeMillis()
        val day = computeToday()
        if (day != _today.value) _today.value = day
    }

    private fun computeToday(): Long = LocalDateTime.now().minusHours(settings.dayStartHour.toLong()).toLocalDate().toEpochDay()

    /**
     * Відмітити / зняти прийом. [live] — відмічаєш зараз (з часом, з нього рахується сон); дописане заднім числом
     * часу не має.
     */
    fun toggleIntake(s: Supplement, day: Long, live: Boolean) {
        viewModelScope.launch {
            val existing = dao.intake(s.id, day)
            val fresh = dao.supplement(s.id) ?: return@launch
            if (existing != null) {
                dao.deleteIntake(existing)
                fresh.unitsLeft?.let { dao.upsertSupplement(fresh.copy(unitsLeft = it + existing.units)) }
            } else {
                dao.insertIntake(Intake(supplementId = s.id, day = day, units = fresh.unitsPerDose, at = if (live) System.currentTimeMillis() else 0L))
                fresh.unitsLeft?.let { dao.upsertSupplement(fresh.copy(unitsLeft = (it - fresh.unitsPerDose).coerceAtLeast(0.0))) }
            }
        }
    }

    /**
     * Зберегти добавку. [doneDays] — «скільки днів уже приймаю» (null — не вказано). [newGroupName] — створити нову
     * групу чергування з такою назвою.
     */
    fun saveSupplement(input: Supplement, doneDays: Int?, newGroupName: String?, newGroupBreak: Int, purchase: Purchase? = null) {
        viewModelScope.launch {
            val today = _today.value
            var s = input
            var createdGroup: AltGroup? = null
            if (s.planE == Plan.Group && s.groupId == null) {
                val name = newGroupName?.trim().orEmpty().ifEmpty { s.name }
                val g = AltGroup(name = name.take(80), breakDays = newGroupBreak.coerceIn(0, 3650), startDay = today)
                createdGroup = g.copy(id = dao.insertGroup(g))
                s = s.copy(groupId = createdGroup.id)
            }
            if (s.planE != Plan.Group) s = s.copy(groupId = null)
            if (s.planE == Plan.Cycle) {
                val previous = if (s.id != 0L) dao.supplement(s.id) else null
                s = when {
                    doneDays != null -> s.copy(startDay = Schedule.cycleStartFor(today, doneDays))
                    previous == null || previous.planE != Plan.Cycle -> s.copy(startDay = today)
                    else -> s
                }
            }
            if (s.planE == Plan.Group && s.id == 0L) {
                // Нова в групі — у кінець черги.
                val last = data.value.supplements.filter { it.groupId == s.groupId && it.planE == Plan.Group }.maxOfOrNull { it.groupOrder } ?: -1
                s = s.copy(groupOrder = last + 1)
            }
            if (s.id == 0L) {
                val order = (data.value.supplements.maxOfOrNull { it.sortOrder } ?: -1) + 1
                s = s.copy(id = dao.insertSupplement(s.copy(sortOrder = order)), sortOrder = order)
            } else {
                dao.upsertSupplement(s)
            }
            if (purchase != null) dao.insertPurchase(purchase.copy(supplementId = s.id, day = today))
            if (s.planE == Plan.Group && doneDays != null) {
                val groupId = s.groupId ?: return@launch
                val g = createdGroup ?: data.value.groups.firstOrNull { it.id == groupId } ?: return@launch
                val all = data.value.supplements.filter { it.id != s.id } + s
                val members = Schedule.members(groupId, all)
                dao.upsertGroup(g.copy(startDay = Schedule.groupStartFor(s, members, g.breakDays, today, doneDays)))
            }
        }
    }

    fun deleteSupplement(s: Supplement, onDone: (Deleted) -> Unit) {
        viewModelScope.launch {
            val d = data.value
            val deleted = Deleted(s, d.intakes.filter { it.supplementId == s.id }, d.purchases.filter { it.supplementId == s.id })
            dao.deleteIntakesOf(s.id)
            dao.deletePurchasesOf(s.id)
            dao.deleteSupplement(s)
            onDone(deleted)
        }
    }

    fun restore(d: Deleted) {
        viewModelScope.launch {
            dao.insertSupplements(listOf(d.supplement))
            dao.insertIntakes(d.intakes)
            dao.insertPurchases(d.purchases)
        }
    }

    /**
     * Оцінка: відповідь [value] (1–5 або null — зняти) на питання [index] ранку чи вечора. [live] — відповідаєш зараз:
     * тоді запам'ятовується й час (підйом / відбій для статистики сну).
     */
    fun setRating(day: Long, part: Part, index: Int, value: Int?, live: Boolean) {
        viewModelScope.launch {
            val old = dao.checkIn(day) ?: CheckIn(day = day)
            var c = old.withAnswer(part, index, value)
            if (live && value != null) {
                val now = System.currentTimeMillis()
                c = when (part) {
                    Part.Morning -> if (c.morningAt == null) c.copy(morningAt = now) else c
                    Part.Evening -> if (c.eveningAt == null) c.copy(eveningAt = now) else c
                }
            }
            if (c.isEmpty) dao.deleteCheckIn(day) else dao.upsertCheckIn(c)
        }
    }

    fun saveGroup(g: AltGroup) {
        viewModelScope.launch { dao.upsertGroup(g) }
    }

    /** Розпустити групу: добавки лишаються, але стають щоденними. */
    fun deleteGroup(g: AltGroup) {
        viewModelScope.launch {
            dao.ungroup(g.id)
            dao.deleteGroup(g)
        }
    }

    /** Посунути добавку в черзі групи на [delta] (−1 — раніше, +1 — пізніше). */
    fun moveInGroup(s: Supplement, delta: Int) {
        viewModelScope.launch {
            val groupId = s.groupId ?: return@launch
            val members = Schedule.members(groupId, data.value.supplements).toMutableList()
            val i = members.indexOfFirst { it.id == s.id }
            val j = i + delta
            if (i < 0 || j < 0 || j >= members.size) return@launch
            val item = members.removeAt(i)
            members.add(j, item)
            members.forEachIndexed { index, m -> if (m.groupOrder != index) dao.upsertSupplement(m.copy(groupOrder = index)) }
        }
    }

    /** Додати вибрані добавки зі схеми. */
    fun importSchema(parsed: JsonIO.Parsed, selected: Set<Int>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val today = _today.value
            val existingGroups = data.value.groups
            val groupIds = HashMap<String, Long>()
            var order = (data.value.supplements.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val added = ArrayList<Pair<Supplement, Int>>()
            parsed.items.forEachIndexed { index, item ->
                if (index !in selected) return@forEachIndexed
                var s = item.supplement
                if (s.planE == Plan.Group) {
                    val key = item.groupKey ?: s.name
                    val gid = groupIds[key] ?: run {
                        val pg = parsed.groups.firstOrNull { it.key == key }
                        val name = pg?.name ?: key
                        val found = existingGroups.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        val id = found?.id ?: dao.insertGroup(AltGroup(name = name.take(80), breakDays = pg?.breakDays ?: 0, startDay = today))
                        groupIds[key] = id
                        id
                    }
                    s = s.copy(groupId = gid)
                }
                if (s.planE == Plan.Cycle) s = s.copy(startDay = Schedule.cycleStartFor(today, item.doneDays))
                s = s.copy(sortOrder = order++)
                val id = dao.insertSupplement(s)
                added += s.copy(id = id) to item.doneDays
            }
            // Чергування: черга рахується від того учасника, для якого сказано «вже приймаю N днів».
            val all = data.value.supplements + added.map { it.first }
            groupIds.values.toSet().forEach { gid ->
                val anchor = added.firstOrNull { it.first.groupId == gid && it.second > 0 } ?: return@forEach
                val fresh = existingGroups.firstOrNull { it.id == gid } ?: AltGroup(id = gid, name = "", startDay = today)
                val name = fresh.name.ifEmpty { parsed.groups.firstOrNull { groupIds[it.key] == gid }?.name ?: anchor.first.name }
                val brk = parsed.groups.firstOrNull { groupIds[it.key] == gid }?.breakDays ?: fresh.breakDays
                val members = Schedule.members(gid, all)
                dao.upsertGroup(fresh.copy(name = name, breakDays = brk, startDay = Schedule.groupStartFor(anchor.first, members, brk, today, anchor.second)))
            }
            onDone(added.size)
        }
    }

    fun restoreBackup(b: JsonIO.Backup, onDone: () -> Unit) {
        viewModelScope.launch {
            dao.replaceAll(b.supplements, b.groups, b.intakes, b.checkIns, b.purchases)
            onDone()
        }
    }

    fun exportJson(): String {
        val d = data.value
        return JsonIO.export(d.supplements, d.groups, d.intakes, d.checkIns, d.purchases, _today.value)
    }
}
