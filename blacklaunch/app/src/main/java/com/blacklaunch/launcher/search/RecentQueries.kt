package com.blacklaunch.launcher.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Останні запити, з яких щось відкрили чи виконали (найновіший перший). Лише на телефоні. */
interface RecentQueries {
    val queries: StateFlow<List<String>>
    fun record(query: String)
    fun remove(query: String)
}

open class InMemoryRecentQueries(private val max: Int = MAX) : RecentQueries {
    private val _queries = MutableStateFlow<List<String>>(emptyList())
    override val queries: StateFlow<List<String>> = _queries.asStateFlow()

    override fun record(query: String) {
        val text = query.trim()
        val key = SearchNormalizer.normalize(text)
        if (key.isEmpty()) return
        _queries.value = (listOf(text) + _queries.value.filterNot { SearchNormalizer.normalize(it) == key }).take(max)
        onChanged()
    }

    override fun remove(query: String) {
        _queries.value = _queries.value - query
        onChanged()
    }

    protected open fun onChanged() = Unit

    internal fun restore(saved: List<String>) {
        _queries.value = saved.take(max)
    }

    companion object {
        const val MAX = 5
    }
}
