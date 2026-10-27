package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Share
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.voice.VoiceActionKind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.blacklaunch.launcher.diagnostics.CrashEntries
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.voice.ModelInfo
import androidx.compose.ui.platform.LocalContext
import android.text.format.Formatter
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.settings.OfflineVoiceMode
import com.blacklaunch.launcher.system.WallpaperTarget
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.ModelImportActivity
import com.blacklaunch.launcher.voice.OfflineStatus
import com.blacklaunch.launcher.voice.VoiceLanguages

/** Налаштування: меню розділів і сторінка кожного розділу. */
@Composable
fun SettingsScreen(
    page: SettingsNav,
    onNavigate: (SettingsNav) -> Unit,
    versionName: String,
    appearance: Appearance,
    onChange: ((Appearance) -> Appearance) -> Unit,
    onSetBlackWallpaperNow: () -> Unit,
    onOpenSystemTheme: () -> Unit,
    offlineStatus: Map<String, OfflineStatus>,
    onCheckOffline: () -> Unit,
    onDownloadOffline: (String) -> Unit,
    installedModels: Set<String>,
    /** Назва й розмір встановлених моделей. */
    modelInfo: Map<String, ModelInfo> = emptyMap(),
    voiceMode: OfflineVoiceMode,
    onVoiceModeChange: (OfflineVoiceMode) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onImportModel: () -> Unit,
    onOpenBattery: () -> Unit,
    globalKinds: Set<GroupKind>,
    onGlobalKindChange: (GroupKind, Boolean) -> Unit,
    onOpenAppInfo: () -> Unit,
    voiceActions: Set<VoiceActionKind>,
    onVoiceActionChange: (VoiceActionKind, Boolean) -> Unit,
    directCall: Boolean,
    onDirectCallChange: (Boolean) -> Unit,
    speakAnswers: Boolean,
    onSpeakAnswersChange: (Boolean) -> Unit,
    /** Встановлені рушії синтезу мовлення: пакет → назва. */
    ttsEngines: List<Pair<String, String>>,
    ttsEngine: String?,
    onTtsEngineChange: (String?) -> Unit,
    onTestVoice: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    /** Приховані з пошуку програми — щоб повернути. */
    hiddenApps: List<AppInfo>,
    onShowApp: (String) -> Unit,
    /** Сторінка «Мої команди» — будує екран, бо їй потрібні програми, профілі й Bluetooth. */
    commandsPage: @Composable () -> Unit,
    /** Сторінка «Монітор системи» — будує екран (потрібні стан доступу й відкриття монітора). */
    monitorPage: @Composable () -> Unit = {},
    onOpenDefaultApps: () -> Unit,
    /** Останні вильоти (найновіший перший) або null. */
    crashLog: String?,
    onCopyCrashLog: () -> Unit,
    /** Резервна копія: зберегти у файл / відновити з файлу. */
    onExportSettings: () -> Unit = {},
    onImportSettings: () -> Unit = {},
    /** Стерти історію запусків, навчання й останні запити. */
    onClearHistory: () -> Unit = {},
    onClearCrashLog: () -> Unit,
    onDismiss: () -> Unit,
    /** «Головний екран»: крапки сповіщень і «Що грає» (свої налаштування — окремо від оформлення). */
    homeExtras: @Composable () -> Unit = {},
) {
    val toMain = { onNavigate(SettingsNav.Main) }
    when (page) {
        SettingsNav.HomeScreen -> SettingsPage(stringResource(R.string.section_home), toMain) {
            Card {
                SwitchRow(
                    title = stringResource(R.string.profile_swipe_title),
                    subtitle = stringResource(R.string.profile_swipe_subtitle),
                    checked = appearance.profileSwipe,
                    onCheckedChange = { on -> onChange { it.copy(profileSwipe = on) } },
                )
                Note(stringResource(R.string.double_tap_hint))
            }
            Card {
                Text(stringResource(R.string.glance_parts_title), color = Oled.TextPrimary, fontSize = 17.sp)
                SwitchRow(stringResource(R.string.glance_part_clock), "", appearance.glanceClock) { on -> onChange { it.copy(glanceClock = on) } }
                SwitchRow(stringResource(R.string.glance_part_alarm), "", appearance.glanceAlarm) { on -> onChange { it.copy(glanceAlarm = on) } }
                SwitchRow(stringResource(R.string.glance_part_event), "", appearance.glanceEvent) { on -> onChange { it.copy(glanceEvent = on) } }
                SwitchRow(stringResource(R.string.glance_part_battery), "", appearance.glanceBattery) { on -> onChange { it.copy(glanceBattery = on) } }
                Note(stringResource(R.string.glance_parts_note))
            }
            Card {
                Note(stringResource(R.string.display_moved_home))
                ActionOption(R.string.settings_profiles, Icons.Filled.Person) { onNavigate(SettingsNav.Profiles) }
            }
            Card { homeExtras() }
            Card {
                Note(stringResource(R.string.grid_hint))
            }
        }

        SettingsNav.Appearance -> SettingsPage(stringResource(R.string.section_appearance), toMain) {
            Card {
                SwitchRow(
                    title = stringResource(R.string.sheet_title),
                    subtitle = stringResource(R.string.sheet_subtitle),
                    checked = appearance.sheet,
                    onCheckedChange = { on -> onChange { it.copy(sheet = on) } },
                )
                Note(stringResource(R.string.sheet_note))
            }
            Card {
                SwitchRow(
                    title = stringResource(R.string.auto_wallpaper_title),
                    subtitle = stringResource(R.string.auto_wallpaper_subtitle),
                    checked = appearance.autoBlackWallpaper,
                    onCheckedChange = { on -> onChange { it.copy(autoBlackWallpaper = on) } },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetOption(R.string.target_home_lock, appearance.wallpaperTarget == WallpaperTarget.HOME_AND_LOCK) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME_AND_LOCK) }
                    }
                    TargetOption(R.string.target_home, appearance.wallpaperTarget == WallpaperTarget.HOME) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME) }
                    }
                    // Разово, навіть коли автоматичний режим вимкнено.
                    ActionOption(R.string.wallpaper_now, Icons.Filled.Refresh, onSetBlackWallpaperNow)
                }
            }
            Card {
                SwitchRow(
                    title = stringResource(R.string.mono_icons_title),
                    subtitle = stringResource(R.string.mono_icons_subtitle),
                    checked = appearance.monochromeIcons,
                    onCheckedChange = { on -> onChange { it.copy(monochromeIcons = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.haptics_title),
                    subtitle = stringResource(R.string.haptics_subtitle),
                    checked = appearance.haptics,
                    onCheckedChange = { on -> onChange { it.copy(haptics = on) } },
                )
            }
            Card {
                ActionOption(R.string.system_theme, Icons.Filled.Settings, onOpenSystemTheme)
                Note(stringResource(R.string.system_theme_note))
            }
        }

        SettingsNav.Search -> SettingsPage(stringResource(R.string.section_search), toMain) {
            if (hiddenApps.isNotEmpty()) {
                Card {
                    Text(stringResource(R.string.hidden_apps_title), color = Oled.TextPrimary, fontSize = 17.sp)
                    Note(stringResource(R.string.hidden_apps_note))
                    hiddenApps.forEach { hidden ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(hidden.label, color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            SmallButton(stringResource(R.string.hidden_apps_show)) { onShowApp(hidden.key) }
                        }
                    }
                }
            }
            var confirmClear by remember { mutableStateOf(false) }
            Card {
                Note(stringResource(R.string.history_note))
                ActionOption(R.string.history_clear, Icons.Filled.Delete, Tone.Danger) { confirmClear = true }
            }
            if (confirmClear) {
                GlassDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { DialogTitle(stringResource(R.string.history_clear)) },
                    text = { Text(stringResource(R.string.history_clear_confirm), color = Oled.TextSecondary) },
                    confirmButton = {
                        DialogButton(stringResource(R.string.history_clear_yes), {
                            confirmClear = false
                            onClearHistory()
                        }, Tone.Danger)
                    },
                    dismissButton = { DialogButton(stringResource(R.string.cancel), { confirmClear = false }) },
                )
            }
            Card {
                Note(stringResource(R.string.display_moved_search))
                ActionOption(R.string.settings_profiles, Icons.Filled.Person) { onNavigate(SettingsNav.Profiles) }
            }
            Card {
                Text(stringResource(R.string.global_section), color = Oled.TextPrimary, fontSize = 17.sp)
                GroupKind.entries.forEach { kind ->
                    SwitchRow(
                        title = stringResource(kind.label()),
                        subtitle = "",
                        checked = kind in globalKinds,
                        onCheckedChange = { onGlobalKindChange(kind, it) },
                    )
                }
                Note(stringResource(R.string.global_note))
            }
            // Довідка «як увімкнути SMS» — лише поки SMS не ввімкнено; далі вона лише займає місце.
            if (GroupKind.SMS !in globalKinds) {
                Card {
                    Text(stringResource(R.string.sms_help_title), color = Oled.TextPrimary, fontSize = 17.sp)
                    Note(stringResource(R.string.sms_help_new))
                    Note(stringResource(R.string.sms_help_old))
                    ActionOption(R.string.sms_help_open, Icons.Filled.Settings, onOpenAppInfo)
                }
            }
            Card {
                Note(stringResource(R.string.search_names_note))
            }
        }

        SettingsNav.Voice -> SettingsPage(stringResource(R.string.section_voice), toMain) {
            Card {
                MenuRow(Icons.Filled.PlayArrow, R.string.custom_commands_title, R.string.custom_commands_menu_hint) {
                    onNavigate(SettingsNav.CustomCommands)
                }
                MenuRow(painterResource(R.drawable.ic_mic), R.string.builtin_commands_title, R.string.builtin_commands_hint) {
                    onNavigate(SettingsNav.VoiceActions)
                }
                MenuRow(Icons.Filled.Settings, R.string.recognition_title, R.string.recognition_hint) {
                    onNavigate(SettingsNav.Recognition)
                }
            }
        }

        SettingsNav.Recognition -> SettingsPage(stringResource(R.string.recognition_title), { onNavigate(SettingsNav.Voice) }) {
            LaunchedEffect(Unit) { onCheckOffline() }
            Card {
                Text(stringResource(R.string.models_section), color = Oled.TextPrimary, fontSize = 17.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfflineVoiceMode.entries.forEach { mode ->
                        TargetOption(mode.label(), voiceMode == mode) { onVoiceModeChange(mode) }
                    }
                }
                VoiceLanguages.ALL.forEach { language ->
                    ModelRow(language, language in installedModels, modelInfo[language], onDownloadModel, onDeleteModel)
                }
                ActionOption(R.string.model_import, Icons.Filled.Add, onImportModel)
                Note(stringResource(R.string.models_note))
            }
            Card {
                Text(stringResource(R.string.voice_section), color = Oled.TextPrimary, fontSize = 17.sp)
                VoiceLanguages.relevant().forEach { language ->
                    OfflineRow(language, offlineStatus[language], onDownloadOffline)
                }
                Note(stringResource(R.string.voice_section_note))
            }
            Card {
                SwitchRow(
                    title = stringResource(R.string.speak_answers_title),
                    subtitle = stringResource(R.string.speak_answers_subtitle),
                    checked = speakAnswers,
                    onCheckedChange = onSpeakAnswersChange,
                )
            }
            if (speakAnswers) {
                Card {
                    Text(stringResource(R.string.tts_engine_title), color = Oled.TextPrimary, fontSize = 17.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetOption(R.string.tts_engine_system, ttsEngine == null) { onTtsEngineChange(null) }
                        ttsEngines.forEach { (pkg, label) ->
                            TargetOption(label, ttsEngine == pkg) { onTtsEngineChange(pkg) }
                        }
                    }
                    Note(stringResource(R.string.tts_engine_note))
                    ActionOption(R.string.tts_test, Icons.Filled.PlayArrow, onTestVoice)
                    ActionOption(R.string.tts_settings, Icons.Filled.Settings, onOpenTtsSettings)
                }
            }
            Card {
                Text(stringResource(R.string.assistant_title), color = Oled.TextPrimary, fontSize = 17.sp)
                Note(stringResource(R.string.assistant_note))
                ActionOption(R.string.assistant_open, Icons.Filled.Settings, onOpenDefaultApps)
            }
        }

        SettingsNav.VoiceActions -> SettingsPage(stringResource(R.string.builtin_commands_title), { onNavigate(SettingsNav.Voice) }) {
            // Три групи замість 11 перемикачів; «Детальніше» — окремі перемикачі для тонкого налаштування.
            var details by remember { mutableStateOf(false) }
            Card { Note(stringResource(R.string.voice_actions_note)) }
            VOICE_GROUPS.forEach { group ->
                Card {
                    val on = group.kinds.count { it in voiceActions }
                    SwitchRow(
                        title = stringResource(group.title),
                        subtitle = if (on in 1 until group.kinds.size) {
                            stringResource(R.string.vgroup_partial, on, group.kinds.size)
                        } else {
                            stringResource(group.example)
                        },
                        checked = on > 0,
                        onCheckedChange = { enabled -> group.kinds.forEach { onVoiceActionChange(it, enabled) } },
                    )
                    if (details) {
                        group.kinds.forEach { kind ->
                            SwitchRow(
                                title = stringResource(kind.title()),
                                subtitle = stringResource(kind.example()),
                                checked = kind in voiceActions,
                                onCheckedChange = { onVoiceActionChange(kind, it) },
                            )
                        }
                    }
                    if (VoiceActionKind.CALLS in group.kinds && VoiceActionKind.CALLS in voiceActions) {
                        SwitchRow(
                            title = stringResource(R.string.direct_call_title),
                            subtitle = stringResource(R.string.direct_call_subtitle),
                            checked = directCall,
                            onCheckedChange = onDirectCallChange,
                        )
                    }
                }
            }
            ActionOption(if (details) R.string.details_hide else R.string.details_show, Icons.Filled.Settings) { details = !details }
        }

        SettingsNav.Monitor -> SettingsPage(stringResource(R.string.section_monitor), toMain) {
            monitorPage()
        }

        SettingsNav.CustomCommands -> SettingsPage(stringResource(R.string.custom_commands_title), { onNavigate(SettingsNav.Voice) }) {
            commandsPage()
        }

        SettingsNav.Battery -> SettingsPage(stringResource(R.string.battery_section), toMain) {
            Card {
                Note(stringResource(R.string.battery_tip))
                ActionOption(R.string.battery_open, Icons.Filled.Lock, onOpenBattery)
            }
            Card {
                Note(stringResource(R.string.battery_profiles_note))
                ActionOption(R.string.battery_apps_open, Icons.Filled.List) { onNavigate(SettingsNav.BatteryApps) }
            }
        }

        SettingsNav.About -> SettingsPage(stringResource(R.string.section_about), toMain) {
            var confirmRestore by remember { mutableStateOf(false) }
            Card {
                Text(stringResource(R.string.backup_title), color = Oled.TextPrimary, fontSize = 16.sp)
                Note(stringResource(R.string.backup_note))
                ActionOption(R.string.backup_export, Icons.Filled.Share, onExportSettings)
                ActionOption(R.string.backup_import, Icons.Filled.Refresh) { confirmRestore = true }
            }
            if (confirmRestore) {
                GlassDialog(
                    onDismissRequest = { confirmRestore = false },
                    title = { DialogTitle(stringResource(R.string.backup_import)) },
                    text = { Text(stringResource(R.string.backup_import_confirm), color = Oled.TextSecondary) },
                    confirmButton = {
                        // Замінює поточні налаштування — червоним, як і все, що стирає.
                        DialogButton(stringResource(R.string.backup_import_choose), {
                            confirmRestore = false
                            onImportSettings()
                        }, Tone.Danger)
                    },
                    dismissButton = { DialogButton(stringResource(R.string.cancel), { confirmRestore = false }) },
                )
            }
            Card {
                Text(stringResource(R.string.app_name) + " $versionName", color = Oled.TextPrimary, fontSize = 17.sp)
                Note(stringResource(R.string.about_note))
            }
            Card {
                Text(stringResource(R.string.crash_log_title), color = Oled.TextPrimary, fontSize = 16.sp)
                if (crashLog == null) {
                    Note(stringResource(R.string.crash_log_empty))
                } else {
                    Note(stringResource(R.string.crash_log_hint))
                    Text(
                        CrashEntries.latest(crashLog),
                        color = Oled.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = CRASH_PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(stringResource(R.string.crash_log_copy), onCopyCrashLog)
                        SmallButton(stringResource(R.string.crash_log_clear), Tone.Danger, onClearCrashLog)
                    }
                }
            }
        }

        else -> SettingsPage(stringResource(R.string.settings_title), onDismiss) {
            Card {
                MenuRow(Icons.Filled.Done, R.string.presets_title, R.string.presets_menu_hint) { onNavigate(SettingsNav.Presets) }
                MenuRow(Icons.Filled.Home, R.string.section_home, R.string.section_home_hint) { onNavigate(SettingsNav.HomeScreen) }
                MenuRow(Icons.Filled.Person, R.string.settings_profiles, R.string.section_profiles_hint) { onNavigate(SettingsNav.Profiles) }
                MenuRow(painterResource(R.drawable.ic_timer), R.string.wb_title, R.string.wb_menu_hint) { onNavigate(SettingsNav.Wellbeing) }
                MenuRow(Icons.Filled.Star, R.string.games_title, R.string.games_menu_hint) { onNavigate(SettingsNav.Games) }
                MenuRow(painterResource(R.drawable.ic_palette), R.string.section_appearance, R.string.section_appearance_hint) {
                    onNavigate(SettingsNav.Appearance)
                }
                MenuRow(Icons.Filled.Search, R.string.section_search, R.string.section_search_hint) { onNavigate(SettingsNav.Search) }
                MenuRow(painterResource(R.drawable.ic_mic), R.string.section_voice, R.string.section_voice_hint) {
                    onNavigate(SettingsNav.Voice)
                }
                MenuRow(painterResource(R.drawable.ic_battery), R.string.battery_section, R.string.section_battery_hint) {
                    onNavigate(SettingsNav.Battery)
                }
                MenuRow(Icons.Filled.Info, R.string.section_monitor, R.string.section_monitor_hint) { onNavigate(SettingsNav.Monitor) }
                MenuRow(Icons.Filled.Lock, R.string.section_permissions, R.string.section_permissions_hint) { onNavigate(SettingsNav.Permissions) }
                MenuRow(Icons.Filled.Info, R.string.section_about, null) { onNavigate(SettingsNav.About) }
            }
        }
    }
}

