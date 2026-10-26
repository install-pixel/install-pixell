package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.search.SearchNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Подія, від якої команда запускається сама; для Bluetooth — конкретний пристрій; для часу — [minute] (від півночі)
 * і [days] (біти пн=1 … нд=64, 0 — щодня); для режиму — [profile] (назва).
 */
data class CommandTrigger(
    val kind: TriggerKind,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val minute: Int? = null,
    val days: Int = 0,
    val profile: String? = null,
) {
    val isBluetooth get() = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED
    val isProfile get() = kind == TriggerKind.PROFILE_ON || kind == TriggerKind.PROFILE_OFF

    /** [detail] — адреса пристрою для Bluetooth, назва режиму для подій режиму. */
    fun matches(event: TriggerKind, detail: String?): Boolean = kind == event && when {
        isBluetooth -> deviceAddress == null || deviceAddress.equals(detail, ignoreCase = true)
        isProfile -> profile != null && detail != null && SearchNormalizer.normalize(profile) == SearchNormalizer.normalize(detail)
        else -> true
    }
}

/** «Лише якщо»: що має бути (або, з [CommandCondition.negate], не бути), щоб команда чи крок виконались. */
enum class ConditionKind { BLUETOOTH_DEVICE, CHARGING, WIRED_HEADSET, TIME, BATTERY_ABOVE, BATTERY_BELOW, PROFILE, WIFI, MOBILE_DATA }

/**
 * Умова. Для Bluetooth — пристрій; для часу — [from]…[to] (хвилини від півночі; через північ теж) і [days]
 * (біти пн=1 … нд=64, 0 — щодня); для батареї — [percent]; для профілю — [profile]. [negate] — «навпаки».
 */
