package com.blacklaunch.supps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.CheckIn
import com.blacklaunch.supps.data.DayStatus
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Slot
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.ui.theme.Oled

/** Добавка на день разом зі станом за схемою. */
data class DayItem(val supplement: Supplement, val status: DayStatus, val taken: Boolean)

/** Що в день [day] приймати, що «за потреби» і що на перерві. */
fun dayItems(data: AppData, day: Long): List<DayItem> = data.active.map { s ->
    DayItem(s, Schedule.status(s, day, data.groups, data.supplements), data.taken(s.id, day))
}

/** Короткий підсумок оцінки: «Настрій 4 · Сон 3». */
@Composable
fun checkInSummary(c: CheckIn?, questions: List<String>): String {
    if (c == null) return ""
    val parts = ArrayList<String>()
    c.dayTypeE?.let { parts += dayTypeName(it) }
    questions.forEachIndexed { i, q -> c.answer(i)?.let { parts += "$q $it" } }
    return parts.joinToString(" · ")
}

@Composable
fun TodayPage(
    data: AppData,
    today: Long,
    questions: List<String>,
    currency: String,
    onToggle: (Supplement) -> Unit,
    onOpen: (Supplement) -> Unit,
    onCheckIn: () -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
) {
    val locale = appLocale()
    val items = remember(data, today) { dayItems(data, today) }
    val list = rememberLazyListState()
    if (data.loaded && data.active.isEmpty()) {
        EmptyCabinet(onAdd, onPaste)
        return
    }
    // Банка закінчується за тиждень — попередження червоним (THEMES.md: червоним і попередження).
    val lowStock = remember(data, today) {
        items.mapNotNull { item ->
            val s = item.supplement
            val end = Schedule.runsOutOn(s, today, item.taken, data.groups, data.supplements) ?: return@mapNotNull null
            if (end - today <= 7) s to end else null
        }
    }
    val checkIn = data.checkIn(today)
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().fadingEdges(list),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                longDate(today, locale).replaceFirstChar { it.titlecase(locale) },
                color = Oled.TextPrimary,
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        item {
            Card(seed = 3, onClick = onCheckIn) {
                Column {
                    Text(stringResource(R.string.checkin_title), color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val summary = checkInSummary(checkIn, questions)
                    Text(
                        summary.ifEmpty { stringResource(R.string.checkin_prompt) },
                        color = Oled.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (lowStock.isNotEmpty()) {
            items(lowStock, key = { "low" + it.first.id }) { (s, end) ->
                Text(
                    if (end < today) stringResource(R.string.stock_empty_named, s.name) else stringResource(R.string.stock_low_named, s.name, shortDate(end, locale)),
                    color = Oled.Danger,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        Slot.entries.forEach { slot ->
            val inSlot = items.filter { it.supplement.slotE == slot && (it.status.phase == Phase.Active || (it.status.phase != Phase.AsNeeded && it.taken)) }
            if (inSlot.isNotEmpty()) {
                item(key = "slot$slot") { SectionTitle(slotName(slot)) }
                items(inSlot, key = { "s" + it.supplement.id }) { item -> IntakeRow(item, data.supplements, onToggle, onOpen) }
            }
        }
        val asNeeded = items.filter { it.status.phase == Phase.AsNeeded }
        if (asNeeded.isNotEmpty()) {
            item(key = "asNeeded") { SectionTitle(stringResource(R.string.plan_as_needed)) }
            items(asNeeded, key = { "n" + it.supplement.id }) { item -> IntakeRow(item, data.supplements, onToggle, onOpen) }
        }
        val resting = items.filter { (it.status.phase == Phase.Off || it.status.phase == Phase.NotStarted) && !it.taken }
        if (resting.isNotEmpty()) {
            item(key = "resting") { SectionTitle(stringResource(R.string.today_resting)) }
            items(resting, key = { "r" + it.supplement.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.supplement.name, color = Oled.TextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    Text(statusText(item.status, data.supplements), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Рядок «прийняти»: назва, доза і стан; дотик — відмітити, довге натискання — відкрити добавку. */
@Composable
fun IntakeRow(item: DayItem, all: List<Supplement>, onToggle: (Supplement) -> Unit, onOpen: (Supplement) -> Unit) {
    val s = item.supplement
    Card(seed = s.id.toInt(), selected = item.taken, onClick = { onToggle(s) }, onLongClick = { onOpen(s) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = statusText(item.status, all)
                val line = if (status.isEmpty() || item.status.phase == Phase.AsNeeded) doseText(s) else doseText(s) + " · " + status
                Text(line, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            CheckMark(item.taken)
        }
    }
}

/** Порожня аптечка: що зробити першим. */
@Composable
fun EmptyCabinet(onAdd: () -> Unit, onPaste: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), color = Oled.TextPrimary, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        MenuRow(Glyph.Plus, stringResource(R.string.add_supplement), onAdd)
        MenuRow(Glyph.Copy, stringResource(R.string.import_paste), onPaste)
        WindowHint(stringResource(R.string.empty_hint))
    }
}
