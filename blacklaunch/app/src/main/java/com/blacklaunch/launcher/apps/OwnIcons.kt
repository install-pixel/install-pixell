package com.blacklaunch.launcher.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Програми, для яких показуємо власний значок Black Glass (літери в кружечку) замість їхнього. */
class OwnIcons(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("own_icons", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun set(appKey: String, own: Boolean) {
        _keys.value = if (own) _keys.value + appKey else _keys.value - appKey
        prefs.edit().putStringSet(KEY, _keys.value).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}

object Monogram {
    /** «Калькулятор» → «Ка», «SIM-Menu» → «SM», «ІЧ-пульт» → «ІП», «PLAY» → «PL». */
    fun of(label: String): String {
        val words = label.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "•"
        if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
        val word = words[0]
        return word.take(1).uppercase() + word.drop(1).take(1).let { if (word.all { c -> !c.isLetter() || c.isUpperCase() }) it else it.lowercase() }
    }
}
