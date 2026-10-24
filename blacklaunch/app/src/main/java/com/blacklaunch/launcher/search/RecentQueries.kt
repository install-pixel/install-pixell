package com.blacklaunch.launcher.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Останні запити, з яких щось відкрили чи виконали (найновіший перший) — окремо для кожного
 * профілю, щоб у «Роботі» не з'являлось домашнє. Лише на телефоні.
 */
interface RecentQueries {
    /** Профіль → запити. */
    val queries: StateFlow<Map<String, List<String>>>
    fun record(profileId: String, query: String)
    fun remove(profileId: String, query: String)
}

open class InMemoryRecentQueries(private val max: Int = MAX) : RecentQueries {
    private val _queries = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    override val queries: StateFlow<Map<String, List<String>>> = _queries.asStateFlow()

    override fun record(profileId: String, query: String) {
        val text = query.trim()
        val key = SearchNormalizer.normalize(text)
        if (key.isEmpty()) return
        val list = _queries.value[profileId].orEmpty()
        val updated = (listOf(text) + list.filterNot { SearchNormalizer.normalize(it) == key }).take(max)
        _queries.value = _queries.value + (profileId to updated)
        onChanged()
    }

    override fun remove(profileId: String, query: String) {
        _queries.value = _queries.value + (profileId to (_queries.value[profileId].orEmpty() - query))
        onChanged()
    }

    protected open fun onChanged() = Unit

    internal fun restore(saved: Map<String, List<String>>) {
        _queries.value = saved.mapValues { it.value.take(max) }
    }

    companion object {
        const val MAX = 5

        /** Рядки «профіль ⇥ запит», найновіші першими. */
        fun encode(queries: Map<String, List<String>>): String =
            queries.flatMap { (profile, list) -> list.map { "$profile\t$it" } }.joinToString("\n")

        fun decode(text: String?): Map<String, List<String>> {
            val result = linkedMapOf<String, MutableList<String>>()
            text.orEmpty().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val tab = line.indexOf('\t')
                // Старий формат (до профілів) — без вкладки: запит загальний.
                val (profile, query) = if (tab < 0) "" to line else line.substring(0, tab) to line.substring(tab + 1)
                result.getOrPut(profile) { mutableListOf() } += query
            }
            return result
        }
    }
}
