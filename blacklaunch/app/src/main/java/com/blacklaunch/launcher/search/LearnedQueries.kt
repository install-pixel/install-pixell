package com.blacklaunch.launcher.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Навчання на діях: який запит → яку програму відкрили. Наступного разу ця програма
 * стоїть першою на такий запит. Зберігається лише на телефоні.
 */
interface LearnedQueries {
    /** Нормалізований запит → (ключ програми → скільки разів обрано). */
    val learned: StateFlow<Map<String, Map<String, Int>>>
    fun record(query: String, appKey: String)
    fun clear()
}

open class InMemoryLearnedQueries(private val maxQueries: Int = MAX_QUERIES) : LearnedQueries {
    private val _learned = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    override val learned: StateFlow<Map<String, Map<String, Int>>> = _learned.asStateFlow()

    override fun record(query: String, appKey: String) {
        val q = SearchNormalizer.normalize(query)
        if (q.isEmpty()) return
        val current = _learned.value
        val forQuery = current[q].orEmpty()
        // Найновіший запит — в кінці; найстаріші відкидаються, щоб пам'ять не росла.
        val updated = (current - q) + (q to forQuery + (appKey to (forQuery[appKey] ?: 0) + 1))
        _learned.value = updated.entries.toList().takeLast(maxQueries).associate { it.key to it.value }
        onChanged()
    }

    override fun clear() {
        _learned.value = emptyMap()
        onChanged()
    }

    protected open fun onChanged() = Unit

    internal fun restore(saved: Map<String, Map<String, Int>>) {
        _learned.value = saved
    }

    companion object {
        const val MAX_QUERIES = 300

        fun encode(learned: Map<String, Map<String, Int>>): String =
            learned.flatMap { (q, apps) -> apps.map { (key, count) -> "$q\t$key\t$count" } }.joinToString("\n")

        fun decode(text: String?): Map<String, Map<String, Int>> {
            val result = linkedMapOf<String, MutableMap<String, Int>>()
            text.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t')
                val count = parts.getOrNull(2)?.toIntOrNull()
                if (parts.size == 3 && parts[0].isNotEmpty() && parts[1].isNotEmpty() && count != null) {
                    result.getOrPut(parts[0]) { linkedMapOf() }[parts[1]] = count
                }
            }
            return result
        }
    }
}
