package com.offlinekeyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.offlinekeyboard.core.Shortcut
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.keyboard.Accent
import com.offlinekeyboard.keyboard.ThemeMode
import com.offlinekeyboard.keyboard.StickerCharacter
import com.offlinekeyboard.keyboard.VibrationStrength
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
    /** How strongly keys vibrate when [hapticFeedback] is on. */
    val vibration: VibrationStrength = VibrationStrength.MEDIUM,
    /** Lingo in the stickers: a boy or a girl. */
    val stickerCharacter: StickerCharacter = StickerCharacter.BOY,
    /** The user's own emoji and text smileys for the emoji panel, separated by spaces. */
    val ownEmoji: String = "",
    /** Null: dictate in the current keyboard language. */
    val voiceLanguage: Language? = null,
    val voiceCommands: Boolean = true,
    val shortcuts: List<Shortcut> = Shortcut.defaults,
    /** Off by default: no input history is collected unless the user turns it on. */
    val learnWords: Boolean = false,
    val showSuggestions: Boolean = true,
    val translateEnabled: Boolean = false,
    val translationDirection: TranslationDirection = TranslationDirection.UK_TO_PL,
    /** True: the field shows the translation while typing. False: Enter translates the typed segment. */
    val translationAuto: Boolean = true,
    /** Automatic mode: insert the translation after this pause in typing; 0 = only at . ! ? and Enter. */
    val translationPauseMs: Int = 2000,
    /** Beam search for the inserted translation (more faithful, a little slower). */
    val translationAccurate: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.SKY,
    /** The phone also works as a Bluetooth keyboard for a computer (off by default). */
    val bluetoothKeyboard: Boolean = false,
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
            val lastMode = updated.lastMode
            if (lastMode != null) prefs[Keys.LAST_MODE] = lastMode.name else prefs.remove(Keys.LAST_MODE)
            prefs[Keys.LAST_LANGUAGE] = updated.lastLanguage.name
            prefs[Keys.ENABLED_LANGUAGES] = updated.enabledLanguages.map { it.name }.toSet()
            prefs[Keys.MOBILE_CURSOR_ROW] = updated.mobileCursorRow
            prefs[Keys.KEY_HEIGHT_SCALE] = updated.keyHeightScale
            prefs[Keys.AUTO_CAPITALIZE] = updated.autoCapitalize
            prefs[Keys.SPACE_SWIPE] = updated.spaceSwipeSwitchesLanguage
            prefs[Keys.HAPTIC] = updated.hapticFeedback
            prefs[Keys.VIBRATION] = updated.vibration.name
            prefs[Keys.STICKER_CHARACTER] = updated.stickerCharacter.name
            prefs[Keys.OWN_EMOJI] = updated.ownEmoji
            val voice = updated.voiceLanguage
            if (voice != null) prefs[Keys.VOICE_LANGUAGE] = voice.name else prefs.remove(Keys.VOICE_LANGUAGE)
            prefs[Keys.VOICE_COMMANDS] = updated.voiceCommands
            prefs[Keys.SHORTCUTS] = Shortcut.encode(updated.shortcuts)
            prefs[Keys.LEARN_WORDS] = updated.learnWords
            prefs[Keys.SHOW_SUGGESTIONS] = updated.showSuggestions
            prefs[Keys.TRANSLATE_ENABLED] = updated.translateEnabled
            prefs[Keys.TRANSLATION_DIRECTION] = updated.translationDirection.name
            prefs[Keys.TRANSLATION_AUTO] = updated.translationAuto
            prefs[Keys.TRANSLATION_PAUSE] = updated.translationPauseMs
            prefs[Keys.TRANSLATION_ACCURATE] = updated.translationAccurate
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.ACCENT] = updated.accent.name
            prefs[Keys.BLUETOOTH_KEYBOARD] = updated.bluetoothKeyboard
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
            vibration = enumOrNull<VibrationStrength>(this[Keys.VIBRATION]) ?: defaults.vibration,
            stickerCharacter = enumOrNull<StickerCharacter>(this[Keys.STICKER_CHARACTER]) ?: defaults.stickerCharacter,
            ownEmoji = this[Keys.OWN_EMOJI] ?: defaults.ownEmoji,
            voiceLanguage = enumOrNull<Language>(this[Keys.VOICE_LANGUAGE]),
            voiceCommands = this[Keys.VOICE_COMMANDS] ?: defaults.voiceCommands,
            shortcuts = this[Keys.SHORTCUTS]?.let { Shortcut.decode(it) }
                ?.let { if (it == Shortcut.previousDefaults) Shortcut.defaults else it } ?: defaults.shortcuts,
            learnWords = this[Keys.LEARN_WORDS] ?: defaults.learnWords,
            showSuggestions = this[Keys.SHOW_SUGGESTIONS] ?: defaults.showSuggestions,
            translateEnabled = this[Keys.TRANSLATE_ENABLED] ?: defaults.translateEnabled,
            translationDirection = enumOrNull<TranslationDirection>(this[Keys.TRANSLATION_DIRECTION])
                ?: defaults.translationDirection,
            translationAuto = this[Keys.TRANSLATION_AUTO] ?: defaults.translationAuto,
            translationPauseMs = this[Keys.TRANSLATION_PAUSE] ?: defaults.translationPauseMs,
            translationAccurate = this[Keys.TRANSLATION_ACCURATE] ?: defaults.translationAccurate,
            themeMode = enumOrNull<ThemeMode>(this[Keys.THEME_MODE]) ?: defaults.themeMode,
            accent = enumOrNull<Accent>(this[Keys.ACCENT]) ?: defaults.accent,
            bluetoothKeyboard = this[Keys.BLUETOOTH_KEYBOARD] ?: defaults.bluetoothKeyboard,
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
        val VIBRATION = stringPreferencesKey("vibration_strength")
        val STICKER_CHARACTER = stringPreferencesKey("sticker_character")
        val OWN_EMOJI = stringPreferencesKey("own_emoji")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val VOICE_COMMANDS = booleanPreferencesKey("voice_commands")
        val SHORTCUTS = stringPreferencesKey("shortcuts")
        val LEARN_WORDS = booleanPreferencesKey("learn_words")
        val SHOW_SUGGESTIONS = booleanPreferencesKey("show_suggestions")
        val TRANSLATE_ENABLED = booleanPreferencesKey("translate_enabled")
        val TRANSLATION_DIRECTION = stringPreferencesKey("translation_direction")
        val TRANSLATION_AUTO = booleanPreferencesKey("translation_auto")
        val TRANSLATION_PAUSE = intPreferencesKey("translation_pause_ms")
        val TRANSLATION_ACCURATE = booleanPreferencesKey("translation_accurate")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val BLUETOOTH_KEYBOARD = booleanPreferencesKey("bluetooth_keyboard")
    }
}
