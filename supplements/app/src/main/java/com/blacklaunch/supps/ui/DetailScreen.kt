package com.blacklaunch.supps.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.Compare
import com.blacklaunch.supps.data.DayType
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Stats
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.ui.theme.Oled
import java.util.Locale

/** Скільки днів показує графік на сторінці добавки. */
private const val CHART_DAYS = 60

/**
 * Сторінка добавки: стан за схемою, залишок, вартість і порівняння самопочуття в дні з прийомом і без.
 */
@Composable
fun DetailScreen(
    s: Supplement,
    data: AppData,
    today: Long,
    questions: List<String>,
    currency: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPurchase: (Int?, Double?, Boolean) -> Unit,
    onStock: (Double?) -> Unit,
) {
    val locale = appLocale()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf<DayType?>(null) }
    val st = Schedule.status(s, today, data.groups, data.supplements)
    val takenDays = remember(data, s.id) { data.intakes.filter { it.supplementId == s.id }.map { it.day }.toSet() }
    val compare = remember(takenDays, data.checkIns, questions.size, filter) { Stats.compare(takenDays, data.checkIns, questions.size, filter) }

    FullPage(
        title = s.name,
        onBack = onBack,
        actions = {
            SheetButton(stringResource(R.string.edit), onEdit)
            RoundButton(Glyph.More, stringResource(R.string.more), { dialog = "menu" }, size = 44.dp)
        },
    ) {
        Column(Modifier.fillMaxSize().bouncyScroll().padding(horizontal = 16.dp)) {
            Text(doseText(s) + " · " + slotName(s.slotE), color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            Text(planText(s, data.groups), color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))

            SectionTitle(stringResource(R.string.detail_today))
            val phaseText = when (st.phase) {
                Phase.Active -> stringResource(R.string.detail_take_today)
                Phase.Off, Phase.NotStarted -> stringResource(R.string.detail_rest_today)
                Phase.AsNeeded -> stringResource(R.string.plan_as_needed)
            }
            InfoLine(phaseText)
            val status = statusText(st, data.supplements)
            if (status.isNotEmpty() && st.phase != Phase.AsNeeded) InfoLine(status)
            if (data.taken(s.id, today)) InfoLine(stringResource(R.string.detail_taken_today))

            SectionTitle(stringResource(R.string.detail_stock))
            InfoLine(stockText(s, data, today))
            val month = Stats.forecastCost(s, today, 30, data.groups, data.supplements, data.purchases)
            InfoLine(if (month != null) stringResource(R.string.cost_month, money(month, currency, locale)) else stringResource(R.string.cost_unknown))
            val spent = data.purchases.filter { it.supplementId == s.id }.sumOf { it.price ?: 0.0 }
            if (spent > 0) InfoLine(stringResource(R.string.detail_spent, money(spent, currency, locale)))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(stringResource(R.string.purchase), { dialog = "purchase" })
                SheetButton(stringResource(R.string.set_stock), { dialog = "stock" })
            }

            SectionTitle(stringResource(R.string.detail_effect))
            val filters = listOf<DayType?>(null, DayType.Shift1, DayType.Shift2, DayType.Off)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { f ->
                    ScoreChip(
                        if (f == null) stringResource(R.string.filter_all) else dayTypeShort(f),
                        selected = filter == f,
                        modifier = Modifier.weight(1f),
                    ) { filter = f }
                }
            }
            Spacer(Modifier.height(8.dp))
            questions.forEachIndexed { i, q -> CompareBlock(q, compare.getOrNull(i), locale) }
            WindowHint(stringResource(R.string.detail_effect_hint))

            SectionTitle(stringResource(R.string.detail_chart, CHART_DAYS))
            ScoreChart(data, s.id, today, questions.size)
            WindowHint(stringResource(R.string.detail_chart_hint))

            if (s.note.isNotBlank()) {
                SectionTitle(stringResource(R.string.edit_note_section))
                InfoLine(s.note, maxLines = 6)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "menu" -> SheetDialog(onDismissRequest = { dialog = null }, title = s.name, buttons = { SheetButton(stringResource(R.string.close), { dialog = null }) }) {
            Column {
                MenuRow(Glyph.Edit, stringResource(R.string.edit), { dialog = null; onEdit() })
                MenuRow(Glyph.Download, stringResource(R.string.purchase), { dialog = "purchase" })
                MenuRow(Glyph.Pill, stringResource(R.string.set_stock), { dialog = "stock" })
                MenuRow(Glyph.Delete, stringResource(R.string.delete), { dialog = "delete" }, tone = Tone.Danger)
            }
        }
        "delete" -> SheetDialog(
            onDismissRequest = { dialog = null },
            title = stringResource(R.string.delete_q, s.name),
            buttons = {
                SheetButton(stringResource(R.string.cancel), { dialog = null })
                SheetButton(stringResource(R.string.delete), { dialog = null; onDelete() }, tone = Tone.Danger)
            },
        ) { Text(stringResource(R.string.delete_body), color = Oled.TextSecondary, fontSize = 15.sp) }
        "purchase" -> PurchaseDialog(s, currency, { dialog = null }, onPurchase)
        "stock" -> TextInputDialog(
            title = stringResource(R.string.set_stock),
            initial = s.unitsLeft?.let { num(it, locale) }.orEmpty(),
            placeholder = stringResource(R.string.unknown),
            onDismiss = { dialog = null },
            keyboardType = KeyboardType.Decimal,
            hint = stringResource(R.string.set_stock_hint, unitShort(s.formE)),
        ) { onStock(parseNumber(it)) }
    }
}

