package com.blacklaunch.supps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.Compare
import com.blacklaunch.supps.data.Part
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Sleep
import com.blacklaunch.supps.data.Stats
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.data.part
import com.blacklaunch.supps.ui.theme.Oled
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Скільки днів показує графік у вікні добавки. */
private const val CHART_DAYS = 60

/**
 * Історія: сон (з часу відміток), самопочуття, дотримання, витрати, календар і коротко про кожну добавку.
 */
@Composable
fun HistoryPage(
    data: AppData,
    today: Long,
    questions: Questions,
    currency: String,
    sleepOffset: Int,
    onDay: (Long) -> Unit,
    onSupplement: (Supplement) -> Unit,
) {
    val locale = appLocale()
    val zone = ZoneId.systemDefault()
    val nights = remember(data, today, sleepOffset) { Sleep.nights(today - 29, today, data.intakes, data.supplements, data.checkIns, sleepOffset) }
    Column(Modifier.fillMaxSize().bouncyScroll().padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.history_sleep))
        val week = nights.filter { it.day > today - 7 }
        val sleep7 = week.mapNotNull { it.minutes }
        val sleep30 = nights.mapNotNull { it.minutes }
        val wake7 = Sleep.averageClock(week.mapNotNull { it.wakeAt }, zone, 0)
        val bed7 = Sleep.averageClock(week.mapNotNull { it.bedBeforeAt }, zone, 12)
        if (sleep30.isEmpty() && wake7 == null && bed7 == null) {
            WindowHint(stringResource(R.string.history_sleep_empty))
        } else {
            StatLine(stringResource(R.string.history_sleep_7), sleep7.takeIf { it.isNotEmpty() }?.let { durationText(it.average().roundToInt()) } ?: "—")
            StatLine(stringResource(R.string.history_sleep_30), sleep30.takeIf { it.isNotEmpty() }?.let { durationText(it.average().roundToInt()) } ?: "—")
            StatLine(stringResource(R.string.history_wake), wake7?.let { clockOfMinutes(it) } ?: "—")
            StatLine(stringResource(R.string.history_bed), bed7?.let { clockOfMinutes(it) } ?: "—")
            WindowHint(stringResource(R.string.history_sleep_hint))
        }

        SectionTitle(stringResource(R.string.history_wellbeing))
        if (data.checkIns.isEmpty() || questions.pairs.isEmpty()) {
            WindowHint(stringResource(R.string.history_no_ratings))
        } else {
            questions.pairs.forEach { (part, i) ->
                val a7 = Stats.average(data.checkIns, part, i, today - 6, today)
                val a30 = Stats.average(data.checkIns, part, i, today - 29, today)
                StatLine(partLabel(part) + " · " + questions.label(part, i), stringResource(R.string.history_avg_pair, avg(a7, locale), avg(a30, locale)))
            }
        }

        SectionTitle(stringResource(R.string.history_adherence))
        val adh7 = remember(data, today) { Stats.adherence(data.supplements, data.groups, data.takenByDay, today - 6, today) }
        val adh30 = remember(data, today) { Stats.adherence(data.supplements, data.groups, data.takenByDay, today - 29, today) }
        StatLine(stringResource(R.string.history_7d), percent(adh7))
        StatLine(stringResource(R.string.history_30d), percent(adh30))

        SectionTitle(stringResource(R.string.history_costs))
        val first = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
        val spent = Stats.spent(data.purchases, first, today)
        val forecast = data.active.mapNotNull { Stats.forecastCost(it, today, 30, data.groups, data.supplements, data.purchases) }.sum()
        val unknown = data.active.count { it.planE != Plan.AsNeeded && Stats.unitPrice(it, data.purchases) == null }
        StatLine(stringResource(R.string.history_spent_month), money(spent, currency, locale))
        StatLine(stringResource(R.string.history_forecast), money(forecast, currency, locale))
        if (unknown > 0) WindowHint(plural(R.plurals.price_unknown_count, unknown))

        SectionTitle(stringResource(R.string.history_calendar))
        MonthCalendar(data, today, onDay)

        if (data.active.isNotEmpty()) {
            SectionTitle(stringResource(R.string.history_by_supplement))
            data.active.forEach { s ->
                val takenDays = data.intakes.count { it.supplementId == s.id && it.day > today - 30 }
                SettingRow(s.name, stringResource(R.string.history_taken_30, takenDays), { onSupplement(s) })
            }
            WindowHint(stringResource(R.string.history_by_supplement_hint))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun partLabel(part: Part): String = stringResource(if (part == Part.Morning) R.string.part_morning else R.string.part_evening)

private fun percent(p: Pair<Int, Int>): String = if (p.second == 0) "—" else "${(p.first * 100f / p.second).roundToInt()}% (${p.first}/${p.second})"

private fun avg(v: Double?, locale: Locale): String = v?.let { String.format(locale, "%.1f", it) } ?: "—"

@Composable
private fun StatLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Місяць сіткою: яскравість — частка прийнятого, крапка — є оцінка. Дотик — день. */
@Composable
private fun MonthCalendar(data: AppData, today: Long, onDay: (Long) -> Unit) {
    val locale = appLocale()
    var monthStart by rememberSaveable { mutableLongStateOf(LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()) }
    val first = LocalDate.ofEpochDay(monthStart)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SheetButton("‹", { monthStart = first.minusMonths(1).toEpochDay() }, compact = true, modifier = Modifier.size(width = 48.dp, height = 40.dp))
        Text(monthTitle(first, locale), color = Oled.TextPrimary, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SheetButton(
            "›",
            { monthStart = first.plusMonths(1).toEpochDay() },
            compact = true,
            enabled = first.plusMonths(1).toEpochDay() <= today,
            modifier = Modifier.size(width = 48.dp, height = 40.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.values().forEach { d ->
            Text(d.getDisplayName(TextStyle.SHORT_STANDALONE, locale), color = Oled.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
    val lead = first.dayOfWeek.value - 1
    val length = first.lengthOfMonth()
    val rows = (lead + length + 6) / 7
    val ratios = remember(data, monthStart) {
        (0 until length).associate { i ->
            val day = monthStart + i
            val planned = data.active.filter { it.planE != Plan.AsNeeded && Schedule.status(it, day, data.groups, data.supplements).phase == Phase.Active }
            val taken = planned.count { data.taken(it.id, day) }
            day to if (planned.isEmpty()) -1f else taken / planned.size.toFloat()
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val index = r * 7 + c - lead
                    if (index < 0 || index >= length) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = monthStart + index
                        val future = day > today
                        val ratio = ratios[day] ?: -1f
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (ratio > 0f && !future) Color.White.copy(alpha = 0.05f + 0.22f * ratio) else Color.Transparent)
                                .pencilFrame(10.dp, seed = index, ink = if (day == today) SelectedInk else SheetInk, alpha = if (day == today) 0.95f else 0.25f)
                                .clickable(enabled = !future) { onDay(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((index + 1).toString(), color = if (future) Oled.TextSecondary.copy(alpha = 0.4f) else Oled.TextPrimary, fontSize = 14.sp, maxLines = 1)
                            if (data.checkIn(day) != null) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).background(Oled.TextPrimary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
    WindowHint(stringResource(R.string.history_calendar_hint))
}

/** День з календаря: сон, що прийнято й коли, оцінки ранку й вечора — усе можна поправити. */
@Composable
fun DayDialog(
    day: Long,
    today: Long,
    data: AppData,
    questions: Questions,
    sleepOffset: Int,
    onDismiss: () -> Unit,
    onToggle: (Supplement) -> Unit,
    onRate: (Part, Int, Int?) -> Unit,
) {
    val locale = appLocale()
    val items = dayItems(data, day)
    val night = remember(data, day, sleepOffset) { Sleep.nights(day, day, data.intakes, data.supplements, data.checkIns, sleepOffset).first() }
    val checkIn = data.checkIn(day)
    SheetDialog(
        onDismissRequest = onDismiss,
        title = longDate(day, locale).replaceFirstChar { it.titlecase(locale) },
        buttons = { SheetButton(stringResource(R.string.close), onDismiss) },
        tall = true,
    ) {
        Column(Modifier.fillMaxSize().bouncyScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sleepLine = listOfNotNull(
                night.minutes?.let { stringResource(R.string.day_sleep, durationText(it)) },
                night.wakeAt?.let { stringResource(R.string.day_wake, clock(it)) },
            ).joinToString(" · ")
            if (sleepLine.isNotEmpty()) Text(sleepLine, color = Oled.TextSecondary, fontSize = 14.sp)
            listOf(Part.Morning, Part.Evening).forEach { part ->
                val list = partItems(items, part)
                val qs = questions.of(part)
                if (list.isNotEmpty() || qs.isNotEmpty()) {
                    Text(partLabel(part), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { item -> IntakeRow(item, data.supplements, onToggle) }
                    qs.forEachIndexed { i, q ->
                        Text(q, color = Oled.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
                        ScoreRow(SCORES, checkIn?.answer(part, i)?.let { it - 1 }) { picked -> onRate(part, i, picked?.let { it + 1 }) }
                    }
                }
            }
            if (day != today) WindowHint(stringResource(R.string.day_past_hint))
        }
    }
}

/** Добавка в історії: як почуваєшся в дні з нею і без, графік. */
@Composable
fun EffectDialog(s: Supplement, data: AppData, today: Long, questions: Questions, onDismiss: () -> Unit) {
    val locale = appLocale()
    val takenDays = remember(data, s.id) { data.intakes.filter { it.supplementId == s.id }.map { it.day }.toSet() }
    val evening = s.slotE.part == Part.Evening
    val compare = remember(takenDays, data.checkIns, questions) { Stats.compare(takenDays, evening, data.checkIns, questions.pairs) }
    SheetDialog(onDismissRequest = onDismiss, title = s.name, buttons = { SheetButton(stringResource(R.string.close), onDismiss) }, tall = true) {
        Column(Modifier.fillMaxSize().bouncyScroll()) {
            Text(stringResource(R.string.history_taken_30, data.intakes.count { it.supplementId == s.id && it.day > today - 30 }), color = Oled.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            if (questions.pairs.isEmpty()) {
                WindowHint(stringResource(R.string.history_no_ratings))
            }
            questions.pairs.forEachIndexed { qi, (part, i) ->
                CompareBlock(partLabel(part) + " · " + questions.label(part, i), compare.getOrNull(qi), locale)
            }
            ScoreChart(data, s.id, today, questions)
            WindowHint(stringResource(R.string.effect_hint))
            if (evening) WindowHint(stringResource(R.string.effect_evening_hint))
        }
    }
}

@Composable
private fun CompareBlock(question: String, c: Compare?, locale: Locale) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 8.dp)) {
        Text(question, color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c == null || (c.withN == 0 && c.withoutN == 0)) {
            Text(stringResource(R.string.compare_none), color = Oled.TextSecondary, fontSize = 13.sp)
        } else {
            CompareLines(c, locale)
        }
    }
}

@Composable
private fun CompareLines(c: Compare, locale: Locale) {
    Column {
        val with = c.withAvg?.let { String.format(locale, "%.1f", it) } ?: "—"
        val without = c.withoutAvg?.let { String.format(locale, "%.1f", it) } ?: "—"
        Text(stringResource(R.string.compare_with, with, c.withN), color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.compare_without, without, c.withoutN), color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val delta = c.delta
        val verdict = when {
            !c.enough -> stringResource(R.string.compare_few, Compare.MIN_DAYS)
            delta == null -> ""
            delta >= 0.3 -> stringResource(R.string.compare_better, String.format(locale, "%+.1f", delta))
            delta <= -0.3 -> stringResource(R.string.compare_worse, String.format(locale, "%+.1f", delta))
            else -> stringResource(R.string.compare_same)
        }
        if (verdict.isNotEmpty()) Text(verdict, color = Oled.TextPrimary.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Стовпчики-тіні — дні з прийомом, лінія — середня оцінка дня (1–5). */
@Composable
private fun ScoreChart(data: AppData, supplementId: Long, today: Long, questions: Questions) {
    val from = today - CHART_DAYS + 1
    val pairs = questions.pairs
    val scores = remember(data, today, questions) { (0 until CHART_DAYS).map { i -> Stats.dayScore(data.checkIn(from + i), pairs) } }
    val taken = remember(data, supplementId, today) { (0 until CHART_DAYS).map { i -> data.taken(supplementId, from + i) } }
    val ink = Oled.TextPrimary
    Text(stringResource(R.string.effect_chart, CHART_DAYS), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .pencilFrame(14.dp, seed = 61, alpha = 0.5f)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        val w = size.width
        val h = size.height
        val step = w / CHART_DAYS
        taken.forEachIndexed { i, t ->
            if (t) drawRect(ink.copy(alpha = 0.10f), topLeft = Offset(i * step, 0f), size = Size(step * 0.9f, h))
        }
        listOf(1f, 3f, 5f).forEach { v ->
            val y = h - (v - 1f) / 4f * h
            drawLine(ink.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        var prev: Offset? = null
        scores.forEachIndexed { i, v ->
            if (v == null) {
                prev = null
            } else {
                val p = Offset(i * step + step / 2, h - ((v.toFloat() - 1f) / 4f) * h)
                prev?.let { drawLine(ink.copy(alpha = 0.7f), it, p, 2.dp.toPx(), cap = StrokeCap.Round) }
                drawCircle(ink, 2.6.dp.toPx(), p)
                prev = p
            }
        }
    }
}
