package com.blacklaunch.launcher.search

import android.content.Context

class PrefsRecentQueries(context: Context) : InMemoryRecentQueries() {
    private val prefs = context.applicationContext.getSharedPreferences("recent_queries", Context.MODE_PRIVATE)

    init {
        restore(decode(prefs.getString(KEY, null)))
    }

    override fun onChanged() {
        prefs.edit().putString(KEY, encode(queries.value)).apply()
    }

    private companion object {
        const val KEY = "queries"
    }
}
