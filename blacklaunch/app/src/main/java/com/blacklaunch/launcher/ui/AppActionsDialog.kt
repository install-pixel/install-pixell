package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo

/** Меню за довгим натисканням на програму в пошуку (як у лаунчері Pixel). */
@Composable
fun AppActionsDialog(
    app: AppInfo,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onNames: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    /** Приховати з пошуку BlackLaunch (у системі лишається). */
    onHide: () -> Unit,
    /** Власний значок Black Glass (літери в кружечку) замість значка програми. */
    ownIcon: Boolean = false,
    onToggleOwnIcon: () -> Unit = {},
    /** Ярлики програми (до 4): назва → запуск. */
    shortcuts: List<Pair<String, () -> Unit>> = emptyList(),
    /** «Навчити голосом»: сказати назву — почуте стане власною назвою програми (для офлайн-розпізнавання). */
    onTeachVoice: (() -> Unit)? = null,
    /** «Відкрити» — першим пунктом (довго натиснули випадково — не треба виходити з меню й торкатися знову). */
    onOpen: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(app.label) },
        text = {
            // Пунктів може бути багато (ярлики програми) — список гортається, нижні не обрізаються.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onOpen?.let { open -> ActionOption(R.string.app_open, Icons.Filled.PlayArrow, Tone.Confirm) { onDismiss(); open() } }
                shortcuts.forEach { (title, open) ->
                    ActionOption(title, Icons.Filled.Star) { open(); onDismiss() }
                }
                if (pinned) {
                    ActionOption(R.string.remove_from_home, Icons.Filled.Close) { onTogglePin(); onDismiss() }
                } else {
                    ActionOption(R.string.add_to_home, Icons.Filled.Add) { onTogglePin(); onDismiss() }
                }
                ActionOption(R.string.alias_menu, Icons.Filled.Edit, onNames)
                onTeachVoice?.let { teach -> ActionOption(R.string.teach_voice, Icons.Filled.Edit) { teach(); onDismiss() } }
                ActionOption(if (ownIcon) R.string.icon_original else R.string.icon_own, Icons.Filled.Face) { onToggleOwnIcon(); onDismiss() }
                ActionOption(R.string.hide_from_search, Icons.Filled.Lock) { onHide(); onDismiss() }
                ActionOption(R.string.app_info, Icons.Filled.Info) { onAppInfo(); onDismiss() }
                ActionOption(R.string.app_uninstall, Icons.Filled.Delete, Tone.Danger) { onUninstall(); onDismiss() }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.cancel), onDismiss) },
    )
}
