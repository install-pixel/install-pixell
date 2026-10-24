package com.blacklaunch.launcher.settings

import android.content.Context
import com.blacklaunch.launcher.voice.VoiceActionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Які голосові дії ввімкнено і чи дзвонити одразу (з відліком), чи лише відкривати номер. */
class VoiceActionSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice_actions", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(
        VoiceActionKind.entries.filter { prefs.getBoolean(it.name, true) }.toSet(),
    )
    val enabled: StateFlow<Set<VoiceActionKind>> = _enabled.asStateFlow()

    private val _directCall = MutableStateFlow(prefs.getBoolean(KEY_DIRECT_CALL, false))

    /** Дзвонити самому після 3-секундного відліку (потрібен дозвіл на дзвінки). */
    val directCall: StateFlow<Boolean> = _directCall.asStateFlow()

    fun set(kind: VoiceActionKind, on: Boolean) {
        prefs.edit().putBoolean(kind.name, on).apply()
        _enabled.value = if (on) _enabled.value + kind else _enabled.value - kind
    }

    fun setDirectCall(on: Boolean) {
        prefs.edit().putBoolean(KEY_DIRECT_CALL, on).apply()
        _directCall.value = on
    }

    private companion object {
        const val KEY_DIRECT_CALL = "direct_call"
    }
}
