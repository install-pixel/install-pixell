package com.blacklaunch.supps.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.JsonIO
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.settings.AppLanguage
import com.blacklaunch.supps.settings.AppSettings
import com.blacklaunch.supps.ui.theme.Oled

/** Меню «⋯» головного екрана: усе, що не потрібно щохвилини. */
@Composable
fun MainMenu(
    onDismiss: () -> Unit,
    onCheckIn: () -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    onFile: () -> Unit,
    onSettings: () -> Unit,
) {
    SheetDialog(onDismissRequest = onDismiss, title = null, buttons = { SheetButton(stringResource(R.string.close), onDismiss) }) {
        Column(Modifier.bouncyScroll()) {
            MenuRow(Glyph.Smile, stringResource(R.string.checkin_title), { onDismiss(); onCheckIn() })
            MenuRow(Glyph.Plus, stringResource(R.string.add_supplement), { onDismiss(); onAdd() })
            Text(stringResource(R.string.menu_schema), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
            MenuRow(Glyph.Copy, stringResource(R.string.import_paste), { onDismiss(); onPaste() })
            MenuRow(Glyph.Folder, stringResource(R.string.import_file), { onDismiss(); onFile() })
            Spacer(Modifier.height(6.dp))
            MenuRow(Glyph.Settings, stringResource(R.string.settings), { onDismiss(); onSettings() })
        }
    }
}

/**
 * Перегляд схеми перед додаванням: що вже є (за назвою) — не вибране одразу (THEMES.md, розділ 12). Якщо це повна
 * копія — ще й «Відновити все» (червоне: замінює всі дані).
 */
@Composable
fun ImportDialog(
    parsed: JsonIO.Parsed,
    existing: List<Supplement>,
    onDismiss: () -> Unit,
    onImport: (Set<Int>) -> Unit,
    onRestore: (JsonIO.Backup) -> Unit,
) {
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

/** Налаштування: стиль, мова, екран, день і оцінка, валюта, копія, довідка. */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onLanguageChanged: () -> Unit,
    onChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    var dayStart by remember { mutableIntStateOf(settings.dayStartHour) }
    var currency by remember { mutableStateOf(settings.currency) }
    var count by remember { mutableIntStateOf(settings.questionCount) }
    var version by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<String?>(null) }
    val defaults = defaultQuestions()
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.settings), buttons = { SheetButton(stringResource(R.string.close), onDismiss) }, tall = true) {
        Column(Modifier.fillMaxSize().bouncyScroll()) {
            MenuRow(Glyph.More, stringResource(R.string.help), { picker = "help" })
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
            SectionTitle(stringResource(R.string.settings_day))
            SettingRow(stringResource(R.string.day_start), "%02d:00".format(dayStart), { picker = "dayStart" })
            SettingRow(stringResource(R.string.currency), currency.ifEmpty { "—" }, { picker = "currency" })
            SettingRow(stringResource(R.string.question_count), count.toString(), { picker = "count" })
            androidx.compose.runtime.key(version) {
                for (i in 0 until count) {
                    val text = settings.question(i).ifEmpty { defaults[i] }
                    SettingRow(stringResource(R.string.question_n, i + 1), text, { picker = "q$i" })
                }
            }
            SectionTitle(stringResource(R.string.settings_backup))
            MenuRow(Glyph.Download, stringResource(R.string.backup_export), { onExport() })
            MenuRow(Glyph.Folder, stringResource(R.string.backup_import), { onImport() })
            WindowHint(stringResource(R.string.backup_hint))
        }
    }
    val p = picker
    when {
        p == "help" -> SheetDialog(onDismissRequest = { picker = null }, title = stringResource(R.string.help), buttons = { SheetButton(stringResource(R.string.back), { picker = null }) }, tall = true) {
            Text(stringResource(R.string.help_body), color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxSize().bouncyScroll())
        }
        p == "dayStart" -> OptionsDialog(
            stringResource(R.string.day_start),
            (0..6).map { "%02d:00".format(it) },
            dayStart,
            { picker = null },
            hint = stringResource(R.string.day_start_hint),
        ) {
            dayStart = it
            settings.dayStartHour = it
            onChanged()
        }
        p == "count" -> OptionsDialog(stringResource(R.string.question_count), (1..4).map { it.toString() }, count - 1, { picker = null }) {
            count = it + 1
            settings.questionCount = it + 1
            onChanged()
        }
        p == "currency" -> TextInputDialog(stringResource(R.string.currency), currency, "zł", { picker = null }) {
            currency = it.take(6)
            settings.currency = it
            onChanged()
        }
        p != null && p.startsWith("q") -> {
            val i = p.drop(1).toIntOrNull() ?: 0
            TextInputDialog(
                stringResource(R.string.question_n, i + 1),
                settings.question(i).ifEmpty { defaults.getOrElse(i) { "" } },
                defaults.getOrElse(i) { "" },
                { picker = null },
                hint = stringResource(R.string.question_hint),
            ) {
                settings.setQuestion(i, if (it == defaults.getOrElse(i) { "" }) "" else it)
                version++
                onChanged()
            }
        }
    }
}

/** Типові тексти питань (якщо свій не задано). */
@Composable
fun defaultQuestions(): List<String> = listOf(
    stringResource(R.string.q_default_1),
    stringResource(R.string.q_default_2),
    stringResource(R.string.q_default_3),
    stringResource(R.string.q_default_4),
)

/** Тексти активних питань з урахуванням налаштувань. [version] — щоб перечитати після змін. */
@Composable
fun activeQuestions(version: Int): List<String> {
    val context = LocalContext.current
    val defaults = defaultQuestions()
    return remember(version, defaults) {
        val s = AppSettings(context)
        (0 until s.questionCount).map { i -> s.question(i).ifEmpty { defaults[i] } }
    }
}
