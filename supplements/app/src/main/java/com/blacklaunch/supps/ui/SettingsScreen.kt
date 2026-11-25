package com.blacklaunch.supps.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.AltGroup
import com.blacklaunch.supps.data.JsonIO
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Stats
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.settings.AppLanguage
import com.blacklaunch.supps.settings.AppSettings
import com.blacklaunch.supps.ui.theme.Oled

/**
 * Усе налаштовування в одному місці: аптечка (добавки за категоріями, чергування, банки — у картці добавки), схема
 * JSON і копія, питання оцінки, день і сон, вигляд, довідка.
 */
@Composable
fun SettingsScreen(
    data: AppData,
    today: Long,
    currency: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Supplement) -> Unit,
    onGroup: (AltGroup) -> Unit,
    onPaste: () -> Unit,
    onFile: () -> Unit,
    onExport: () -> Unit,
    onTemplate: () -> Unit,
    onLanguageChanged: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    var dayStart by remember { mutableIntStateOf(settings.dayStartHour) }
    var offset by remember { mutableIntStateOf(settings.sleepOffsetMinutes) }
    var cur by remember { mutableStateOf(settings.currency) }
    var mCount by remember { mutableIntStateOf(settings.questionCount(false)) }
    var eCount by remember { mutableIntStateOf(settings.questionCount(true)) }
    var version by remember { mutableIntStateOf(0) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultsM = listOf(stringResource(R.string.qm_default_1), stringResource(R.string.qm_default_2), stringResource(R.string.qm_default_3))
    val defaultsE = listOf(stringResource(R.string.qe_default_1), stringResource(R.string.qe_default_2), stringResource(R.string.qe_default_3))

    FullPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(Modifier.fillMaxSize().bouncyScroll().padding(horizontal = 16.dp)) {
            MenuRow(Glyph.More, stringResource(R.string.help), { picker = "help" })

            // Аптечка: за категоріями; дотик — картка добавки (там і банка, і цикл, і видалення).
            SectionTitle(stringResource(R.string.cabinet))
            MenuRow(Glyph.Plus, stringResource(R.string.add_supplement), onAdd)
            val byCategory = data.active.groupBy { it.category.trim() }.toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { it.lowercase() })
            byCategory.forEach { (category, list) ->
                Text(
                    category.ifEmpty { stringResource(R.string.category_none) },
                    color = Oled.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { s -> CabinetRow(s, data, today, currency) { onEdit(s) } }
                }
            }
            val groups = data.groups.filter { g -> Schedule.members(g.id, data.supplements).isNotEmpty() }
            if (groups.isNotEmpty()) {
                Text(stringResource(R.string.groups), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g -> GroupCard(g, data, today) { onGroup(g) } }
                }
            }

            SectionTitle(stringResource(R.string.schema_section))
            MenuRow(Glyph.Copy, stringResource(R.string.import_paste), onPaste)
            MenuRow(Glyph.Folder, stringResource(R.string.import_file), onFile)
            MenuRow(Glyph.Download, stringResource(R.string.backup_export), onExport)
            MenuRow(Glyph.Template, stringResource(R.string.template_copy), onTemplate)
            WindowHint(stringResource(R.string.schema_hint))

            SectionTitle(stringResource(R.string.ratings_section))
            androidx.compose.runtime.key(version) {
                SettingRow(stringResource(R.string.questions_morning), mCount.toString(), { picker = "mCount" })
                for (i in 0 until mCount) SettingRow(stringResource(R.string.question_n, i + 1), settings.question(false, i).ifEmpty { defaultsM[i] }, { picker = "qm$i" })
                SettingRow(stringResource(R.string.questions_evening), eCount.toString(), { picker = "eCount" })
                for (i in 0 until eCount) SettingRow(stringResource(R.string.question_n, i + 1), settings.question(true, i).ifEmpty { defaultsE[i] }, { picker = "qe$i" })
            }
            WindowHint(stringResource(R.string.ratings_hint))

            SectionTitle(stringResource(R.string.day_section))
            SettingRow(stringResource(R.string.day_start), clockOfMinutes(dayStart * 60), { picker = "dayStart" })
            SettingRow(stringResource(R.string.sleep_offset), stringResource(R.string.minutes_value, offset), { picker = "offset" })
            SettingRow(stringResource(R.string.currency), cur.ifEmpty { "—" }, { picker = "currency" })
            WindowHint(stringResource(R.string.day_section_hint))

            SectionTitle(stringResource(R.string.settings_style))
            ChoiceRow(stringResource(R.string.style_glass), stringResource(R.string.style_glass_desc), selected = AppStyle.glass) {
                AppStyle.glass = true
                settings.glass = true
            }
            ChoiceRow(stringResource(R.string.style_sheet), stringResource(R.string.style_sheet_desc), selected = !AppStyle.glass) {
                AppStyle.glass = false
                settings.glass = false
            }
            SectionTitle(stringResource(R.string.settings_language))
            AppLanguage.entries.forEach { lang ->
                val title = when (lang) {
                    AppLanguage.System -> stringResource(R.string.lang_system)
                    AppLanguage.Ukrainian -> "Українська"
                    AppLanguage.Polish -> "Polski"
                    AppLanguage.English -> "English"
                }
                ChoiceRow(title, null, selected = language == lang) {
                    if (language != lang) {
                        language = lang
                        settings.language = lang
                        onLanguageChanged()
                    }
                }
            }
            SectionTitle(stringResource(R.string.settings_screen))
            Column(Modifier.padding(horizontal = 8.dp)) {
                SwitchRow(stringResource(R.string.status_hide_title), StatusBar.hidden, subtitle = stringResource(R.string.status_hide_desc)) {
                    StatusBar.hidden = it
                    settings.hideStatusBar = it
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val p = picker
    when {
        p == "help" -> SheetDialog(onDismissRequest = { picker = null }, title = stringResource(R.string.help), buttons = { SheetButton(stringResource(R.string.close), { picker = null }) }, tall = true) {
            Text(stringResource(R.string.help_body), color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxSize().bouncyScroll())
        }
        p == "dayStart" -> OptionsDialog(stringResource(R.string.day_start), (0..6).map { clockOfMinutes(it * 60) }, dayStart, { picker = null }, hint = stringResource(R.string.day_start_hint)) {
            dayStart = it
            settings.dayStartHour = it
            onChanged()
        }
        p == "offset" -> {
            val options = listOf(0, 15, 30, 45, 60, 90, 120)
            OptionsDialog(stringResource(R.string.sleep_offset), options.map { m -> context.getString(R.string.minutes_value, m) }, options.indexOf(offset).coerceAtLeast(0), { picker = null }, hint = stringResource(R.string.sleep_offset_hint)) {
                offset = options[it]
                settings.sleepOffsetMinutes = options[it]
                onChanged()
            }
        }
        p == "currency" -> TextInputDialog(stringResource(R.string.currency), cur, "zł", { picker = null }) {
            cur = it.take(6)
            settings.currency = it
            onChanged()
        }
        p == "mCount" || p == "eCount" -> {
            val evening = p == "eCount"
            OptionsDialog(stringResource(if (evening) R.string.questions_evening else R.string.questions_morning), (0..3).map { it.toString() }, if (evening) eCount else mCount, { picker = null }) {
                if (evening) eCount = it else mCount = it
                settings.setQuestionCount(evening, it)
                onChanged()
            }
        }
        p != null && (p.startsWith("qm") || p.startsWith("qe")) -> {
            val evening = p.startsWith("qe")
            val i = p.drop(2).toIntOrNull()?.coerceIn(0, 2) ?: 0
            val def = (if (evening) defaultsE else defaultsM)[i]
            TextInputDialog(stringResource(R.string.question_n, i + 1), settings.question(evening, i).ifEmpty { def }, def, { picker = null }, hint = stringResource(R.string.question_hint)) {
                settings.setQuestion(evening, i, if (it == def) "" else it)
                version++
                onChanged()
            }
        }
    }
}

/** Добавка в аптечці: доза й час, схема й стан, залишок. */
@Composable
private fun CabinetRow(s: Supplement, data: AppData, today: Long, currency: String, onClick: () -> Unit) {
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
            val month = Stats.forecastCost(s, today, 30, data.groups, data.supplements, data.purchases)
            val stock = stockText(s, data, today)
            Text(if (month != null) stock + " · " + stringResource(R.string.cost_month, money(month, currency, locale)) else stock, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    val changeOn = st.changeOn
                    val info = when {
                        active -> stringResource(R.string.status_day_of, st.dayNo, st.length)
                        changeOn != null -> stringResource(R.string.status_from, shortDate(changeOn, locale))
                        else -> ""
                    }
                    Text(info, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (g.breakDays > 0) Text(plural(R.plurals.break_between, g.breakDays), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
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
fun GroupDialog(group: AltGroup, data: AppData, onDismiss: () -> Unit, onSave: (AltGroup) -> Unit, onMove: (Supplement, Int) -> Unit, onDelete: (AltGroup) -> Unit) {
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
            LabeledField(stringResource(R.string.group_break), brk, { brk = it.filter { c -> c.isDigit() }.take(4) }, placeholder = "0", keyboardType = KeyboardType.Number)
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

/**
 * Перегляд схеми перед додаванням: те, що вже є (за назвою), не вибране одразу (THEMES.md, розділ 12). Повна копія —
 * ще й «Відновити все» (червоне: замінює всі дані).
 */
@Composable
fun ImportDialog(parsed: JsonIO.Parsed, existing: List<Supplement>, onDismiss: () -> Unit, onImport: (Set<Int>) -> Unit, onRestore: (JsonIO.Backup) -> Unit) {
    val names = existing.map { it.name.trim().lowercase() }.toSet()
    var selected by remember { mutableStateOf(parsed.items.indices.filter { parsed.items[it].supplement.name.trim().lowercase() !in names }.toSet()) }
    var confirmRestore by remember { mutableStateOf(false) }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.import_title),
        tall = true,
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.import_add, selected.size), { onImport(selected); onDismiss() }, tone = Tone.Confirm, enabled = selected.isNotEmpty())
        },
    ) {
        Column(Modifier.fillMaxSize().bouncyScroll()) {
            parsed.items.forEachIndexed { i, item ->
                val s = item.supplement
                val duplicate = s.name.trim().lowercase() in names
                val group = item.groupKey?.let { key -> parsed.groups.firstOrNull { it.key == key }?.name ?: key }
                val schedule = when (s.planE) {
                    Plan.Cycle -> stringResource(R.string.plan_cycle_short, s.onDays, s.offDays)
                    Plan.Group -> stringResource(R.string.plan_group_short, group.orEmpty())
                    else -> planName(s.planE)
                }
                val sub = (if (duplicate) stringResource(R.string.import_exists) + " · " else "") + doseText(s) + " · " + schedule
                ChoiceRow(s.name, sub, selected = i in selected) {
                    selected = if (i in selected) selected - i else selected + i
                }
            }
            WindowHint(stringResource(R.string.import_hint))
            if (parsed.backup != null) {
                Spacer(Modifier.height(8.dp))
                MenuRow(Glyph.Undo, stringResource(R.string.restore_all), { confirmRestore = true }, tone = Tone.Danger)
            }
        }
    }
    val backup = parsed.backup
    if (confirmRestore && backup != null) {
        SheetDialog(
            onDismissRequest = { confirmRestore = false },
            title = stringResource(R.string.restore_q),
            buttons = {
                SheetButton(stringResource(R.string.cancel), { confirmRestore = false })
                SheetButton(stringResource(R.string.restore_yes), { confirmRestore = false; onRestore(backup); onDismiss() }, tone = Tone.Danger)
            },
        ) {
            Text(stringResource(R.string.restore_body, backup.supplements.size, backup.intakes.size), color = Oled.TextSecondary, fontSize = 15.sp)
        }
    }
}

/** Тексти активних питань з налаштувань. [version] — щоб перечитати після змін. */
@Composable
fun activeQuestions(version: Int): Questions {
    val context = LocalContext.current
    val dm = listOf(stringResource(R.string.qm_default_1), stringResource(R.string.qm_default_2), stringResource(R.string.qm_default_3))
    val de = listOf(stringResource(R.string.qe_default_1), stringResource(R.string.qe_default_2), stringResource(R.string.qe_default_3))
    return remember(version, dm, de) {
        val s = AppSettings(context)
        Questions(
            morning = (0 until s.questionCount(false)).map { i -> s.question(false, i).ifEmpty { dm[i] } },
            evening = (0 until s.questionCount(true)).map { i -> s.question(true, i).ifEmpty { de[i] } },
        )
    }
}