private const val CRASH_PREVIEW_LINES = 14

@StringRes
private fun VoiceActionKind.title(): Int = when (this) {
    VoiceActionKind.ALARM -> R.string.action_alarm
    VoiceActionKind.FLASHLIGHT -> R.string.action_flashlight
    VoiceActionKind.MEDIA -> R.string.action_media
    VoiceActionKind.PANELS -> R.string.action_panels
    VoiceActionKind.DISPLAY -> R.string.action_display
    VoiceActionKind.NAVIGATION -> R.string.action_navigation
    VoiceActionKind.CALLS -> R.string.action_calls
    VoiceActionKind.SMS -> R.string.action_sms
    VoiceActionKind.MESSENGERS -> R.string.action_messengers
    VoiceActionKind.CALENDAR -> R.string.action_calendar
    VoiceActionKind.TIME -> R.string.action_time
}

@StringRes
private fun VoiceActionKind.example(): Int = when (this) {
    VoiceActionKind.ALARM -> R.string.action_alarm_example
    VoiceActionKind.FLASHLIGHT -> R.string.action_flashlight_example
    VoiceActionKind.MEDIA -> R.string.action_media_example
    VoiceActionKind.PANELS -> R.string.action_panels_example
    VoiceActionKind.DISPLAY -> R.string.action_display_example
    VoiceActionKind.NAVIGATION -> R.string.action_navigation_example
    VoiceActionKind.CALLS -> R.string.action_calls_example
    VoiceActionKind.SMS -> R.string.action_sms_example
    VoiceActionKind.MESSENGERS -> R.string.action_messengers_example
    VoiceActionKind.CALENDAR -> R.string.action_calendar_example
    VoiceActionKind.TIME -> R.string.action_time_example
}

