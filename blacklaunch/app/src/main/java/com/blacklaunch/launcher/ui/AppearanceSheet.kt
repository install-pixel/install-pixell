package com.blacklaunch.launcher.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.system.WallpaperTarget
import com.blacklaunch.launcher.ui.theme.Oled

private val CardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    appearance: Appearance,
    onChange: ((Appearance) -> Appearance) -> Unit,
    onSetBlackWallpaperNow: () -> Unit,
    onOpenSystemTheme: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C0C0C),
        scrimColor = Color(0xB3000000),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Оформлення", color = Oled.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)

            Card {
                SwitchRow(
                    title = "Чорні шпалери автоматично",
                    subtitle = "Якщо шпалери зміняться, BlackLaunch знову зробить їх чорними. " +
                        "Попередні шпалери не зберігаються.",
                    checked = appearance.autoBlackWallpaper,
                    onCheckedChange = { on -> onChange { it.copy(autoBlackWallpaper = on) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetChip("Головний + блокування", appearance.wallpaperTarget == WallpaperTarget.HOME_AND_LOCK) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME_AND_LOCK) }
                    }
                    TargetChip("Тільки головний", appearance.wallpaperTarget == WallpaperTarget.HOME) {
                        onChange { it.copy(wallpaperTarget = WallpaperTarget.HOME) }
                    }
                }
                ActionRow("Зробити чорними зараз", onSetBlackWallpaperNow)
            }

            Card {
                SwitchRow(
                    title = "Сірі значки",
                    subtitle = "Одноколірні значки на об'ємному тлі в результатах пошуку",
                    checked = appearance.monochromeIcons,
                    onCheckedChange = { on -> onChange { it.copy(monochromeIcons = on) } },
                )
            }

            Card {
                ActionRow("Темна тема системи", onOpenSystemTheme)
                Text(
                    "Колір шторки та інших програм задає система. Увімкніть темний режим " +
                        "(в OxygenOS — найтемніший рівень). На Android 12+ палітра шторки " +
                        "береться з кольору шпалер, тож чорні шпалери роблять її нейтральнішою.",
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
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
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
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
