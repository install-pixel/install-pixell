package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.search.SearchNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Подія, від якої команда запускається сама; для Bluetooth — конкретний пристрій. */
data class CommandTrigger(val kind: TriggerKind, val deviceAddress: String? = null, val deviceName: String? = null) {
    val isBluetooth get() = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED

    fun matches(event: TriggerKind, address: String?): Boolean =
        kind == event && (!isBluetooth || deviceAddress == null || deviceAddress.equals(address, ignoreCase = true))
}

/** «Лише якщо»: що має бути підключено, щоб команда виконалась. */
enum class ConditionKind { BLUETOOTH_DEVICE, CHARGING, WIRED_HEADSET }

data class CommandCondition(val kind: ConditionKind, val deviceAddress: String? = null, val deviceName: String? = null)

/**
 * Власна команда: [phrase] (голосом чи текстом; може бути порожньою, якщо команда запускається сама) →
 * кроки по черзі. Кожен крок — звичайна команда, як її кажуть: «вайфай», «яскравість 30», «режим дім».
 * [triggers] — події, від яких команда запускається сама; [condition] — виконується лише якщо щось підключено.
 */
data class CustomCommand(
    val id: String,
    val phrase: String,
    val steps: List<String>,
    val triggers: List<CommandTrigger> = emptyList(),
    val condition: CommandCondition? = null,
) {
    companion object {
        fun create(phrase: String, steps: List<String>) = CustomCommand(UUID.randomUUID().toString(), phrase.trim(), clean(steps))

        fun clean(steps: List<String>) = steps.map { it.trim() }.filter { it.isNotEmpty() }
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
            .sortedBy { if (it.condition != null) 0 else 1 }
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

        private fun String.safe() = replace(FIELD, ' ').replace(STEP, ' ').replace(ITEM, ' ').replace(PART, ' ')

        /** Рядок на команду: id, фраза, кроки, події, умова (останні два — з 0.32.0). */
        fun encode(commands: List<CustomCommand>): String = commands.joinToString("\n") { c ->
            val triggers = c.triggers.joinToString(ITEM.toString()) { t ->
                listOf(t.kind.name, t.deviceAddress.orEmpty(), t.deviceName.orEmpty().safe()).joinToString(PART.toString())
            }
            val condition = c.condition?.let { listOf(it.kind.name, it.deviceAddress.orEmpty(), it.deviceName.orEmpty().safe()).joinToString(PART.toString()) }
            listOf(c.id, c.phrase.safe(), c.steps.joinToString(STEP.toString()) { it.safe() }, triggers, condition.orEmpty())
                .joinToString(FIELD.toString())
        }

        fun decode(text: String?): List<CustomCommand> = text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3 && parts.size != 5) return@mapNotNull null
            val steps = CustomCommand.clean(parts[2].split(STEP))
            val triggers = parts.getOrNull(3).orEmpty().split(ITEM).mapNotNull { item ->
                val p = item.split(PART)
                val kind = TriggerKind.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
                CommandTrigger(kind, p.getOrNull(1)?.ifEmpty { null }, p.getOrNull(2)?.ifEmpty { null })
            }
            val condition = parts.getOrNull(4).orEmpty().split(PART).let { p ->
                ConditionKind.entries.firstOrNull { it.name == p[0] }?.let { CommandCondition(it, p.getOrNull(1)?.ifEmpty { null }, p.getOrNull(2)?.ifEmpty { null }) }
            }
            if (parts[1].isBlank() && triggers.isEmpty()) return@mapNotNull null
            CustomCommand(parts[0], parts[1], steps, triggers, condition)
        }.toList()
    }
}