@StringRes
private fun OfflineVoiceMode.label(): Int = when (this) {
    OfflineVoiceMode.OFF -> R.string.mode_off
    OfflineVoiceMode.WHEN_OFFLINE -> R.string.mode_when_offline
    OfflineVoiceMode.ALWAYS -> R.string.mode_always
}

@Composable
private fun ModelRow(
    language: String,
    installed: Boolean,
    info: ModelInfo?,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(ModelImportActivity.languageName(language)), color = Oled.TextPrimary, fontSize = 16.sp)
            // Яка саме модель стоїть і скільки займає — щоб через тиждень не гадати, мала чи велика.
            val status = if (installed && info != null) {
                listOf(info.name ?: stringResource(R.string.model_installed), Formatter.formatShortFileSize(context, info.bytes)).joinToString(" · ")
            } else {
                stringResource(if (installed) R.string.model_installed else R.string.model_missing)
            }
            Text(status, color = Oled.TextSecondary, fontSize = 13.sp)
        }
        if (installed) {
            SmallButton(stringResource(R.string.model_delete), Tone.Danger) { onDelete(language) }
        } else {
            SmallButton(stringResource(R.string.download)) { onDownload(language) }
        }
    }
}


@Composable
private fun OfflineRow(language: String, status: OfflineStatus?, onDownload: (String) -> Unit) {
    val name = stringResource(
        when (language) {
            VoiceLanguages.UKRAINIAN -> R.string.lang_uk
            VoiceLanguages.POLISH -> R.string.lang_pl
            else -> R.string.lang_en
        },
    )
    val statusText = stringResource(
        when (status) {
            null -> R.string.offline_checking
            OfflineStatus.INSTALLED -> R.string.offline_installed
            OfflineStatus.DOWNLOADING -> R.string.offline_downloading
            OfflineStatus.DOWNLOADABLE -> R.string.offline_downloadable
            OfflineStatus.UNSUPPORTED -> R.string.offline_unsupported
            OfflineStatus.UNKNOWN -> R.string.offline_unknown
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Oled.TextPrimary, fontSize = 16.sp)
            Text(statusText, color = Oled.TextSecondary, fontSize = 13.sp)
        }
        if (status == OfflineStatus.DOWNLOADABLE) {
            SmallButton(stringResource(R.string.download)) { onDownload(language) }
        }
    }
}

