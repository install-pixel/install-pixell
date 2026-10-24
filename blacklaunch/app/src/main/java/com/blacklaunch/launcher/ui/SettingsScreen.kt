package com.blacklaunch.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.settings.OfflineVoiceMode
import com.blacklaunch.launcher.system.WallpaperTarget
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.ModelImportActivity
import com.blacklaunch.launcher.voice.OfflineStatus
import com.blacklaunch.launcher.voice.VoiceLanguages

/** Налаштування на весь екран: довгий список гортається без конфлікту зі згортанням шторки. */
@Composable
fun SettingsScreen(
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
    onOpenProfiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onCheckOffline() }
    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Oled.Black)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Oled.TextPrimary,
                )
            }
            Text(
                stringResource(R.string.settings_title),
                color = Oled.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            Card {
                ActionOption(R.string.settings_profiles, Icons.Filled.Person, onOpenProfiles)
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
            }

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

            Card {
                ActionOption(R.string.system_theme, Icons.Filled.Settings, onOpenSystemTheme)
                Note(stringResource(R.string.system_theme_note))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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





