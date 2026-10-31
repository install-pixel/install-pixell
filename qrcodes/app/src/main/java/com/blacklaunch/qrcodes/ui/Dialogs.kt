package com.blacklaunch.qrcodes.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun FullscreenCode(code: QrCode, covered: Boolean, onDismiss: () -> Unit, onMenu: () -> Unit) {
    val matrix = remember(code.content) { QrMatrix.encode(code.content) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Яскравість — у власному вікні діалогу (воно зверху, система бере яскравість із нього). Поки поверх відкрите
        // меню — звичайна яскравість, а код розмитий і притемнений, як усе під меню.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, covered) {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = if (covered) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            onDispose { }
        }
        val blur = backdropAmount(covered)
        Box(Modifier.fillMaxSize().background(Oled.Black).backdrop(blur).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Той самий розмір, що й угорі на «Створити»: великий код сканер не бере зблизька.
                val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                QrImage(matrix, Modifier.size(qrPreviewSide(screen - 32.dp)), seed = code.id.toInt(), radius = 14.dp)
                Text(
                    code.title,
                    color = Oled.TextPrimary,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            // Лише «Закрити» й «Меню» — усі дії (вибране, назва, друк, копіювати, видалити) в одному місці, у меню.
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Glyph.Close, stringResource(R.string.close), onDismiss)
                RoundButton(Glyph.More, stringResource(R.string.more), onMenu)
            }
        }
    }
}

/**
 * Налаштування: мова (як у телефоні або одна з трьох) і стиль («Аркуш» — від руки, «Скло» — Black Glass).
 * Стиль міняється одразу; мова — після короткого перезапуску екрана.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onLanguageChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { com.blacklaunch.qrcodes.settings.AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.settings)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Text(stringResource(R.string.settings_style), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
            ChoiceRow(stringResource(R.string.style_sheet), stringResource(R.string.style_sheet_desc), selected = !AppStyle.glass) {
                AppStyle.glass = false
                settings.glass = false
            }
            ChoiceRow(stringResource(R.string.style_glass), stringResource(R.string.style_glass_desc), selected = AppStyle.glass) {
                AppStyle.glass = true
                settings.glass = true
            }
            Text(stringResource(R.string.settings_language), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            com.blacklaunch.qrcodes.settings.AppLanguage.entries.forEach { lang ->
                // Назви мов — кожна своєю мовою, щоб її легко знайти з будь-якої.
                val title = when (lang) {
                    com.blacklaunch.qrcodes.settings.AppLanguage.System -> stringResource(R.string.lang_system)
                    com.blacklaunch.qrcodes.settings.AppLanguage.Ukrainian -> "Українська"
                    com.blacklaunch.qrcodes.settings.AppLanguage.Polish -> "Polski"
                    com.blacklaunch.qrcodes.settings.AppLanguage.English -> "English"
                }
                ChoiceRow(title, null, selected = language == lang) {
                    if (language != lang) {
                        language = lang
                        settings.language = lang
                        onLanguageChanged()
                    }
                }
            }
        }
    }
}

/** Рядок вибору: назва, пояснення і галочка, якщо вибрано. */
@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Oled.TextSecondary, fontSize = 14.sp)
        }
        if (selected) GlyphIcon(Glyph.Save, null, tint = Oled.Confirm)
    }
}
