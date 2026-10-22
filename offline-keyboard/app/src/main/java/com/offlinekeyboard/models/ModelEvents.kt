package com.offlinekeyboard.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings and the keyboard run in one process; the keyboard drops cached models when files change. */
object ModelEvents {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun modelsChanged() {
        _version.value++
    }
}
