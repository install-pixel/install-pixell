package com.blacklaunch.launcher.profiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Розкладка головного екрана в SharedPreferences: рядки «профіль\tпрограма\tклітинка». */
class PrefsHomeLayoutStore(context: Context) : HomeLayoutStore {
    private val prefs = context.applicationContext.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    private val _layouts = MutableStateFlow(InMemoryHomeLayoutStore.decode(prefs.getString(KEY, null)))
    override val layouts: StateFlow<Map<String, Map<String, Int>>> = _layouts.asStateFlow()

    override fun save(profileId: String, cells: Map<String, Int>) {
        _layouts.value = _layouts.value + (profileId to cells)
        prefs.edit().putString(KEY, InMemoryHomeLayoutStore.encode(_layouts.value)).apply()
    }

    private companion object {
        const val KEY = "cells"
    }
}