data class CommandCondition(
    val kind: ConditionKind,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val from: Int? = null,
    val to: Int? = null,
    val days: Int = 0,
    val percent: Int? = null,
    val profile: String? = null,
    val negate: Boolean = false,
) {
    /** Режими умови «Поточний режим» (можна кілька — будь-який з них): «Дорога|Дім». */
    val profileNames: List<String> get() = profile?.split(PROFILE_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    companion object {
        const val PROFILE_SEPARATOR = "|"
    }
}

/** Крок команди: [text] — як сказана команда («режим дорога»), [conditions] — лише якщо (усі). */
data class CommandStep(val text: String, val conditions: List<CommandCondition> = emptyList())

/** Перевірки, які не залежать від Android — час і дні тижня. */
object ConditionRules {
    /** [minute] — хвилина від півночі, [isoDay] — 1 (пн) … 7 (нд). */
    fun timeMatches(condition: CommandCondition, minute: Int, isoDay: Int): Boolean {
        val dayOk = condition.days == 0 || (condition.days shr (isoDay - 1)) and 1 == 1
        val from = condition.from
        val to = condition.to
        if (from == null || to == null || from == to) return dayOk
        val inside = if (from < to) minute in from until to else minute >= from || minute < to
        return dayOk && inside
    }
}

/**
 * Власна команда: [phrase] (голосом чи текстом; може бути порожньою, якщо команда запускається сама) →
 * кроки по черзі. Кожен крок — звичайна команда, як її кажуть: «вайфай», «яскравість 30», «режим дім».
 * [triggers] — події, від яких команда запускається сама; [conditions] — виконується лише якщо всі виконані.
 */
data class CustomCommand(
    val id: String,
    val phrase: String,
    val steps: List<CommandStep>,
    val triggers: List<CommandTrigger> = emptyList(),
    val conditions: List<CommandCondition> = emptyList(),
) {
    /** Тексти кроків — для показу. */
    val stepTexts: List<String> get() = steps.map { it.text }

    companion object {
        fun create(phrase: String, steps: List<String>) = CustomCommand(UUID.randomUUID().toString(), phrase.trim(), clean(steps).map(::CommandStep))

        fun clean(steps: List<String>) = steps.map { it.trim() }.filter { it.isNotEmpty() }

        fun cleanSteps(steps: List<CommandStep>) = steps.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }
}

object CustomCommandMatcher {
    /** Сказана чи набрана фраза рівно збігається з командою. */
    fun exact(commands: List<CustomCommand>, text: String): CustomCommand? = exactAll(commands, text).firstOrNull()

    /**
     * Усі команди з цією фразою: спершу ті, що з умовою («музика» лише з навушниками),
     * потім без умови — так одна фраза може робити різне залежно від підключеного.
     */
    fun exactAll(commands: List<CustomCommand>, text: String): List<CustomCommand> {
        val q = SearchNormalizer.normalize(text)
        if (q.isEmpty()) return emptyList()
        return commands.filter { it.phrase.isNotBlank() && SearchNormalizer.normalize(it.phrase) == q }
            .sortedBy { if (it.conditions.isNotEmpty()) 0 else 1 }
    }

    /** Для пошуку: збіг або початок фрази (від 3 літер); одна фраза — один рядок. */
    fun suggest(commands: List<CustomCommand>, text: String): List<CustomCommand> {
        val q = SearchNormalizer.normalize(text)
        val found = if (q.length < MIN_PREFIX) {
            exactAll(commands, text)
        } else {
            commands.filter { it.phrase.isNotBlank() && SearchNormalizer.normalize(it.phrase).startsWith(q) }
        }
        return found.distinctBy { SearchNormalizer.normalize(it.phrase) }
    }

    private const val MIN_PREFIX = 3
}

/** Зберігання лише на телефоні (SharedPreferences), без мережі. */
interface CustomCommandStore {
    val commands: StateFlow<List<CustomCommand>>
    fun save(command: CustomCommand)
    fun delete(id: String)
}

class InMemoryCustomCommandStore(initial: List<CustomCommand> = emptyList()) : CustomCommandStore {
    private val _commands = MutableStateFlow(initial)
    override val commands: StateFlow<List<CustomCommand>> = _commands.asStateFlow()

    override fun save(command: CustomCommand) {
        val list = _commands.value
        _commands.value = if (list.any { it.id == command.id }) list.map { if (it.id == command.id) command else it } else list + command
    }

    override fun delete(id: String) {
        _commands.value = _commands.value.filterNot { it.id == id }
    }

    companion object {
        private const val FIELD = '\t'
        private const val STEP = '\u001F'
        private const val ITEM = '\u001E'
        private const val PART = '\u001D'
        private const val STEP_CONDITIONS = '\u001C'

        private fun String.safe() = replace(FIELD, ' ').replace(STEP, ' ').replace(ITEM, ' ').replace(PART, ' ').replace(STEP_CONDITIONS, ' ')

        private fun encodeCondition(c: CommandCondition) = listOf(
            c.kind.name, c.deviceAddress.orEmpty(), c.deviceName.orEmpty().safe(), c.from?.toString().orEmpty(), c.to?.toString().orEmpty(),
            c.days.toString(), c.percent?.toString().orEmpty(), c.profile.orEmpty().safe(), if (c.negate) "1" else "0",
        ).joinToString(PART.toString())

        /** До 1.2.0 умова мала три частини (вид, адреса, назва) — решта за замовчуванням. */
        private fun decodeCondition(item: String): CommandCondition? {
            val p = item.split(PART)
            val kind = ConditionKind.entries.firstOrNull { it.name == p[0] } ?: return null
            return CommandCondition(
                kind,
                p.getOrNull(1)?.ifEmpty { null },
                p.getOrNull(2)?.ifEmpty { null },
                p.getOrNull(3)?.toIntOrNull(),
                p.getOrNull(4)?.toIntOrNull(),
                p.getOrNull(5)?.toIntOrNull() ?: 0,
                p.getOrNull(6)?.toIntOrNull(),
                p.getOrNull(7)?.ifEmpty { null },
                p.getOrNull(8) == "1",
            )
        }

        private fun encodeConditions(list: List<CommandCondition>) = list.joinToString(ITEM.toString(), transform = ::encodeCondition)

        private fun decodeConditions(text: String) = if (text.isEmpty()) emptyList() else text.split(ITEM).mapNotNull(::decodeCondition)

        /** Рядок на команду: id, фраза, кроки (з умовами кроку з 1.2.0), події, умови (останні два — з 0.32.0). */
        fun encode(commands: List<CustomCommand>): String = commands.joinToString("\n") { c ->
            val triggers = c.triggers.joinToString(ITEM.toString()) { t ->
                listOf(t.kind.name, t.deviceAddress.orEmpty(), t.deviceName.orEmpty().safe(), t.minute?.toString().orEmpty(), t.days.toString(), t.profile.orEmpty().safe())
                    .joinToString(PART.toString())
            }
            val steps = c.steps.joinToString(STEP.toString()) { step ->
                if (step.conditions.isEmpty()) step.text.safe() else step.text.safe() + STEP_CONDITIONS + encodeConditions(step.conditions)
            }
            listOf(c.id, c.phrase.safe(), steps, triggers, encodeConditions(c.conditions)).joinToString(FIELD.toString())
        }

        fun decode(text: String?): List<CustomCommand> = text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3 && parts.size != 5) return@mapNotNull null
            val steps = CustomCommand.cleanSteps(
                parts[2].split(STEP).map { raw ->
                    val pieces = raw.split(STEP_CONDITIONS, limit = 2)
                    CommandStep(pieces[0], decodeConditions(pieces.getOrNull(1).orEmpty()))
                },
            )
            val triggers = parts.getOrNull(3).orEmpty().split(ITEM).mapNotNull { item ->
                val p = item.split(PART)
                val kind = TriggerKind.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
                CommandTrigger(
                    kind, p.getOrNull(1)?.ifEmpty { null }, p.getOrNull(2)?.ifEmpty { null }, p.getOrNull(3)?.toIntOrNull(),
                    p.getOrNull(4)?.toIntOrNull() ?: 0, p.getOrNull(5)?.ifEmpty { null },
                )
            }
            val conditions = decodeConditions(parts.getOrNull(4).orEmpty())
            if (parts[1].isBlank() && triggers.isEmpty()) return@mapNotNull null
            CustomCommand(parts[0], parts[1], steps, triggers, conditions)
        }.toList()
    }
}
