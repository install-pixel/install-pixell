package com.blacklaunch.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled

/** Один доступ: назва, для чого він, чи наданий і що відкрити, щоб змінити. */
data class PermissionItem(val title: String, val purpose: String, val granted: Boolean, val onClick: () -> Unit)

/**
 * Усі доступи Black Glass на одній сторінці. Кожен питається лише тоді, коли вмикають
 * відповідну функцію; тут видно все разом і можна змінити одним дотиком.
 */
@Composable
internal fun PermissionsScreen(items: List<PermissionItem>, onBack: () -> Unit) {
    val haptics = LocalHaptics.current
    SettingsPage(stringResource(R.string.section_permissions), onBack) {
        Card {
            Note(stringResource(R.string.permissions_note))
        }
        Card {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { haptics.tap(); item.onClick() }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Oled.TextPrimary, fontSize = 16.sp)
                        Text(item.purpose, color = Oled.TextSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(if (item.granted) R.string.permission_on else R.string.permission_off),
                        color = if (item.granted) Oled.TextPrimary else Oled.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
