package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import com.blacklaunch.launcher.commands.CommandProblemKind
import com.blacklaunch.launcher.commands.CommandProblem
import com.blacklaunch.launcher.commands.CommandCheck
import androidx.compose.material.icons.filled.Notifications
import com.blacklaunch.launcher.commands.StepTexts
import com.blacklaunch.launcher.commands.StepCommands
import com.blacklaunch.launcher.commands.StepCommand
import com.blacklaunch.launcher.commands.CommandReverse
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Call
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.commands.CommandCondition
import com.blacklaunch.launcher.commands.CommandStep
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser

/**
 * «Мої команди»: фраза (голосом чи текстом), «Лише якщо» (що має бути підключено), «Запускати саму»
 * (подія) → кроки по черзі. Кроки збираються з готових блоків (програма, дія, режим); кожен зберігається
 * звичайною командою мовою телефону, тож її видно й можна виправити текстом.
 */
@Composable
internal fun CustomCommandsCard(
    commands: List<CustomCommand>,
    apps: List<AppInfo>,
    profiles: List<String>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    bluetoothAllowed: Boolean,
    pairedDevices: () -> List<PairedDevice>,
    onRequestBluetooth: () -> Unit,
    onSave: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
    /** Дозвіл «Поверх інших вікон»: без нього команда, що стартує сама, відкриває екран лише дотиком до сповіщення. */
    canOpenDirectly: Boolean = true,
) {
    // null — список команд; порожня команда — нова.
    var editing by remember { mutableStateOf<CustomCommand?>(null) }
    val haptics = LocalHaptics.current
    val current = editing
    if (current != null) {
        CommandEditor(
            command = current,
            others = commands.filter { it.id != current.id },
            apps = apps,
            profiles = profiles,
            iconFor = iconFor,
            devices = DeviceAccess(bluetoothAllowed, pairedDevices, onRequestBluetooth),
            canOpenDirectly = canOpenDirectly,
            onSave = { onSave(it); editing = null },
            onDelete = if (current.id.isEmpty()) null else ({ onDelete(current.id); editing = null }),
            onCancel = { editing = null },
            onReverse = { editing = it },
        )
        return
    }
    Card {
        Text(stringResource(R.string.custom_commands_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(R.string.custom_commands_note))
        commands.forEach { command ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { haptics.tap(); editing = command }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                val title = command.phrase.ifBlank { command.triggers.firstOrNull()?.let { triggerText(it) }.orEmpty() }
                Text(title, color = Oled.TextPrimary, fontSize = 16.sp)
                // Умова й події — одним рядком, щоб було видно, коли команда спрацює.
                val details = listOfNotNull(
                    command.conditions.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.condition_only_if, conditionsText(it)) },
                    command.triggers.takeIf { it.isNotEmpty() && command.phrase.isNotBlank() }?.let { list ->
                        stringResource(R.string.command_auto_short, list.map { triggerText(it) }.joinToString(", "))
                    },
                )
                details.forEach { Text(it, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(
                    command.stepTexts.joinToString(" → "),
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ActionOption(R.string.custom_command_add, Icons.Filled.Add) { editing = CustomCommand("", "", emptyList()) }
    }
}

private enum class StepPicker { MENU, SEARCH, APP, ACTION, PROFILE, ROUTE, CALL, SMS, WAIT, SAY, TEXT }

/** З чим збігається фраза: інша команда без умови, програма, профіль, вбудована команда. */
@Composable
private fun phraseConflict(
    phrase: String,
    conditions: List<CommandCondition>,
    others: List<CustomCommand>,
    apps: List<AppInfo>,
    profiles: List<String>,
): String? {
    val q = SearchNormalizer.normalize(phrase)
    if (q.isEmpty()) return null
    val twin = others.firstOrNull { SearchNormalizer.normalize(it.phrase) == q && it.conditions.isEmpty() }
    return when {
        twin != null && conditions.isEmpty() -> stringResource(R.string.phrase_conflict_command)
        profiles.any { SearchNormalizer.normalize(it) == q } -> stringResource(R.string.phrase_conflict_profile)
        apps.firstOrNull { SearchNormalizer.normalize(it.label) == q } != null ->
            stringResource(R.string.phrase_conflict_app, apps.first { SearchNormalizer.normalize(it.label) == q }.label)
        VoiceCommandParser.parse(phrase).let { it is VoiceCommand.Action || it is VoiceCommand.SwitchProfile || it == VoiceCommand.OpenLast } ->
            stringResource(R.string.phrase_conflict_builtin)
        else -> null
    }
}

@Composable
private fun CommandEditor(
    command: CustomCommand,
    others: List<CustomCommand>,
    apps: List<AppInfo>,
    profiles: List<String>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    devices: DeviceAccess,
    canOpenDirectly: Boolean,
    onSave: (CustomCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    onReverse: (CustomCommand) -> Unit,
) {
    val searchEngine = remember { AppSearchEngine() }
    val appIndex = remember(apps) { searchEngine.index(apps) }
    var phrase by remember { mutableStateOf(command.phrase) }
    var steps by remember { mutableStateOf(command.steps) }
    var triggers by remember { mutableStateOf(command.triggers) }
    var conditions by remember { mutableStateOf(command.conditions) }
    var picker by remember { mutableStateOf<StepPicker?>(null) }
    var pickingCondition by remember { mutableStateOf(false) }
    // Крок, для якого відкрито «Умови кроку».
    var stepConditionsFor by remember { mutableStateOf<Int?>(null) }
    var addingTrigger by remember { mutableStateOf(false) }
    // Без фрази команда можлива, лише якщо вона запускається сама.
    val canSave = (phrase.isNotBlank() || triggers.isNotEmpty()) && steps.isNotEmpty()
    val addStep: (String) -> Unit = { step -> steps = CustomCommand.cleanSteps(steps + CommandStep(step)); picker = null }
    Card {
        Text(stringResource(R.string.custom_commands_title), color = Oled.TextPrimary, fontSize = 17.sp)
        OutlinedTextField(
            value = phrase,
            onValueChange = { phrase = it },
            singleLine = true,
            label = { Text(stringResource(R.string.custom_command_phrase)) },
            placeholder = { Text(stringResource(R.string.custom_command_phrase_hint), color = Oled.TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        // Фраза, що збігається з чимось іншим, — попереджаємо, щоб жодна команда не «зникала» мовчки.
        phraseConflict(phrase, conditions, others, apps, profiles)?.let { Note(it) }
        // «Лише якщо»: усі умови мають виконуватись; без умов — будь-коли.
        Text(stringResource(R.string.condition_title), color = Oled.TextSecondary, fontSize = 13.sp)
        if (conditions.isEmpty()) Note(stringResource(R.string.condition_always))
        conditions.forEach { condition ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(conditionText(condition), color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                SmallButton(Icons.Filled.Close, R.string.custom_step_remove, onClick = { conditions = conditions - condition })
            }
        }
        ActionOption(R.string.condition_add, Icons.Filled.Add) { pickingCondition = true }
        Text(stringResource(R.string.command_auto_title), color = Oled.TextSecondary, fontSize = 13.sp)
        triggers.forEach { trigger ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(triggerText(trigger), color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                SmallButton(Icons.Filled.Close, R.string.custom_step_remove, onClick = { triggers = triggers - trigger })
            }
        }
        ActionOption(R.string.command_auto_add, Icons.Filled.Add) { addingTrigger = true }
        Text(stringResource(R.string.custom_command_steps), color = Oled.TextSecondary, fontSize = 13.sp)
        if (steps.isEmpty()) Note(stringResource(R.string.custom_steps_empty))
        steps.forEachIndexed { index, step ->
            StepRow(
                number = index + 1,
                step = step,
                onConditions = { stepConditionsFor = index },
                onUp = if (index > 0) ({ steps = steps.swap(index, index - 1) }) else null,
                onDown = if (index < steps.lastIndex) ({ steps = steps.swap(index, index + 1) }) else null,
                onRemove = { steps = steps.filterIndexed { i, _ -> i != index } },
            )
        }
        ActionOption(R.string.custom_step_add, Icons.Filled.Add) { picker = StepPicker.MENU }
        // Що не спрацює — одразу, до збереження.
        val problems = CommandCheck.check(
            CustomCommand(command.id, phrase, steps, triggers, conditions),
            appExists = { query -> appIndex.search(query).isNotEmpty() },
            profiles = profiles,
            canOpenDirectly = canOpenDirectly,
        )
        problems.forEach { problem -> Warning(problemText(problem)) }
        // Кнопки — у тому ж вигляді, що й пункти меню налаштувань.
        if (canSave) {
            ActionOption(R.string.custom_command_save, Icons.Filled.Done) {
                val id = command.id.ifEmpty { CustomCommand.create(phrase, emptyList()).id }
                onSave(CustomCommand(id, phrase.trim(), steps, triggers, conditions))
            }
        } else {
            Note(stringResource(R.string.custom_command_save_hint))
        }
        // Зворотна команда: подія навпаки й протилежні кроки — відкривається новою, щоб перевірити й зберегти.
        if (triggers.isNotEmpty()) {
            val texts = stepTexts()
            val reversed = CommandReverse.reverse(CustomCommand(command.id, phrase, steps, triggers, conditions), texts)
            if (reversed != null) {
                ActionOption(R.string.command_reverse, Icons.Filled.Refresh) { onReverse(reversed) }
                Note(stringResource(R.string.command_reverse_note))
            }
        }
        ActionOption(R.string.cancel, Icons.Filled.Close, onCancel)
        if (onDelete != null) ActionOption(R.string.custom_command_delete, Icons.Filled.Delete, onDelete)
    }
    if (pickingCondition) {
        ConditionDialog(devices, profiles, onPick = { conditions = (conditions + it).distinct(); pickingCondition = false }, onDismiss = { pickingCondition = false })
    }
    stepConditionsFor?.let { index ->
        steps.getOrNull(index)?.let { step ->
            StepConditionsDialog(
                step = step,
                devices = devices,
                profiles = profiles,
                onChange = { updated -> steps = steps.mapIndexed { i, s -> if (i == index) s.copy(conditions = updated) else s } },
                onDismiss = { stepConditionsFor = null },
            )
        }
    }
    if (addingTrigger) {
        TriggerDialog(devices, profiles, onPick = { triggers = (triggers + it).distinct(); addingTrigger = false }, onDismiss = { addingTrigger = false })
    }
    when (picker) {
        StepPicker.MENU -> StepMenu(onPick = { picker = it }, onHome = addStep, onDismiss = { picker = null })
        StepPicker.SEARCH -> SearchStepDialog(apps, profiles, onPick = addStep, onDismiss = { picker = null })
        StepPicker.ROUTE -> RouteStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.CALL -> CallStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.SMS -> SmsStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.WAIT -> WaitStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.SAY -> SingleFieldStepDialog(
            R.string.custom_step_say, R.string.custom_step_say_text, stringResource(R.string.cstep_say), addStep, { picker = null },
            note = stringResource(R.string.custom_step_say_note),
        )
        StepPicker.APP -> AppStepDialog(apps, iconFor, onPick = addStep, onDismiss = { picker = null })
        StepPicker.ACTION -> ActionStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.PROFILE -> ProfileStepDialog(profiles, onPick = addStep, onDismiss = { picker = null })
        StepPicker.TEXT -> TextStepDialog(onPick = addStep, onDismiss = { picker = null })
        null -> Unit
    }
}

private const val MAX_APPS_IN_SEARCH = 3

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

/** Крок: номер, значок за видом (програма / режим / дія / текст), текст, умови кроку й кнопки порядку. */
@Composable
private fun StepRow(number: Int, step: CommandStep, onConditions: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onRemove: () -> Unit) {
    val icon = when (StepCommands.parse(step.text)) {
        is StepCommand.Wait -> Icons.Filled.Refresh
        StepCommand.Home -> Icons.Filled.Home
        StepCommand.PreviousProfile -> Icons.Filled.AccountCircle
        is StepCommand.Say -> Icons.Filled.Notifications
        null -> when (val parsed = VoiceCommandParser.parse(step.text)) {
            is VoiceCommand.Open, VoiceCommand.OpenLast -> Icons.Filled.PlayArrow
            is VoiceCommand.SwitchProfile -> Icons.Filled.AccountCircle
            is VoiceCommand.Action -> parsed.action.icon()
            null -> Icons.Filled.Edit
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$number", color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.width(18.dp))
        Icon(icon, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(step.text, color = Oled.TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (step.conditions.isNotEmpty()) {
                Text(stringResource(R.string.stepcond_only_if, conditionsText(step.conditions)), color = Oled.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Замок — умови саме цього кроку (світліший, якщо вони є).
        SmallButton(Icons.Filled.Lock, R.string.stepcond_title, onConditions, highlighted = step.conditions.isNotEmpty())
        SmallButton(Icons.Filled.KeyboardArrowUp, R.string.custom_step_up, onUp)
        SmallButton(Icons.Filled.KeyboardArrowDown, R.string.custom_step_down, onDown)
        SmallButton(Icons.Filled.Close, R.string.custom_step_remove, onRemove)
    }
}

/** «Умови кроку»: крок виконується, лише якщо всі ці умови виконані (інакше пропускається, решта йде далі). */
@Composable
private fun StepConditionsDialog(
    step: CommandStep,
    devices: DeviceAccess,
    profiles: List<String>,
    onChange: (List<CommandCondition>) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(R.string.stepcond_title), color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(step.text, color = Oled.TextPrimary, fontSize = 15.sp)
                Note(stringResource(R.string.stepcond_note))
                if (step.conditions.isEmpty()) Note(stringResource(R.string.condition_always))
                step.conditions.forEach { condition ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(conditionText(condition), color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        SmallButton(Icons.Filled.Close, R.string.custom_step_remove, onClick = { onChange(step.conditions - condition) })
                    }
                }
                ActionOption(R.string.condition_add, Icons.Filled.Add) { adding = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Oled.TextPrimary) } },
    )
    if (adding) {
        ConditionDialog(devices, profiles, onPick = { onChange((step.conditions + it).distinct()); adding = false }, onDismiss = { adding = false })
    }
}

@Composable
private fun SmallButton(icon: ImageVector, @StringRes description: Int, onClick: (() -> Unit)?, highlighted: Boolean = false) {
    val haptics = LocalHaptics.current
    IconButton(onClick = { haptics.tap(); onClick?.invoke() }, enabled = onClick != null, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            tint = when {
                onClick == null -> Oled.TextSecondary.copy(alpha = 0.3f)
                highlighted -> Oled.TextPrimary
                else -> Oled.TextSecondary
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StepMenu(onPick: (StepPicker) -> Unit, onHome: (String) -> Unit, onDismiss: () -> Unit) {
    val home = stringResource(R.string.cstep_home)
    PickerDialog(R.string.custom_step_add, onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionOption(R.string.custom_step_from_search, Icons.Filled.Search) { onPick(StepPicker.SEARCH) }
            ActionOption(R.string.custom_step_app, Icons.Filled.PlayArrow) { onPick(StepPicker.APP) }
            ActionOption(R.string.custom_step_action, Icons.Filled.Settings) { onPick(StepPicker.ACTION) }
            ActionOption(R.string.custom_step_profile, Icons.Filled.AccountCircle) { onPick(StepPicker.PROFILE) }
            ActionOption(R.string.custom_step_route, Icons.Filled.Place) { onPick(StepPicker.ROUTE) }
            ActionOption(R.string.custom_step_call, Icons.Filled.Call) { onPick(StepPicker.CALL) }
            ActionOption(R.string.custom_step_sms, Icons.Filled.Send) { onPick(StepPicker.SMS) }
            ActionOption(R.string.custom_step_wait, Icons.Filled.Refresh) { onPick(StepPicker.WAIT) }
            ActionOption(R.string.custom_step_say, Icons.Filled.Notifications) { onPick(StepPicker.SAY) }
            ActionOption(R.string.custom_step_home, Icons.Filled.Home) { onHome(home) }
            ActionOption(R.string.custom_step_text, Icons.Filled.Edit) { onPick(StepPicker.TEXT) }
        }
    }
}

/**
 * «З пошуку»: пишете, як у пошуку, — і бачите, що саме зробить крок: дія, маршрут, програма, режим.
 * Вибраний варіант зберігається звичайною командою мовою телефону.
 */
@Composable
private fun SearchStepDialog(apps: List<AppInfo>, profiles: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val engine = remember { AppSearchEngine() }
    val index = remember(apps) { engine.index(apps) }
    val openTemplate = stringResource(R.string.step_open)
    val profileTemplate = stringResource(R.string.step_profile)
    val routeTemplate = stringResource(R.string.step_route)
    val text = query.trim()
    // (крок, опис, значок)
    val candidates = buildList<Triple<String, String, ImageVector>> {
        if (text.isEmpty()) return@buildList
        when (val own = StepCommands.parse(text)) {
            is StepCommand.Wait -> add(Triple(text, stepCommandTitle(own), Icons.Filled.Refresh))
            StepCommand.Home -> add(Triple(text, stringResource(R.string.custom_step_home), Icons.Filled.Home))
            StepCommand.PreviousProfile -> add(Triple(text, stringResource(R.string.cstep_previous_profile), Icons.Filled.AccountCircle))
            is StepCommand.Say -> add(Triple(text, stringResource(R.string.custom_step_say_preview, own.text), Icons.Filled.Notifications))
            null -> when (val parsed = VoiceCommandParser.parse(text)) {
                is VoiceCommand.Action -> add(Triple(text, parsed.action.title(), parsed.action.icon()))
                is VoiceCommand.SwitchProfile -> add(Triple(text, profileTemplate.format(parsed.name), Icons.Filled.AccountCircle))
                else -> Unit
            }
        }
        profiles.filter { SearchNormalizer.normalize(it).startsWith(SearchNormalizer.normalize(text)) }.take(2)
            .forEach { add(Triple(profileTemplate.format(it), profileTemplate.format(it), Icons.Filled.AccountCircle)) }
        index.search(text).take(MAX_APPS_IN_SEARCH).forEach { add(Triple(openTemplate.format(it.label), openTemplate.format(it.label), Icons.Filled.PlayArrow)) }
        // Як «Знайти шлях» у глобальному пошуку: будь-яка назва місця — маршрут туди.
        add(Triple(routeTemplate.format(text), routeTemplate.format(text), Icons.Filled.Place))
    }.distinctBy { it.first }
    PickerDialog(R.string.custom_step_from_search, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_step_search_hint), color = Oled.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                items(candidates, key = { it.first }) { (step, title, icon) ->
                    ActionOption(title, icon) { onPick(step) }
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("⚠", color = WarningColor, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = WarningColor, fontSize = 13.sp)
    }
}

/** Приглушений бурштиновий — помітно, але в тоні чорної теми. */
private val WarningColor = Color(0xFFD6B47A)

@Composable
private fun problemText(problem: CommandProblem): String = when (problem.kind) {
    CommandProblemKind.UNKNOWN_APP -> stringResource(R.string.problem_unknown_app, problem.step, problem.detail)
    CommandProblemKind.UNKNOWN_PROFILE -> stringResource(R.string.problem_unknown_profile, problem.step, problem.detail)
    CommandProblemKind.NEVER -> stringResource(R.string.problem_never)
    CommandProblemKind.STEP_NEVER -> stringResource(R.string.problem_step_never, problem.step)
    CommandProblemKind.WAIT_AT_END -> stringResource(R.string.problem_wait_end, problem.step)
    CommandProblemKind.NEEDS_SCREEN -> stringResource(R.string.problem_needs_screen, problem.step)
}

/** Тексти кроків для зворотної команди — мовою телефону. */
@Composable
private fun stepTexts() = StepTexts(
    flashlightOn = stringResource(R.string.step_flashlight_on),
    flashlightOff = stringResource(R.string.step_flashlight_off),
    play = stringResource(R.string.step_play),
    pause = stringResource(R.string.step_pause),
    louder = stringResource(R.string.step_volume_up),
    quieter = stringResource(R.string.step_volume_down),
    home = stringResource(R.string.cstep_home),
    previousProfile = stringResource(R.string.cstep_previous_profile),
)

@Composable
private fun stepCommandTitle(wait: StepCommand.Wait): String =
    if (wait.seconds % 60 == 0) stringResource(R.string.cstep_wait_minutes, wait.seconds / 60) else stringResource(R.string.cstep_wait_seconds, wait.seconds)

/** Одне поле — один крок за шаблоном (маршрут, дзвінок). */
@Composable
private fun SingleFieldStepDialog(@StringRes title: Int, @StringRes label: Int, template: String, onPick: (String) -> Unit, onDismiss: () -> Unit, note: String? = null) {
    var value by remember { mutableStateOf("") }
    PickerDialog(title, onDismiss, confirm = if (value.isNotBlank()) ({ onPick(template.format(value.trim())) }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(label)) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            note?.let { Note(it) }
        }
    }
}

@Composable
private fun RouteStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) =
    SingleFieldStepDialog(R.string.custom_step_route, R.string.custom_step_place, stringResource(R.string.step_route), onPick, onDismiss)

@Composable
private fun CallStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) =
    SingleFieldStepDialog(R.string.custom_step_call, R.string.custom_step_contact, stringResource(R.string.step_call), onPick, onDismiss)

/** SMS: кому й текст; відкривається готовим повідомленням — надсилаєте ви. */
@Composable
private fun SmsStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val template = stringResource(R.string.step_sms)
    PickerDialog(R.string.custom_step_sms, onDismiss, confirm = if (name.isNotBlank() && body.isNotBlank()) ({ onPick(template.format(name.trim(), body.trim())) }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.custom_step_contact)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text(stringResource(R.string.custom_step_sms_text)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Note(stringResource(R.string.custom_step_sms_note))
        }
    }
}

/** «Зачекати»: секунди чи хвилини; довгі паузи витримує системний будильник. */
@Composable
private fun WaitStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("10") }
    var minutes by remember { mutableStateOf(false) }
    val n = amount.toIntOrNull()?.takeIf { it in 1..(if (minutes) 1440 else 3600) }
    val template = stringResource(if (minutes) R.string.cstep_wait_minutes else R.string.cstep_wait_seconds)
    PickerDialog(R.string.custom_step_wait, onDismiss, confirm = if (n != null) ({ onPick(template.format(n)) }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            SwitchRow(stringResource(R.string.custom_wait_in_minutes), "", minutes) { minutes = it }
            Note(stringResource(R.string.custom_step_wait_note))
        }
    }
}

@Composable
private fun AppStepDialog(apps: List<AppInfo>, iconFor: suspend (AppInfo) -> ImageBitmap?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val engine = remember { AppSearchEngine() }
    val index = remember(apps) { engine.index(apps) }
    val shown = if (query.isBlank()) apps else index.search(query)
    val template = stringResource(R.string.step_open)
    val haptics = LocalHaptics.current
    PickerDialog(R.string.custom_step_app, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_step_search), color = Oled.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(shown, key = { it.key }) { app ->
                    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { haptics.tap(); onPick(template.format(app.label)) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) } }
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Oled.TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/** Дії без параметрів — одразу кроком; яскравість, таймер, будильник — із вибором значення. */
@Composable
private fun ActionStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var param by remember { mutableStateOf<Int?>(null) }
    val simple = listOf(
        R.string.step_flashlight_on, R.string.step_flashlight_off,
        R.string.step_play, R.string.step_pause, R.string.step_next,
        R.string.step_volume_up, R.string.step_volume_down, R.string.step_mute,
        R.string.step_wifi, R.string.step_bluetooth, R.string.step_mobile_data, R.string.step_time,
    ).map { stringResource(it) }
    when (param) {
        R.string.step_brightness -> BrightnessDialog(onPick, onDismiss)
        R.string.step_timer -> TimerDialog(onPick, onDismiss)
        R.string.step_alarm -> AlarmDialog(onPick, onDismiss)
        else -> PickerDialog(R.string.custom_step_action, onDismiss) {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ActionOption(R.string.custom_action_brightness, Icons.Filled.Settings) { param = R.string.step_brightness } }
                item { ActionOption(R.string.custom_action_timer, Icons.Filled.Settings) { param = R.string.step_timer } }
                item { ActionOption(R.string.custom_action_alarm, Icons.Filled.Settings) { param = R.string.step_alarm } }
                items(simple) { text -> ActionOption(text.replaceFirstChar { it.uppercase() }, Icons.Filled.Settings) { onPick(text) } }
                // Чесно: Wi-Fi, Bluetooth і дані Android дозволяє перемикати лише самому користувачу.
                item { Note(stringResource(R.string.custom_step_panels_note)) }
            }
        }
    }
}

@Composable
private fun BrightnessDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var percent by remember { mutableStateOf(50f) }
    val template = stringResource(R.string.step_brightness)
    PickerDialog(R.string.custom_action_brightness, onDismiss, confirm = { onPick(template.format(percent.toInt())) }) {
        Column {
            Text(stringResource(R.string.custom_step_percent, percent.toInt()), color = Oled.TextPrimary, fontSize = 16.sp)
            Slider(
                value = percent,
                onValueChange = { percent = it },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Oled.TextPrimary, activeTrackColor = Oled.TextPrimary),
            )
        }
    }
}

