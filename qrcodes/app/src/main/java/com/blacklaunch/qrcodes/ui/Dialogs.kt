package com.blacklaunch.qrcodes.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.QrMatrix
import com.blacklaunch.qrcodes.ui.theme.Oled

/** Вибір способу вводу (кнопка всередині капсули). */
@Composable
fun ModeMenu(onDismiss: () -> Unit, onPick: (InputMode) -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.mode_choose)) {
        Column {
            MenuRow(Glyph.Keyboard, stringResource(R.string.mode_manual), { onPick(InputMode.Manual) })
            MenuRow(Glyph.ScanQr, stringResource(R.string.mode_scan), { onPick(InputMode.ScanQr) })
            MenuRow(Glyph.Photo, stringResource(R.string.mode_photo), { onPick(InputMode.PhotoText) })
        }
    }
}

/** Меню збереженого коду (довге натискання). */
@Composable
fun ItemMenu(
    code: QrCode,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    SheetDialog(onDismissRequest = onDismiss, title = code.title) {
        Column {
            MenuRow(
                if (code.isFavorite) Glyph.StarEmpty else Glyph.Star,
                stringResource(if (code.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                onFavorite,
            )
            MenuRow(Glyph.Edit, stringResource(R.string.rename), onRename)
            MenuRow(Glyph.Print, stringResource(R.string.print_share), onShare)
            MenuRow(Glyph.Copy, stringResource(R.string.copy_text), onCopy)
            MenuRow(Glyph.Delete, stringResource(R.string.delete), onDelete, tone = Tone.Danger)
        }
    }
}

/** Своя назва коду. Порожня — підписом знову буде зміст. */
@Composable
fun RenameDialog(code: QrCode, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var name by remember { mutableStateOf(code.name.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.rename), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onApply(name) }, tone = Tone.Confirm)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTextField(
                name,
                { name = it },
                placeholder = displayTitleOf(code),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                focusRequester = focus,
            )
            Text(stringResource(R.string.rename_hint), fontSize = 14.sp)
        }
    }
}

/** Підказка в полі назви: якщо назви немає — зміст; якщо є — не показуємо зміст (там може бути пароль). */
private fun displayTitleOf(code: QrCode): String = if (code.name == null) code.title else ""

/**
 * Код на весь екран — щоб відсканували з телефона. Яскравість екрана на цей час — максимальна.
 */
@Composable
fun FullscreenCode(code: QrCode, onDismiss: () -> Unit, onShare: () -> Unit, onMenu: () -> Unit) {
    val matrix = remember(code.content) { QrMatrix.encode(code.content) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Яскравість — у власному вікні діалогу (воно зверху, система бере яскравість із нього).
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                QrImage(matrix, Modifier.widthIn(max = 480.dp).fillMaxWidth(), seed = code.id.toInt(), radius = 18.dp)
                Text(
                    code.title,
                    color = Oled.TextPrimary,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Glyph.Close, stringResource(R.string.close), onDismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundButton(Glyph.Edit, stringResource(R.string.more), onMenu)
                    RoundButton(Glyph.Print, stringResource(R.string.print_share), onShare)
                }
            }
        }
    }
}
