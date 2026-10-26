package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser

/** Що в команді не спрацює — показуємо одразу в редакторі. */
enum class CommandProblemKind {
    /** Програму з кроку «відкрий …» не знайдено (або текст кроку не розпізнано). */
    UNKNOWN_APP,

    /** Режиму з кроку «режим …» немає. */
    UNKNOWN_PROFILE,

    /** Умови самої команди суперечать одна одній чи події — команда ніколи не виконається. */
    NEVER,

    /** Умови кроку суперечать (з умовами команди) — крок ніколи не виконається. */
    STEP_NEVER,

    /** Пауза останнім кроком — після неї нічого немає. */
    WAIT_AT_END,

    /** Команда запускається сама, а крок відкриває екран — без дозволу «Поверх інших вікон» лише дотиком до сповіщення. */
    NEEDS_SCREEN,

    /** Від тієї ж події запускається ще інша команда — виконаються обидві. */
    SAME_TRIGGER,
}

/** [step] — номер кроку (з 1) або 0 для всієї команди; [detail] — назва програми, режиму тощо. */
data class CommandProblem(val kind: CommandProblemKind, val step: Int = 0, val detail: String = "")

object CommandCheck {
    fun check(
        command: CustomCommand,
        appExists: (String) -> Boolean,
        profiles: List<String>,
        canOpenDirectly: Boolean,
        others: List<CustomCommand> = emptyList(),
    ): List<CommandProblem> = buildList {
        val profileKeys = profiles.map(SearchNormalizer::normalize)
        // З фразою команду можна запустити й голосом — тоді події не обмежують умови.
        val triggers = if (command.phrase.isBlank()) command.triggers else emptyList()
        if (contradicts(command.conditions, triggers)) add(CommandProblem(CommandProblemKind.NEVER))
        var screenStep = 0
        command.steps.forEachIndexed { i, step ->
            val n = i + 1
            if (step.conditions.isNotEmpty() && contradicts(command.conditions + step.conditions, triggers)) {
                add(CommandProblem(CommandProblemKind.STEP_NEVER, n))
            }
            when (val own = StepCommands.parse(step.text)) {
                is StepCommand.Wait -> if (i == command.steps.lastIndex) add(CommandProblem(CommandProblemKind.WAIT_AT_END, n))
                StepCommand.Home -> if (screenStep == 0) screenStep = n
                StepCommand.PreviousProfile, is StepCommand.Say -> Unit
                null -> when (val parsed = VoiceCommandParser.parse(step.text)) {
                    is VoiceCommand.Open -> {
                        if (!appExists(parsed.query)) add(CommandProblem(CommandProblemKind.UNKNOWN_APP, n, parsed.query))
                        if (screenStep == 0) screenStep = n
                    }
                    VoiceCommand.OpenLast -> if (screenStep == 0) screenStep = n
                    is VoiceCommand.SwitchProfile -> {
                        val key = SearchNormalizer.normalize(parsed.name)
                        if (profileKeys.none { it == key || it.startsWith(key) }) add(CommandProblem(CommandProblemKind.UNKNOWN_PROFILE, n, parsed.name))
                    }
                    is VoiceCommand.Action -> if (needsScreen(parsed.action) && screenStep == 0) screenStep = n
                    null -> add(CommandProblem(CommandProblemKind.UNKNOWN_APP, n, step.text))
                }
            }
        }
        if (command.triggers.isNotEmpty() && !canOpenDirectly && screenStep > 0) add(CommandProblem(CommandProblemKind.NEEDS_SCREEN, screenStep))
        // Одне попередження з усіма такими командами.
        val sameEvent = others.filter { other -> other.id != command.id && other.triggers.any { theirs -> command.triggers.any { mine -> sameEvent(mine, theirs) } } }
            .map { other -> other.phrase.ifBlank { other.stepTexts.firstOrNull().orEmpty() } }.distinct()
        if (sameEvent.isNotEmpty()) add(CommandProblem(CommandProblemKind.SAME_TRIGGER, detail = sameEvent.joinToString(", ")))
    }

    private fun needsScreen(action: VoiceAction): Boolean = when (action) {
        is VoiceAction.Navigate, is VoiceAction.Call, is VoiceAction.Sms, is VoiceAction.Message, is VoiceAction.Panel,
        is VoiceAction.CalendarEvent, is VoiceAction.Alarm,
        -> true
        else -> false
    }

