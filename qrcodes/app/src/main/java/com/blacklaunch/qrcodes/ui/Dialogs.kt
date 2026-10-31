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
import androidx.compose.foundation.layout.width
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
import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.qr.Symbol
import com.blacklaunch.qrcodes.qr.codeFormat
import com.blacklaunch.qrcodes.ui.theme.Oled

/** Вибір способу вводу (кнопка всередині капсули). */
@Composable
fun ModeMenu(onDismiss: () -> Unit, onPick: (InputMode) -> Unit, onForm: (FormKind) -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.mode_choose)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            MenuRow(Glyph.Keyboard, stringResource(R.string.mode_manual), { onPick(InputMode.Manual) })
            MenuRow(Glyph.ScanQr, stringResource(R.string.mode_scan), { onPick(InputMode.ScanQr) })
            MenuRow(Glyph.Photo, stringResource(R.string.mode_photo), { onPick(InputMode.PhotoText) })
            // Готові форми: телефон, що сканує такий код, сам пропонує підключитися, зберегти контакт, відкрити сайт.
            Text(stringResource(R.string.forms_title), color = Oled.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
            MenuRow(Glyph.Wifi, stringResource(R.string.form_wifi), { onForm(FormKind.Wifi) })
            MenuRow(Glyph.Person, stringResource(R.string.form_contact), { onForm(FormKind.Contact) })
            MenuRow(Glyph.Globe, stringResource(R.string.form_site), { onForm(FormKind.Site) })
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
    val symbol = remember(code.content, code.format) { Symbol.encode(code.content, code.codeFormat) }
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
                val bars = code.codeFormat == CodeFormat.BARCODE
                CodeImage(
                    symbol,
                    if (bars) Modifier.width(barPreviewWidth(screen - 32.dp)) else Modifier.size(qrPreviewSide(screen - 32.dp)),
                    seed = code.id.toInt(),
                    radius = 14.dp,
                    barcode = bars,
                )
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
fun SettingsDialog(onDismiss: () -> Unit, onLanguageChanged: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
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
            // Резервна копія: усі коди — у файл (наприклад, у «Завантаження» чи на диск) і назад.
            Text(stringResource(R.string.settings_backup), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            MenuRow(Glyph.Download, stringResource(R.string.backup_export), onExport)
            MenuRow(Glyph.Copy, stringResource(R.string.backup_import), onImport)
            Text(stringResource(R.string.backup_hint), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
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

/** Форми для QR-кодів, які телефон розуміє сам. */
enum class FormKind { Wifi, Contact, Site }

/**
 * Форма: заповнюєш поля — у поле вводу йде готовий текст коду, а назва (мережа, ім'я, сайт) стане назвою коду, тож
 * пароль чи телефон ніде не підписуються. [onDone] — (текст коду, назва).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PayloadForm(kind: FormKind, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var security by remember { mutableStateOf(com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA) }
    var hidden by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = stringResource(
        when (kind) {
            FormKind.Wifi -> R.string.form_wifi
            FormKind.Contact -> R.string.form_contact
            FormKind.Site -> R.string.form_site
        },
    )
    val ready = when (kind) {
        FormKind.Wifi -> a.isNotBlank()
        FormKind.Contact -> a.isNotBlank() && (b.isNotBlank() || c.isNotBlank())
        FormKind.Site -> a.isNotBlank()
    }
    SheetDialog(onDismissRequest = onDismiss, title = title, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.done), {
            val p = com.blacklaunch.qrcodes.qr.Payloads
            when (kind) {
                FormKind.Wifi -> onDone(p.wifi(a.trim(), b, security, hidden), "Wi‑Fi: ${a.trim()}")
                FormKind.Contact -> onDone(p.contact(a, b, c, d), a.trim())
                FormKind.Site -> onDone(p.url(a), p.siteName(a))
            }
        }, tone = Tone.Confirm, enabled = ready)
    }) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (kind) {
                FormKind.Wifi -> {
                    SheetTextField(a, { a = it }, stringResource(R.string.form_wifi_ssid), focusRequester = focus)
                    if (security != com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.NONE) {
                        SheetTextField(b, { b = it }, stringResource(R.string.form_wifi_password), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                    }
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("WPA/WPA2", security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA }
                        ChoiceChip("WEP", security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WEP) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WEP }
                        ChoiceChip(stringResource(R.string.form_wifi_open), security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.NONE) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.NONE }
                        ChoiceChip(stringResource(R.string.form_wifi_hidden), hidden) { hidden = !hidden }
                    }
                }
                FormKind.Contact -> {
                    SheetTextField(a, { a = it }, stringResource(R.string.form_contact_name), focusRequester = focus)
                    SheetTextField(b, { b = it }, stringResource(R.string.form_contact_phone), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                    SheetTextField(c, { c = it }, stringResource(R.string.form_contact_email), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                    SheetTextField(d, { d = it }, stringResource(R.string.form_contact_org))
                }
                FormKind.Site -> {
                    SheetTextField(a, { a = it }, stringResource(R.string.form_site_address), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri), focusRequester = focus)
                }
            }
            Text(stringResource(R.string.form_hint), fontSize = 14.sp)
        }
    }
}
