package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.system.WallpaperTarget
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.OfflineStatus
import com.blacklaunch.launcher.voice.VoiceLanguages

private val CardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    appearance: Appearance,
    onChange: ((Appearance) -> Appearance) -> Unit,
    onSetBlackWallpaperNow: () -> Unit,
    onOpenSystemTheme: () -> Unit,
    offlineStatus: Map<String, OfflineStatus>,
    onCheckOffline: () -> Unit,
    onDownloadOffline: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onCheckOffline() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C0C0C),
        scrimColor = Color(0xB3000000),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                color = Oled.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )

            Card {
                SwitchRow(
                    title = stringResource(R.string.auto_wallpaper_title),
                    subtitle = stringResource(R.string.auto_wallpaper_subtitle),
                    checked = appearance.autoBlackWallpaper,
                    onCheckedChange = { on -> onChange { it.copy(autoBlackWallpaper = on) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetChip(R.string.target_home_lock, appearance.wallpaperTarget == WallpaperTarget.HOME_AND_LOCK) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME_AND_LOCK) }
                    }
                    TargetChip(R.string.target_home, appearance.wallpaperTarget == WallpaperTarget.HOME) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME) }
                    }
                }
                ActionRow(stringResource(R.string.wallpaper_now), onSetBlackWallpaperNow)
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
                Text(stringResource(R.string.voice_section), color = Oled.TextPrimary, fontSize = 17.sp)
                VoiceLanguages.relevant().forEach { language ->
                    OfflineRow(language, offlineStatus[language], onDownloadOffline)
                }
                Note(stringResource(R.string.voice_section_note))
            }

            Card {
                ActionRow(stringResource(R.string.system_theme), onOpenSystemTheme)
                Note(stringResource(R.string.system_theme_note))
            }
            Spacer(Modifier.height(8.dp))
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
            Text(
                text = stringResource(R.string.download),
                color = Oled.Accent,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Oled.Knob)
                    .border(1.dp, Oled.Glint, RoundedCornerShape(12.dp))
                    .clickable { onDownload(language) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Oled.TextSecondary, fontSize = 13.sp)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Oled.TextPrimary, fontSize = 17.sp)
            Text(subtitle, color = Oled.TextSecondary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF2F2F2),
                checkedTrackColor = Color(0xFF5A5A5A),
                uncheckedThumbColor = Color(0xFF7A7A7A),
                uncheckedTrackColor = Color(0xFF1E1E1E),
                uncheckedBorderColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

@Composable
private fun TargetChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Oled.TextSecondary,
            selectedLabelColor = Oled.TextPrimary,
            selectedContainerColor = Color(0xFF2E2E2E),
        ),
    )
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Text(
        text = "$title  →",
        color = Oled.Accent,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
