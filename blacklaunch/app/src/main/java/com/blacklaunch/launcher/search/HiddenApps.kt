package com.blacklaunch.launcher.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Програми, приховані з пошуку BlackLaunch (у системі лишаються). Ключі програм. */
interface HiddenApps {
    val hidden: StateFlow<Set<String>>
    fun hide(appKey: String)
    fun show(appKey: String)
}

open class InMemoryHiddenApps : HiddenApps {
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    override val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    override fun hide(appKey: String) {
        _hidden.value = _hidden.value + appKey
        onChanged()
    }

    override fun show(appKey: String) {
        _hidden.value = _hidden.value - appKey
        onChanged()
    }

    protected open fun onChanged() = Unit

    internal fun restore(saved: Set<String>) {
        _hidden.value = saved
    }
}
