package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.ui.theme.Oled

/** Меню за довгим натисканням на програму в пошуку (як у лаунчері Pixel). */
@Composable
fun AppActionsDialog(
    app: AppInfo,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onNames: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(app.label, color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pinned) {
                    ActionOption(R.string.remove_from_home, Icons.Filled.Close) { onTogglePin(); onDismiss() }
                } else {
                    ActionOption(R.string.add_to_home, Icons.Filled.Add) { onTogglePin(); onDismiss() }
                }
                ActionOption(R.string.alias_menu, Icons.Filled.Edit, onNames)
                ActionOption(R.string.app_info, Icons.Filled.Info) { onAppInfo(); onDismiss() }
                ActionOption(R.string.app_uninstall, Icons.Filled.Delete) { onUninstall(); onDismiss() }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}
