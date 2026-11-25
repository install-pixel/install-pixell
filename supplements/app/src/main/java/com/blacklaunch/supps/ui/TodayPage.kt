package com.blacklaunch.supps.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.CheckIn
import com.blacklaunch.supps.data.DayStatus
import com.blacklaunch.supps.data.Part
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Sleep
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.data.part
import com.blacklaunch.supps.ui.theme.Oled
import java.time.Instant
import java.time.ZoneId

/** Питання ранкової й вечірньої оцінки (тексти з налаштувань або типові). */
data class Questions(val morning: List<String>, val evening: List<String>) {
    fun of(part: Part): List<String> = if (part == Part.Morning) morning else evening

    /** Усі питання по черзі: ранкові, потім вечірні. */
    val pairs: List<Pair<Part, Int>> get() = morning.indices.map { Part.Morning to it } + evening.indices.map { Part.Evening to it }

    fun label(part: Part, i: Int): String = of(part).getOrElse(i) { "" }
}

/** Добавка на день разом зі станом за схемою. */
data class DayItem(val supplement: Supplement, val status: DayStatus, val taken: Boolean, val takenAt: Long?)

fun dayItems(data: AppData, day: Long): List<DayItem> = data.active.map { s ->
    val intake = data.intake(s.id, day)
    DayItem(s, Schedule.status(s, day, data.groups, data.supplements), intake != null, intake?.at?.takeIf { it > 0 })
}

/** Що показувати в частині дня: заплановане, «за потреби» і вже відмічене. */
fun partItems(items: List<DayItem>, part: Part): List<DayItem> = items.filter {
    it.supplement.slotE.part == part && (it.status.phase == Phase.Active || it.status.phase == Phase.AsNeeded || it.taken)
}.sortedBy { it.supplement.slotE.ordinal }

/** Скільки годин після підйому вважати, що вже вечір (якщо ранок не завершили). */
private const val EVENING_AFTER_WAKE_H = 10

/** Якщо підйому не видно з відміток — вечір з цієї години. */
private const val EVENING_HOUR = 17

/**
 * Головна: два блоки — «Ранок» і «Вечір». Відкритий той, що зараз доречний: ранок, доки його не завершено, далі —
 * вечір. У блоці — що прийняти (дотик — відмітити) і кілька оцінок 1–5; усе зберігається одразу, без кнопок.
 */
