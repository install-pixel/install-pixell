package com.blacklaunch.launcher.settings

import android.content.Context
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.games.GameKind
import com.blacklaunch.launcher.profiles.ProfileDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Готові набори налаштувань (етап 18): з чого почати — далі все можна змінити самому.
 * Кожен набір — оформлення, головний екран і пошук усіх режимів, голос, «Що грає», крапки, котик і своя гра
 * (Мінімум — Змійка, Темп — Зоряний патруль, Затишок — Блоки). Програми, режими, команди й історію не чіпає.
 */
enum class Preset(val game: GameKind) {
    /** Мінімум усього: чорний екран, нічого зайвого, без підказок і звуків. */
    MINIMUM(GameKind.SNAKE),

    /** Швидко перемикатись і запускати: свайпи, підказки, останні запити, огляд, відповіді вголос. */
    TEMPO(GameKind.STARS),

    /** Неспішно: великий годинник і огляд, спокійні підказки, котик, «Що грає», відповіді вголос, без свайпів. */
    COZY(GameKind.BLOCKS),
}

/** Чисті правила набору: як виглядає екран режиму. */
object PresetRules {
    fun display(preset: Preset, current: ProfileDisplay): ProfileDisplay = when (preset) {
        Preset.MINIMUM -> current.copy(
            glance = false, profileLabel = false, hideStatusBar = true, suggestions = false,
            recentQueries = false, allAppsButton = false, ownGlance = null,
        )
        Preset.TEMPO -> current.copy(
            glance = true, profileLabel = true, hideStatusBar = false, suggestions = true,
            recentQueries = true, allAppsButton = true, quickSwitch = true, ownGlance = null,
        )
        Preset.COZY -> current.copy(
            glance = true, profileLabel = true, hideStatusBar = true, suggestions = true,
            recentQueries = false, allAppsButton = true, ownGlance = null,
        )
    }

    fun appearance(preset: Preset, current: Appearance): Appearance = when (preset) {
        Preset.MINIMUM -> current.copy(profileSwipe = false, monochromeIcons = true, haptics = true, glanceClock = false, glanceAlarm = false, glanceEvent = false, glanceBattery = false)
        Preset.TEMPO -> current.copy(profileSwipe = true, monochromeIcons = true, haptics = true, glanceClock = true, glanceAlarm = true, glanceEvent = true, glanceBattery = true)
        Preset.COZY -> current.copy(profileSwipe = false, monochromeIcons = true, haptics = true, glanceClock = true, glanceAlarm = true, glanceEvent = true, glanceBattery = false)
    }
}

/** Який набір вибрано (null — ще не вибирали: перший запуск покаже вибір). */
class PresetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("presets", Context.MODE_PRIVATE)
    private val _chosen = MutableStateFlow(prefs.getString(KEY, null))

    /** Назва набору, «kept» (лишили як було) або null. */
    val chosen: StateFlow<String?> = _chosen.asStateFlow()

    val current: Preset? get() = Preset.entries.firstOrNull { it.name == _chosen.value }

    fun keepAsIs() = mark(KEPT)

    private fun mark(value: String) {
        _chosen.value = value
        prefs.edit().putString(KEY, value).apply()
    }

    /** Застосувати набір до всього, що він охоплює. */
    suspend fun apply(app: BlackLaunchApp, preset: Preset) {
        app.appearanceSettings.update { PresetRules.appearance(preset, it) }
        app.profileRepository.profiles.first().forEach { profile ->
            app.profileDisplay.set(profile.id, PresetRules.display(preset, app.profileDisplay.of(profile.id)))
        }
        app.voiceActionSettings.setSpeakAnswers(preset != Preset.MINIMUM)
        app.wellbeing.setDots(preset != Preset.MINIMUM)
        app.wellbeing.setNowPlaying(preset != Preset.MINIMUM)
        app.focus.setCat(preset == Preset.COZY)
        app.games.setShown(setOf(preset.game))
        mark(preset.name)
    }

    private companion object {
        const val KEY = "chosen"
        const val KEPT = "kept"
    }
}
