package com.blacklaunch.launcher.search

import android.content.Context

/** Навчання на діях у SharedPreferences, як і решта простих даних BlackLaunch. */
class PrefsLearnedQueries(context: Context) : InMemoryLearnedQueries() {
    private val prefs = context.applicationContext.getSharedPreferences("learned_queries", Context.MODE_PRIVATE)

    init {
        restore(decode(prefs.getString(KEY, null)))
    }

    override fun onChanged() {
        prefs.edit().putString(KEY, encode(learned.value)).apply()
    }

    private companion object {
        const val KEY = "learned"
    }
}