@Composable
private fun TimerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("10") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..999 }
    val template = stringResource(R.string.step_timer)
    PickerDialog(R.string.custom_action_timer, onDismiss, confirm = minutes?.let { { onPick(template.format(it)) } }) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(3) },
            singleLine = true,
            label = { Text(stringResource(R.string.custom_step_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
    }
}

@Composable
private fun AlarmDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var hour by remember { mutableStateOf("7") }
    var minute by remember { mutableStateOf("00") }
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
    val m = minute.toIntOrNull()?.takeIf { it in 0..59 }
    val template = stringResource(R.string.step_alarm)
    PickerDialog(R.string.custom_action_alarm, onDismiss, confirm = if (h != null && m != null) ({ onPick(template.format(h, m)) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                singleLine = true,
                label = { Text(stringResource(R.string.custom_step_hour)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
            Text(" : ", color = Oled.TextPrimary, fontSize = 20.sp)
            OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                singleLine = true,
                label = { Text(stringResource(R.string.custom_step_minute)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
        }
    }
}

@Composable
private fun ProfileStepDialog(profiles: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val template = stringResource(R.string.step_profile)
    val previous = stringResource(R.string.cstep_previous_profile)
    PickerDialog(R.string.custom_step_profile, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // «Попередній режим» — той, що був до останнього перемикання (зручно у зворотних командах).
            ActionOption(previous, Icons.Filled.Refresh) { onPick(previous) }
            profiles.forEach { name -> ActionOption(name, Icons.Filled.AccountCircle) { onPick(template.format(name)) } }
        }
    }
}

@Composable
private fun TextStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    PickerDialog(R.string.custom_step_text, onDismiss, confirm = if (text.isNotBlank()) ({ onPick(text.trim()) }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_command_steps_hint), color = Oled.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Note(stringResource(R.string.custom_command_steps_note))
        }
    }
}

/** Темний діалог вибору; [confirm] = null — без кнопки «Додати» (вибір одним дотиком). */
@Composable
private fun PickerDialog(@StringRes title: Int, onDismiss: () -> Unit, confirm: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(title), color = Oled.TextPrimary) },
        text = content,
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = confirm) { Text(stringResource(R.string.custom_step_add_confirm), color = Oled.TextPrimary) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Oled.TextPrimary,
    unfocusedTextColor = Oled.TextPrimary,
    cursorColor = Oled.Accent,
    focusedBorderColor = Color(0xFF5A5A5A),
    unfocusedBorderColor = Color(0xFF3A3A3A),
    focusedLabelColor = Oled.TextSecondary,
    unfocusedLabelColor = Oled.TextSecondary,
)
