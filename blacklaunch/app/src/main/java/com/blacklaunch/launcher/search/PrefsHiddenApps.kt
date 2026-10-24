package com.blacklaunch.launcher.search

import android.content.Context

class PrefsHiddenApps(context: Context) : InMemoryHiddenApps() {
    private val prefs = context.applicationContext.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)

    init {
        restore(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    }

    override fun onChanged() {
        prefs.edit().putStringSet(KEY, hidden.value).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