@StringRes
private fun GroupKind.label(): Int = when (this) {
    GroupKind.CALCULATOR -> R.string.group_calculator
    GroupKind.PROFILES -> R.string.group_profiles
    GroupKind.SETTINGS -> R.string.group_settings
    GroupKind.SHORTCUTS -> R.string.group_shortcuts
    GroupKind.CONTACTS -> R.string.group_contacts
    GroupKind.CALENDAR -> R.string.group_calendar
    GroupKind.SMS -> R.string.group_sms
    GroupKind.CALLS -> R.string.group_calls
    GroupKind.NOTIFICATIONS -> R.string.group_notifications
    GroupKind.MUSIC -> R.string.group_music
    GroupKind.VIDEO -> R.string.group_video
    GroupKind.PHOTOS -> R.string.group_photos
}

/** Група вбудованих команд у налаштуваннях: назва, приклад фраз і що входить. */
private class VoiceGroup(@StringRes val title: Int, @StringRes val example: Int, val kinds: List<VoiceActionKind>)

private val VOICE_GROUPS = listOf(
    VoiceGroup(R.string.vgroup_calls, R.string.vgroup_calls_example, listOf(VoiceActionKind.CALLS, VoiceActionKind.SMS, VoiceActionKind.MESSENGERS)),
    VoiceGroup(
        R.string.vgroup_phone,
        R.string.vgroup_phone_example,
        listOf(VoiceActionKind.ALARM, VoiceActionKind.FLASHLIGHT, VoiceActionKind.DISPLAY, VoiceActionKind.PANELS, VoiceActionKind.MEDIA),
    ),
    VoiceGroup(R.string.vgroup_other, R.string.vgroup_other_example, listOf(VoiceActionKind.NAVIGATION, VoiceActionKind.CALENDAR, VoiceActionKind.TIME)),
)