@Composable
private fun InfoLine(text: String, maxLines: Int = 2) {
    Text(text, color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 15.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
fun dayTypeShort(t: DayType): String = stringResource(
    when (t) {
        DayType.Shift1 -> R.string.day_shift1_short
        DayType.Shift2 -> R.string.day_shift2_short
        DayType.Off -> R.string.day_off_short
        DayType.Other -> R.string.day_other
    },
)

/** Порівняння по одному питанню: «з прийомом 4,2 (12 дн.) · без 3,6 (9 дн.) · +0,6». */
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

/**
 * Графік за останні дні: стовпчики-тіні — дні з прийомом, крапки з лінією — середня оцінка дня (1–5).
 */
@Composable
private fun ScoreChart(data: AppData, supplementId: Long, today: Long, questions: Int) {
    val from = today - CHART_DAYS + 1
    val scores = remember(data, today, questions) {
        (0 until CHART_DAYS).map { i -> Stats.dayScore(data.checkIn(from + i), questions) }
    }
    val taken = remember(data, supplementId, today) { (0 until CHART_DAYS).map { i -> data.taken(supplementId, from + i) } }
    val ink = Oled.TextPrimary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pencilFrame(14.dp, seed = 61, alpha = 0.5f)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        val w = size.width
        val h = size.height
        val step = w / CHART_DAYS
        // Дні з прийомом — тьмяні стовпчики на всю висоту.
        taken.forEachIndexed { i, t ->
            if (t) drawRect(ink.copy(alpha = 0.10f), topLeft = Offset(i * step, 0f), size = Size(step * 0.9f, h))
        }
        // Допоміжні лінії 1, 3, 5.
        listOf(1f, 3f, 5f).forEach { v ->
            val y = h - (v - 1f) / 4f * h
            drawLine(ink.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        var prev: Offset? = null
        scores.forEachIndexed { i, v ->
            if (v == null) {
                prev = null
                return@forEachIndexed
            }
            val p = Offset(i * step + step / 2, h - ((v.toFloat() - 1f) / 4f) * h)
            prev?.let { drawLine(ink.copy(alpha = 0.7f), it, p, 2.dp.toPx(), cap = StrokeCap.Round) }
            drawCircle(ink, 2.6.dp.toPx(), p)
            prev = p
        }
    }
}

/** Купив банку: кількість, ціна, додати до залишку чи почати з нової. */
@Composable
fun PurchaseDialog(s: Supplement, currency: String, onDismiss: () -> Unit, onApply: (Int?, Double?, Boolean) -> Unit) {
    var units by remember { mutableStateOf(s.packUnits?.toString().orEmpty()) }
    var price by remember { mutableStateOf(s.packPrice?.let { num(it) }.orEmpty()) }
    var add by remember { mutableStateOf(true) }
    val unit = unitShort(s.formE)
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.purchase_title, s.name),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.save), { onApply(parseInt(units)?.takeIf { it > 0 }, parseNumber(price), add); onDismiss() }, tone = Tone.Confirm)
        },
    ) {
        Column(Modifier.bouncyScroll()) {
            LabeledField(stringResource(R.string.edit_pack, unit), units, { units = it.filter { c -> c.isDigit() }.take(6) }, placeholder = stringResource(R.string.unknown), keyboardType = KeyboardType.Number)
            LabeledField(stringResource(R.string.edit_price, currency), price, { price = it }, placeholder = stringResource(R.string.unknown), keyboardType = KeyboardType.Decimal)
            if (s.unitsLeft != null) {
                Spacer(Modifier.height(6.dp))
                SwitchRow(stringResource(R.string.purchase_add), add, subtitle = stringResource(R.string.purchase_add_desc)) { add = it }
            } else {
                WindowHint(stringResource(R.string.purchase_unknown_stock))
            }
        }
    }
}
