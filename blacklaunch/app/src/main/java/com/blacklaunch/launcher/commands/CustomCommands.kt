package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.search.SearchNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Власна команда: фраза → кроки по черзі. Кожен крок — звичайна команда, як її кажуть
 * чи набирають: «вайфай», «яскравість 30», «режим дім», «відкрий музику».
 */
data class CustomCommand(val id: String, val phrase: String, val steps: List<String>) {
    companion object {
        fun create(phrase: String, steps: List<String>) = CustomCommand(UUID.randomUUID().toString(), phrase.trim(), clean(steps))

        fun clean(steps: List<String>) = steps.map { it.trim() }.filter { it.isNotEmpty() }
    }
}

object CustomCommandMatcher {
    /** Сказана чи набрана фраза рівно збігається з командою. */
    fun exact(commands: List<CustomCommand>, text: String): CustomCommand? {
        val q = SearchNormalizer.normalize(text)
        if (q.isEmpty()) return null
        return commands.firstOrNull { SearchNormalizer.normalize(it.phrase) == q }
    }

    /** Для пошуку: збіг або початок фрази (від 3 літер). */
    fun suggest(commands: List<CustomCommand>, text: String): List<CustomCommand> {
        val q = SearchNormalizer.normalize(text)
        if (q.length < MIN_PREFIX) return listOfNotNull(exact(commands, text))
        return commands.filter { SearchNormalizer.normalize(it.phrase).startsWith(q) }
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

        fun encode(commands: List<CustomCommand>): String = commands.joinToString("\n") { c ->
            listOf(c.id, c.phrase.replace(FIELD, ' '), c.steps.joinToString(STEP.toString()) { it.replace(FIELD, ' ') })
                .joinToString(FIELD.toString())
        }

        fun decode(text: String?): List<CustomCommand> = text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3 || parts[1].isBlank()) return@mapNotNull null
            CustomCommand(parts[0], parts[1], CustomCommand.clean(parts[2].split(STEP)))
        }.toList()
    }
}
