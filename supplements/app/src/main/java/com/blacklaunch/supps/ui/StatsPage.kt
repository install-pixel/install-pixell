package com.blacklaunch.supps.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Stats
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.ui.theme.Oled
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Статистика: дотримання, самопочуття, витрати, календар і перехід до кожної добавки. */
@Composable
fun StatsPage(
    data: AppData,
    today: Long,
    questions: List<String>,
    currency: String,
    onDay: (Long) -> Unit,
    onOpen: (Supplement) -> Unit,
) {
    val locale = appLocale()
    Column(Modifier.fillMaxSize().bouncyScroll().padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.stats_adherence))
        val week = remember(data, today) { Stats.adherence(data.supplements, data.groups, data.takenByDay, today - 6, today) }
        val month = remember(data, today) { Stats.adherence(data.supplements, data.groups, data.takenByDay, today - 29, today) }
        StatLine(stringResource(R.string.stats_7d), percent(week))
        StatLine(stringResource(R.string.stats_30d), percent(month))
        WindowHint(stringResource(R.string.stats_adherence_hint))

        SectionTitle(stringResource(R.string.stats_wellbeing))
        if (data.checkIns.isEmpty()) {
            WindowHint(stringResource(R.string.stats_no_checkins))
        } else {
            questions.forEachIndexed { i, q ->
                val a7 = Stats.average(data.checkIns, i, today - 6, today)
                val a30 = Stats.average(data.checkIns, i, today - 29, today)
                StatLine(q, stringResource(R.string.stats_avg_pair, avg(a7, locale), avg(a30, locale)))
            }
        }

        SectionTitle(stringResource(R.string.stats_costs))
        val first = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
        val spent = Stats.spent(data.purchases, first, today)
        val used = Stats.intakeCost(data.intakes, data.supplements, data.purchases, first, today)
        val forecast = data.active.mapNotNull { Stats.forecastCost(it, today, 30, data.groups, data.supplements, data.purchases) }.sum()
        val unknown = data.active.count { it.planE != Plan.AsNeeded && Stats.unitPrice(it, data.purchases) == null }
        StatLine(stringResource(R.string.stats_spent_month), money(spent, currency, locale))
        StatLine(stringResource(R.string.stats_used_month), money(used, currency, locale))
        StatLine(stringResource(R.string.stats_forecast), money(forecast, currency, locale))
        if (unknown > 0) WindowHint(plural(R.plurals.price_unknown_count, unknown))

        SectionTitle(stringResource(R.string.stats_calendar))
        MonthCalendar(data, today, onDay)

        if (data.active.isNotEmpty()) {
            SectionTitle(stringResource(R.string.stats_by_supplement))
            data.active.forEach { s -> MenuRow(Glyph.Chart, s.name, { onOpen(s) }) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun percent(p: Pair<Int, Int>): String = if (p.second == 0) "—" else "${(p.first * 100f / p.second).roundToInt()}% (${p.first}/${p.second})"

private fun avg(v: Double?, locale: java.util.Locale): String = v?.let { String.format(locale, "%.1f", it) } ?: "—"

@Composable
private fun StatLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Місяць сіткою: яскравість клітинки — частка прийнятого, крапка — є оцінка дня. Дотик — день. */
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
    val weekdays = DayOfWeek.values().toList()
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                color = Oled.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val lead = first.dayOfWeek.value - 1
    val length = first.lengthOfMonth()
    val cells = lead + length
    val rows = (cells + 6) / 7
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
                        val hasCheckIn = data.checkIn(day) != null
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
                            Text(
                                (index + 1).toString(),
                                color = if (future) Oled.TextSecondary.copy(alpha = 0.4f) else Oled.TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                            if (hasCheckIn) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).background(Oled.TextPrimary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
    WindowHint(stringResource(R.string.stats_calendar_hint))
}

/** День з календаря: тип дня й оцінки, відмітки прийому (можна дописати забуте). */
@Composable
fun DayDialog(
    day: Long,
    data: AppData,
    questions: List<String>,
    onDismiss: () -> Unit,
    onToggle: (Supplement) -> Unit,
    onCheckIn: () -> Unit,
) {
    val locale = appLocale()
    val items = dayItems(data, day).filter { it.status.phase == Phase.Active || it.status.phase == Phase.AsNeeded || it.taken }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = longDate(day, locale).replaceFirstChar { it.titlecase(locale) },
        buttons = { SheetButton(stringResource(R.string.close), onDismiss) },
        tall = true,
    ) {
        Column(Modifier.fillMaxSize().bouncyScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val summary = checkInSummary(data.checkIn(day), questions)
            SettingRow(stringResource(R.string.checkin_title), summary.ifEmpty { stringResource(R.string.checkin_none) }, onCheckIn)
            if (items.isEmpty()) {
                WindowHint(stringResource(R.string.day_nothing))
            } else {
                items.forEach { item -> IntakeRow(item, data.supplements, onToggle) { } }
            }
            WindowHint(stringResource(R.string.day_hint))
        }
    }
}
