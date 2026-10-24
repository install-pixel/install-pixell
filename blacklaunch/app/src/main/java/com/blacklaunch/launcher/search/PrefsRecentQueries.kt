package com.blacklaunch.launcher.search

import android.content.Context

class PrefsRecentQueries(context: Context) : InMemoryRecentQueries() {
    private val prefs = context.applicationContext.getSharedPreferences("recent_queries", Context.MODE_PRIVATE)

    init {
        restore(prefs.getString(KEY, null).orEmpty().lines().filter { it.isNotBlank() })
    }

    override fun onChanged() {
        prefs.edit().putString(KEY, queries.value.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "queries"
    }
}
