package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blacklaunch.launcher.system.WallpaperTarget

@Composable
fun BlackWallpaperDialog(
    onConfirm: (WallpaperTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Чорні шпалери") },
        text = {
            Text(
                "Системні шпалери стануть чорними, і старі не з'являтимуться в анімаціях " +
                    "кнопки «Додому» та останніх програм. Поточні шпалери BlackLaunch не збереже: " +
                    "повернути їх можна лише вручну в налаштуваннях телефона.",
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onConfirm(WallpaperTarget.HOME_AND_LOCK) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Головний екран і екран блокування")
                }
                TextButton(onClick = { onConfirm(WallpaperTarget.HOME) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Тільки головний екран")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Скасувати")
                }
            }
        },
    )
}
