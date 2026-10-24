package com.blacklaunch.launcher.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.blacklaunch.launcher.diagnostics.CrashEntries
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
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
    voiceMode: OfflineVoiceMode,
    onVoiceModeChange: (OfflineVoiceMode) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onImportModel: () -> Unit,
    onOpenBattery: () -> Unit,
    globalKinds: Set<GroupKind>,
    onGlobalKindChange: (GroupKind, Boolean) -> Unit,
    onOpenAppInfo: () -> Unit,
    /** Останні вильоти (найновіший перший) або null. */
    crashLog: String?,
    onCopyCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val toMain = { onNavigate(SettingsNav.Main) }
    when (page) {
        SettingsNav.HomeScreen -> SettingsPage(stringResource(R.string.section_home), toMain) {
            Card {
                ActionOption(R.string.settings_profiles, Icons.Filled.Person) { onNavigate(SettingsNav.Profiles) }
            }
            Card {
                SwitchRow(
                    title = stringResource(R.string.profile_swipe_title),
                    subtitle = stringResource(R.string.profile_swipe_subtitle),
                    checked = appearance.profileSwipe,
                    onCheckedChange = { on -> onChange { it.copy(profileSwipe = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.profile_label_title),
                    subtitle = stringResource(R.string.profile_label_subtitle),
                    checked = appearance.showProfileLabel,
                    onCheckedChange = { on -> onChange { it.copy(showProfileLabel = on) } },
                )
            }
            Card {
                Note(stringResource(R.string.grid_hint))
            }
        }

        SettingsNav.Appearance -> SettingsPage(stringResource(R.string.section_appearance), toMain) {
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
            }
            Card {
                ActionOption(R.string.system_theme, Icons.Filled.Settings, onOpenSystemTheme)
                Note(stringResource(R.string.system_theme_note))
            }
        }

        SettingsNav.Search -> SettingsPage(stringResource(R.string.section_search), toMain) {
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
            Card {
                Text(stringResource(R.string.sms_help_title), color = Oled.TextPrimary, fontSize = 17.sp)
                Note(stringResource(R.string.sms_help_new))
                Note(stringResource(R.string.sms_help_old))
                ActionOption(R.string.sms_help_open, Icons.Filled.Settings, onOpenAppInfo)
            }
            Card {
                Note(stringResource(R.string.search_names_note))
            }
        }

        SettingsNav.Voice -> SettingsPage(stringResource(R.string.section_voice), toMain) {
            LaunchedEffect(Unit) { onCheckOffline() }
            Card {
                Text(stringResource(R.string.models_section), color = Oled.TextPrimary, fontSize = 17.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfflineVoiceMode.entries.forEach { mode ->
                        TargetOption(mode.label(), voiceMode == mode) { onVoiceModeChange(mode) }
                    }
                }
                VoiceLanguages.ALL.forEach { language ->
                    ModelRow(language, language in installedModels, onDownloadModel, onDeleteModel)
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
        }

        SettingsNav.Battery -> SettingsPage(stringResource(R.string.battery_section), toMain) {
            Card {
                Note(stringResource(R.string.battery_tip))
                ActionOption(R.string.battery_open, Icons.Filled.Lock, onOpenBattery)
            }
            Card {
                Note(stringResource(R.string.battery_profiles_note))
            }
        }

        SettingsNav.About -> SettingsPage(stringResource(R.string.section_about), toMain) {
            Card {
                Text("BlackLaunch $versionName", color = Oled.TextPrimary, fontSize = 17.sp)
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
                        SmallButton(stringResource(R.string.crash_log_clear), onClearCrashLog)
                    }
                }
            }
        }

        else -> SettingsPage(stringResource(R.string.settings_title), onDismiss) {
            Card {
                MenuRow(Icons.Filled.Home, R.string.section_home, R.string.section_home_hint) { onNavigate(SettingsNav.HomeScreen) }
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
                MenuRow(Icons.Filled.Info, R.string.section_about, null) { onNavigate(SettingsNav.About) }
            }
        }
    }
}

private const val CRASH_PREVIEW_LINES = 14

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
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(ModelImportActivity.languageName(language)), color = Oled.TextPrimary, fontSize = 16.sp)
            Text(
                stringResource(if (installed) R.string.model_installed else R.string.model_missing),
                color = Oled.TextSecondary,
                fontSize = 13.sp,
            )
        }
        if (installed) {
            SmallButton(stringResource(R.string.model_delete)) { onDelete(language) }
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
}