@Composable
fun TodayPage(
    data: AppData,
    today: Long,
    now: Long,
    questions: Questions,
    onToggle: (Supplement) -> Unit,
    onRate: (Part, Int, Int?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val locale = appLocale()
    val context = LocalContext.current
    if (data.loaded && data.active.isEmpty()) {
        EmptyToday(onOpenSettings)
        return
    }
    val items = remember(data, today) { dayItems(data, today) }
    val checkIn = data.checkIn(today)
    val morning = partItems(items, Part.Morning)
    val evening = partItems(items, Part.Evening)
    val morningDone = morning.filter { it.status.phase == Phase.Active }.all { it.taken } && (checkIn?.answered(Part.Morning, questions.morning.size) ?: questions.morning.isEmpty())
    val eveningStarted = evening.any { it.taken } || checkIn?.eveningAt != null || (0 until questions.evening.size).any { checkIn?.answer(Part.Evening, it) != null }
    val wakeAt = remember(data, today) { Sleep.wakeAt(today, data.intakes, data.supplements.associate { it.id to it.slotE }, checkIn) }
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val current = when {
        morningDone || eveningStarted -> Part.Evening
        wakeAt != null && now - wakeAt >= EVENING_AFTER_WAKE_H * 3_600_000L -> Part.Evening
        wakeAt == null && hour >= EVENING_HOUR -> Part.Evening
        else -> Part.Morning
    }
    var manual by remember(today) { mutableStateOf<Part?>(null) }
    val expanded = manual ?: current
    val lowStock = remember(data, today) {
        items.mapNotNull { item ->
            val s = item.supplement
            val end = Schedule.runsOutOn(s, today, item.taken, data.groups, data.supplements) ?: return@mapNotNull null
            if (end - today <= 7) s to end else null
        }
    }
    val resting = items.filter { (it.status.phase == Phase.Off || it.status.phase == Phase.NotStarted) && !it.taken }
    val showMorning = morning.isNotEmpty() || questions.morning.isNotEmpty()
    val showEvening = evening.isNotEmpty() || questions.evening.isNotEmpty()

    Column(Modifier.fillMaxSize().bouncyScroll().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            longDate(today, locale).replaceFirstChar { it.titlecase(locale) },
            color = Oled.TextPrimary,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
        lowStock.forEach { (s, end) ->
            Text(
                if (end < today) stringResource(R.string.stock_empty_named, s.name) else stringResource(R.string.stock_low_named, s.name, shortDate(end, locale)),
                color = Oled.Danger,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (showMorning) {
            PartBlock(Part.Morning, morning, checkIn, questions, expanded == Part.Morning, data.supplements, { manual = Part.Morning }, onToggle, onRate)
        }
        if (showEvening) {
            PartBlock(Part.Evening, evening, checkIn, questions, expanded == Part.Evening, data.supplements, { manual = Part.Evening }, onToggle, onRate)
        }
        if (resting.isNotEmpty()) {
            val text = resting.joinToString(" · ") { item ->
                val until = item.status.changeOn?.let { " (" + context.getString(R.string.status_from, shortDate(it, locale)) + ")" }.orEmpty()
                item.supplement.name + until
            }
            Text(stringResource(R.string.today_resting_line, text), color = Oled.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Блок частини дня: заголовок з підсумком; відкритий — добавки й оцінки, згорнутий — один рядок. */
@Composable
private fun PartBlock(
    part: Part,
    items: List<DayItem>,
    checkIn: CheckIn?,
    questions: Questions,
    expanded: Boolean,
    all: List<Supplement>,
    onExpand: () -> Unit,
    onToggle: (Supplement) -> Unit,
    onRate: (Part, Int, Int?) -> Unit,
) {
    val scheduled = items.filter { it.status.phase == Phase.Active }
    val takenCount = scheduled.count { it.taken }
    val qs = questions.of(part)
    val answered = qs.indices.count { checkIn?.answer(part, it) != null }
    val done = takenCount == scheduled.size && answered == qs.size
    val title = stringResource(if (part == Part.Morning) R.string.part_morning else R.string.part_evening)
    val progress = buildString {
        if (scheduled.isNotEmpty()) append("$takenCount/${scheduled.size}")
        if (done) append(if (isEmpty()) "✓" else " ✓")
    }
    Card(seed = if (part == Part.Morning) 101 else 202, selected = expanded, onClick = if (expanded) null else onExpand, modifier = Modifier.animateContentSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Oled.TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(progress, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1)
            }
            if (!expanded) {
                val summary = if (done) {
                    qs.indices.mapNotNull { i -> checkIn?.answer(part, i)?.let { "${qs[i]} $it" } }.joinToString(" · ").ifEmpty { stringResource(R.string.part_done) }
                } else {
                    items.joinToString(" · ") { it.supplement.name }.ifEmpty { qs.joinToString(" · ") }
                }
                Text(summary, color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                if (items.isEmpty()) {
                    Text(stringResource(R.string.part_nothing), color = Oled.TextSecondary, fontSize = 14.sp)
                }
                items.forEach { item -> IntakeRow(item, all, onToggle) }
                qs.forEachIndexed { i, q ->
                    Text(q, color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                    ScoreRow(SCORES, checkIn?.answer(part, i)?.let { it - 1 }) { picked -> onRate(part, i, picked?.let { it + 1 }) }
                }
                if (qs.isNotEmpty()) FieldHint(stringResource(R.string.rate_hint))
            }
        }
    }
}

val SCORES = listOf("1", "2", "3", "4", "5")

/** Рядок «прийняти»: назва, доза, стан або час відмітки; дотик — відмітити / зняти. */
@Composable
fun IntakeRow(item: DayItem, all: List<Supplement>, onToggle: (Supplement) -> Unit) {
    val s = item.supplement
    Card(seed = s.id.toInt(), selected = item.taken, onClick = { onToggle(s) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extra = when {
                    item.takenAt != null -> stringResource(R.string.taken_at, clock(item.takenAt))
                    item.taken -> stringResource(R.string.taken)
                    item.status.phase == Phase.AsNeeded -> stringResource(R.string.plan_as_needed)
                    else -> statusText(item.status, all)
                }
                Text(
                    if (extra.isEmpty()) doseText(s) else doseText(s) + " · " + extra,
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            CheckMark(item.taken)
        }
    }
}

/** Порожньо: одна дорога — в аптечку (там і додавання, і схема JSON). */
@Composable
private fun EmptyToday(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), color = Oled.TextPrimary, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        MenuRow(Glyph.Settings, stringResource(R.string.open_settings), onOpenSettings)
        WindowHint(stringResource(R.string.empty_hint))
    }
}
