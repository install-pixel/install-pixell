package com.offlinekeyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class KeyboardSettings(
    val defaultMode: KeyboardMode = KeyboardMode.MOBILE,
    val rememberLastMode: Boolean = true,
    val lastMode: KeyboardMode? = null,
    val lastLanguage: Language = Language.UK,
    val enabledLanguages: List<Language> = Language.entries,
    val mobileCursorRow: Boolean = false,
    val keyHeightScale: Float = 1f,
    val autoCapitalize: Boolean = true,
    val spaceSwipeSwitchesLanguage: Boolean = true,
    val hapticFeedback: Boolean = true,
    val voiceLanguage: Language = Language.UK,
) {
    val startMode: KeyboardMode get() = if (rememberLastMode) lastMode ?: defaultMode else defaultMode

    companion object {
        const val MIN_KEY_HEIGHT_SCALE = 0.7f
        const val MAX_KEY_HEIGHT_SCALE = 1.4f
    }
}

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard_settings")

/**
 * Settings live only in the app's private storage (DataStore). Nothing typed is stored here,
 * only preferences.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.keyboardDataStore

    val settings: Flow<KeyboardSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.DEFAULT_MODE] = updated.defaultMode.name
            prefs[Keys.REMEMBER_LAST_MODE] = updated.rememberLastMode
            updated.lastMode?.let { prefs[Keys.LAST_MODE] = it.name } ?: prefs.remove(Keys.LAST_MODE)
            prefs[Keys.LAST_LANGUAGE] = updated.lastLanguage.name
            prefs[Keys.ENABLED_LANGUAGES] = updated.enabledLanguages.map { it.name }.toSet()
            prefs[Keys.MOBILE_CURSOR_ROW] = updated.mobileCursorRow
            prefs[Keys.KEY_HEIGHT_SCALE] = updated.keyHeightScale
            prefs[Keys.AUTO_CAPITALIZE] = updated.autoCapitalize
            prefs[Keys.SPACE_SWIPE] = updated.spaceSwipeSwitchesLanguage
            prefs[Keys.HAPTIC] = updated.hapticFeedback
            prefs[Keys.VOICE_LANGUAGE] = updated.voiceLanguage.name
        }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSettings(): KeyboardSettings {
        val defaults = KeyboardSettings()
        val enabled = this[Keys.ENABLED_LANGUAGES]
            ?.mapNotNull { enumOrNull<Language>(it) }
            ?.let { set -> Language.entries.filter { it in set } }
            ?.ifEmpty { null }
        return KeyboardSettings(
            defaultMode = enumOrNull<KeyboardMode>(this[Keys.DEFAULT_MODE]) ?: defaults.defaultMode,
            rememberLastMode = this[Keys.REMEMBER_LAST_MODE] ?: defaults.rememberLastMode,
            lastMode = enumOrNull<KeyboardMode>(this[Keys.LAST_MODE]),
            lastLanguage = enumOrNull<Language>(this[Keys.LAST_LANGUAGE]) ?: defaults.lastLanguage,
            enabledLanguages = enabled ?: defaults.enabledLanguages,
            mobileCursorRow = this[Keys.MOBILE_CURSOR_ROW] ?: defaults.mobileCursorRow,
            keyHeightScale = (this[Keys.KEY_HEIGHT_SCALE] ?: defaults.keyHeightScale)
                .coerceIn(KeyboardSettings.MIN_KEY_HEIGHT_SCALE, KeyboardSettings.MAX_KEY_HEIGHT_SCALE),
            autoCapitalize = this[Keys.AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            spaceSwipeSwitchesLanguage = this[Keys.SPACE_SWIPE] ?: defaults.spaceSwipeSwitchesLanguage,
            hapticFeedback = this[Keys.HAPTIC] ?: defaults.hapticFeedback,
            voiceLanguage = enumOrNull<Language>(this[Keys.VOICE_LANGUAGE]) ?: defaults.voiceLanguage,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val REMEMBER_LAST_MODE = booleanPreferencesKey("remember_last_mode")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val LAST_LANGUAGE = stringPreferencesKey("last_language")
        val ENABLED_LANGUAGES = stringSetPreferencesKey("enabled_languages")
        val MOBILE_CURSOR_ROW = booleanPreferencesKey("mobile_cursor_row")
        val KEY_HEIGHT_SCALE = floatPreferencesKey("key_height_scale")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val SPACE_SWIPE = booleanPreferencesKey("space_swipe_language")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    }
}
