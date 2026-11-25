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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.AltGroup
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Stats
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.ui.theme.Oled

/** Аптечка: групи чергування й усі добавки з залишком і вартістю. */
@Composable
fun CabinetPage(
    data: AppData,
    today: Long,
    currency: String,
    onOpen: (Supplement) -> Unit,
    onGroup: (AltGroup) -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
) {
    if (data.loaded && data.active.isEmpty()) {
        EmptyCabinet(onAdd, onPaste)
        return
    }
    val list = rememberLazyListState()
    val groups = data.groups.filter { g -> Schedule.members(g.id, data.supplements).isNotEmpty() }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().fadingEdges(list),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle(stringResource(R.string.tab_cabinet)) }
        if (groups.isNotEmpty()) {
            items(groups, key = { "g" + it.id }) { g -> GroupCard(g, data, today) { onGroup(g) } }
        }
        items(data.active, key = { it.id }) { s -> SupplementCard(s, data, today, currency) { onOpen(s) } }
        item {
            Spacer(Modifier.height(4.dp))
            MenuRow(Glyph.Plus, stringResource(R.string.add_supplement), onAdd)
        }
    }
}

@Composable
private fun GroupCard(g: AltGroup, data: AppData, today: Long, onClick: () -> Unit) {
    val locale = appLocale()
    val members = Schedule.members(g.id, data.supplements)
    Card(seed = 900 + g.id.toInt(), onClick = onClick) {
        Column {
            Text(stringResource(R.string.group_title, g.name), color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            members.forEach { m ->
                val st = Schedule.status(m, today, data.groups, data.supplements)
                val active = st.phase == Phase.Active
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (active) "▸" else "·", color = if (active) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp, modifier = Modifier.width(16.dp))
                    Text(m.name, color = if (active) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    val info = when {
                        active -> stringResource(R.string.status_day_of, st.dayNo, st.length)
                        st.changeOn != null -> stringResource(R.string.status_from, shortDate(st.changeOn, locale))
                        else -> ""
                    }
                    Text(info, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (g.breakDays > 0) Text(plural(R.plurals.break_between, g.breakDays), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SupplementCard(s: Supplement, data: AppData, today: Long, currency: String, onClick: () -> Unit) {
    val locale = appLocale()
    val st = Schedule.status(s, today, data.groups, data.supplements)
    Card(seed = s.id.toInt() + 50, onClick = onClick) {
        Column {
            Text(s.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(doseText(s) + " · " + slotName(s.slotE), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = statusText(st, data.supplements)
            Text(
                if (status.isEmpty() || s.planE == Plan.AsNeeded) planText(s, data.groups) else planText(s, data.groups) + " · " + status,
                color = Oled.TextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stockText(s, data, today), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val month = Stats.forecastCost(s, today, 30, data.groups, data.supplements, data.purchases)
            if (month != null) Text(stringResource(R.string.cost_month, money(month, currency, locale)), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** «Залишок 40 капс. · вистачить до 3 січ.» або «Залишок невідомий». */
@Composable
fun stockText(s: Supplement, data: AppData, today: Long): String {
    val locale = appLocale()
    val left = s.unitsLeft ?: return stringResource(R.string.stock_unknown)
    val base = stringResource(R.string.stock_left, num(left, locale), unitShort(s.formE))
    val end = Schedule.runsOutOn(s, today, data.taken(s.id, today), data.groups, data.supplements) ?: return base
    return base + " · " + if (end < today) stringResource(R.string.stock_empty) else stringResource(R.string.stock_until, shortDate(end, locale))
}

/** Вікно групи чергування: назва, перерва між блоками, порядок черги, розпустити. */
@Composable
fun GroupDialog(
    group: AltGroup,
    data: AppData,
    today: Long,
    onDismiss: () -> Unit,
    onSave: (AltGroup) -> Unit,
    onMove: (Supplement, Int) -> Unit,
    onDelete: (AltGroup) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var brk by remember { mutableStateOf(group.breakDays.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val members = Schedule.members(group.id, data.supplements)
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.group_edit),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(
                stringResource(R.string.save),
                { onSave(group.copy(name = name.trim().ifEmpty { group.name }, breakDays = (parseInt(brk) ?: 0).coerceIn(0, 3650))); onDismiss() },
                tone = Tone.Confirm,
            )
        },
    ) {
        Column(Modifier.bouncyScroll()) {
            LabeledField(stringResource(R.string.group_name), name, { name = it }, placeholder = stringResource(R.string.group_name_hint))
            LabeledField(
                stringResource(R.string.group_break),
                brk,
                { brk = it.filter { c -> c.isDigit() }.take(4) },
                placeholder = "0",
                keyboardType = KeyboardType.Number,
            )
            Text(stringResource(R.string.group_order), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp))
            members.forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${m.name}", color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(plural(R.plurals.days_block, m.onDays.coerceAtLeast(1)), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    SheetButton("↑", { onMove(m, -1) }, enabled = i > 0, compact = true, modifier = Modifier.width(44.dp))
                    Spacer(Modifier.width(6.dp))
                    SheetButton("↓", { onMove(m, 1) }, enabled = i < members.size - 1, compact = true, modifier = Modifier.width(44.dp))
                }
            }
            WindowHint(stringResource(R.string.group_hint))
            Spacer(Modifier.height(8.dp))
            MenuRow(Glyph.Delete, stringResource(R.string.group_delete), { confirmDelete = true }, tone = Tone.Danger)
        }
    }
    if (confirmDelete) {
        SheetDialog(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.group_delete_q),
            buttons = {
                SheetButton(stringResource(R.string.cancel), { confirmDelete = false })
                SheetButton(stringResource(R.string.group_delete_yes), { confirmDelete = false; onDelete(group); onDismiss() }, tone = Tone.Danger)
            },
        ) {
            Text(stringResource(R.string.group_delete_body), color = Oled.TextSecondary, fontSize = 15.sp)
        }
    }
}