    /**
     * Умови, які разом ніколи не виконаються: те саме «так» і «навпаки»; два різні режими; батарея вище X і нижче Y (X ≥ Y);
     * Wi‑Fi і мобільний інтернет одночасно; умова, що суперечить події (відключено → «заряджається», вимкнено режим X → «режим X»…).
     */
    fun contradicts(conditions: List<CommandCondition>, triggers: List<CommandTrigger> = emptyList()): Boolean {
        val positive = conditions.filter { !it.negate }
        // Те саме «так» і «навпаки».
        if (conditions.any { c -> conditions.any { o -> o.negate != c.negate && o.copy(negate = c.negate) == c } }) return true
        val profilesWanted = positive.filter { it.kind == ConditionKind.PROFILE }.mapNotNull { it.profile?.let(SearchNormalizer::normalize) }.toSet()
        if (profilesWanted.size > 1) return true
        val above = positive.filter { it.kind == ConditionKind.BATTERY_ABOVE }.mapNotNull { it.percent }.maxOrNull()
        val below = positive.filter { it.kind == ConditionKind.BATTERY_BELOW }.mapNotNull { it.percent }.minOrNull()
        if (above != null && below != null && above >= below - 1) return true
        if (positive.any { it.kind == ConditionKind.WIFI } && positive.any { it.kind == ConditionKind.MOBILE_DATA }) return true
        // Подія «за часом», а умова часу її не містить (для кожної такої події).
        val timeTriggers = triggers.filter { it.kind == TriggerKind.TIME && it.minute != null }
        val timeConditions = conditions.filter { it.kind == ConditionKind.TIME }
        if (timeTriggers.isNotEmpty() && triggers.all { it.kind == TriggerKind.TIME } && timeConditions.isNotEmpty()) {
            val anyFits = timeTriggers.any { t ->
                (1..7).any { day ->
                    val dayOk = t.days == 0 || (t.days shr (day - 1)) and 1 == 1
                    dayOk && timeConditions.all { c -> ConditionRules.timeMatches(c, t.minute!!, day) != c.negate }
                }
            }
            if (!anyFits) return true
        }
        // Команда лише від подій, і жодна подія не сумісна з умовами.
        if (triggers.isNotEmpty() && triggers.none { it.kind == TriggerKind.TIME }) {
            if (triggers.all { t -> triggerContradicts(t, conditions) }) return true
        }
        return false
    }

    private fun triggerContradicts(t: CommandTrigger, conditions: List<CommandCondition>): Boolean = conditions.any { c ->
        when (t.kind) {
            TriggerKind.POWER_CONNECTED -> c.kind == ConditionKind.CHARGING && c.negate
            TriggerKind.POWER_DISCONNECTED -> c.kind == ConditionKind.CHARGING && !c.negate
            TriggerKind.HEADSET_PLUGGED -> c.kind == ConditionKind.WIRED_HEADSET && c.negate
            TriggerKind.HEADSET_UNPLUGGED -> c.kind == ConditionKind.WIRED_HEADSET && !c.negate
            TriggerKind.BLUETOOTH_CONNECTED -> c.kind == ConditionKind.BLUETOOTH_DEVICE && c.negate && sameDevice(t, c)
            TriggerKind.BLUETOOTH_DISCONNECTED -> c.kind == ConditionKind.BLUETOOTH_DEVICE && !c.negate && sameDevice(t, c)
            TriggerKind.PROFILE_ON -> c.kind == ConditionKind.PROFILE && sameProfile(t, c) == c.negate
            TriggerKind.PROFILE_OFF -> c.kind == ConditionKind.PROFILE && !c.negate && sameProfile(t, c)
            TriggerKind.TIME -> false
        }
    }

    /** Та сама подія: вид і (для Bluetooth — пристрій, для режиму — назва, для часу — хвилина й дні). */
    private fun sameEvent(it1: CommandTrigger, it2: CommandTrigger): Boolean = it1.kind == it2.kind && when {
        it1.isBluetooth -> it1.deviceAddress.equals(it2.deviceAddress, ignoreCase = true)
        it1.isProfile -> it1.profile != null && it2.profile != null && SearchNormalizer.normalize(it1.profile) == SearchNormalizer.normalize(it2.profile)
        it1.kind == TriggerKind.TIME -> it1.minute == it2.minute && (it1.days == 0 || it2.days == 0 || it1.days and it2.days != 0)
        else -> true
    }

    private fun sameDevice(t: CommandTrigger, c: CommandCondition) =
        t.deviceAddress != null && t.deviceAddress.equals(c.deviceAddress, ignoreCase = true)

    private fun sameProfile(t: CommandTrigger, c: CommandCondition) =
        t.profile != null && c.profile != null && SearchNormalizer.normalize(t.profile) == SearchNormalizer.normalize(c.profile)
}
