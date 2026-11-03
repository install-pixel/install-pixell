package com.blacklaunch.qrcodes.ui

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.tween

import androidx.compose.animation.togetherWith

import androidx.compose.animation.slideOutHorizontally

import androidx.compose.animation.slideInHorizontally

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.blacklaunch.qrcodes.qr.codeKind
import com.blacklaunch.qrcodes.ui.theme.Oled

/** Вибір способу вводу (кнопка всередині капсули). */
@Composable
fun ModeMenu(onDismiss: () -> Unit, onPick: (InputMode) -> Unit, onForm: (FormKind) -> Unit, onBatch: (Boolean) -> Unit = {}, onFields: () -> Unit = {}) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.mode_choose)) {
        Column(Modifier.bouncyScroll()) {
            MenuRow(Glyph.Keyboard, stringResource(R.string.mode_manual), { onPick(InputMode.Manual) })
            MenuRow(Glyph.ScanQr, stringResource(R.string.mode_scan), { onPick(InputMode.ScanQr) })
            MenuRow(Glyph.Photo, stringResource(R.string.mode_photo), { onPick(InputMode.PhotoText) })
            // Готові форми: телефон, що сканує такий код, сам пропонує підключитися, зберегти контакт, відкрити сайт.
            Text(stringResource(R.string.forms_title), color = Oled.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
            MenuRow(Glyph.Wifi, stringResource(R.string.form_wifi), { onForm(FormKind.Wifi) })
            MenuRow(Glyph.Person, stringResource(R.string.form_contact), { onForm(FormKind.Contact) })
            MenuRow(Glyph.Globe, stringResource(R.string.form_site), { onForm(FormKind.Site) })
            MenuRow(Glyph.Keyboard, stringResource(R.string.forms_list_title), { onFields() })
            // Багато кодів одразу: серія з номерами чи список — усі в одну папку.
            Text(stringResource(R.string.batch_title), color = Oled.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
            MenuRow(Glyph.Grid, stringResource(R.string.batch_series), { onBatch(false) })
            MenuRow(Glyph.TextT, stringResource(R.string.batch_list), { onBatch(true) })
        }
    }
}

/** Меню збереженого коду (довге натискання). */
@Composable
fun ItemMenu(
    code: QrCode,
    isForm: Boolean = false,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onContent: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onToFolder: () -> Unit,
    onFromFolder: (() -> Unit)?,
    /** «Вибрати кілька» — режим вибору, цей код уже вибраний (null — пункту немає). */
    onSelect: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val action = remember(code.content) { com.blacklaunch.qrcodes.qr.ContentAction.detect(code.content) }
    val noApp = stringResource(R.string.action_no_app)
    SheetDialog(onDismissRequest = onDismiss, title = code.title) {
        Column {
            // Як камера телефона: посилання — відкрити, Wi‑Fi — підключитися, контакт — зберегти…
            if (action != null && !isForm) {
                MenuRow(action.glyph(), stringResource(action.label()), {
                    onDismiss()
                    if (!launch(context, action)) android.widget.Toast.makeText(context, noApp, android.widget.Toast.LENGTH_SHORT).show()
                })
            }
            MenuRow(
                if (code.isFavorite) Glyph.StarEmpty else Glyph.Star,
                stringResource(if (code.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                onFavorite,
            )
            MenuRow(Glyph.Edit, stringResource(R.string.rename), onRename)
            if (isForm) {
                MenuRow(Glyph.Template, stringResource(R.string.forms_edit), onContent)
            } else {
                MenuRow(Glyph.TextT, stringResource(R.string.content_edit), onContent)
                MenuRow(Glyph.Print, stringResource(R.string.print_share), onShare)
                MenuRow(Glyph.Copy, stringResource(R.string.copy_text), onCopy)
            }
            if (onSelect != null) MenuRow(Glyph.Save, stringResource(R.string.select_many), onSelect)
            MenuRow(Glyph.Folder, stringResource(R.string.folder_move), onToFolder)
            if (onFromFolder != null) MenuRow(Glyph.Back, stringResource(R.string.folder_take_out), onFromFolder)
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
    val symbol = remember(code.content, code.format) { Symbol.encode(code.content, code.codeKind) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Яскравість — у власному вікні діалогу (воно зверху, система бере яскравість із нього). Поки поверх відкрите
        // меню — звичайна яскравість, а код розмитий і притемнений, як усе під меню.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Підвищена яскравість — спільне налаштування з формами (кнопка внизу); тоді світиться код, решта приглушена.
        val context = androidx.compose.ui.platform.LocalContext.current
        val settings = remember { com.blacklaunch.qrcodes.settings.AppSettings(context) }
        var bright by remember { mutableStateOf(settings.qrBright) }
        val dim = if (bright && !covered) FORM_DIM else 1f
        DisposableEffect(window, covered, bright) {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = if (covered || !bright) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
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
                    modifier = Modifier.padding(top = 16.dp).graphicsLayer { alpha = dim },
                )
            }
            // Лише «Закрити» й «Меню» — усі дії (вибране, назва, друк, копіювати, видалити) в одному місці, у меню.
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = dim }, horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Glyph.Close, stringResource(R.string.close), onDismiss)
                // Яскравість: увімкнено — значок яскравий, вимкнено — тьмяний.
                RoundButton(
                    Glyph.Flash,
                    stringResource(if (bright) R.string.forms_bright_off else R.string.forms_bright_on),
                    { bright = !bright; settings.qrBright = bright },
                    ink = if (bright) SheetInk else SheetInk.copy(alpha = 0.4f),
                )
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
    // Довідка відкривається в тому самому вікні (того самого розміру): сторінки міняються плавно, «Назад» — на крок.
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    val current = page
    val back: (@Composable () -> Unit)? = when (current) {
        SettingsPage.Main -> null
        else -> { { SheetButton(stringResource(R.string.help_back), { page = current.up() }) } }
    }
    SheetDialog(
        // Дотик повз вікно — закрити; «Назад» на сторінці довідки — крок назад (BackHandler нижче).
        onDismissRequest = onDismiss,
        title = stringResource(
            when (current) {
                SettingsPage.Main -> R.string.settings
                SettingsPage.Help -> R.string.help
                is SettingsPage.Topic -> current.topic.title
            },
        ),
        buttons = back,
        tall = true,
    ) {
        androidx.activity.compose.BackHandler(enabled = current != SettingsPage.Main) { page = current.up() }
        androidx.compose.animation.AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                (fadeIn(tween(PAGE_IN_MS, delayMillis = 60)) + slideInHorizontally(tween(PAGE_IN_MS, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 8 }) togetherWith
                    (fadeOut(tween(PAGE_OUT_MS)) + slideOutHorizontally(tween(PAGE_OUT_MS, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 8 })
            },
            label = "settingsPage",
        ) { shown ->
            when (shown) {
                SettingsPage.Main -> Column(Modifier.fillMaxWidth().bouncyScroll()) {
                    // Довідка — першою: тут усі приховані жести, розкладені за темами.
                    MenuRow(Glyph.More, stringResource(R.string.help), { page = SettingsPage.Help })
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_style), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                    // Black Glass — типова, тому перша.
                    ChoiceRow(stringResource(R.string.style_glass), stringResource(R.string.style_glass_desc), selected = AppStyle.glass) {
                        AppStyle.glass = true
                        settings.glass = true
                    }
                    ChoiceRow(stringResource(R.string.style_sheet), stringResource(R.string.style_sheet_desc), selected = !AppStyle.glass) {
                        AppStyle.glass = false
                        settings.glass = false
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
                SettingsPage.Help -> Column(Modifier.fillMaxWidth().bouncyScroll()) {
                    HelpTopic.entries.forEach { t -> MenuRow(t.glyph, stringResource(t.title), { page = SettingsPage.Topic(t) }) }
                }
                is SettingsPage.Topic -> Text(
                    stringResource(shown.topic.body),
                    color = Oled.TextPrimary.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.fillMaxWidth().bouncyScroll(),
                )
            }
        }
    }
}

/** Сторінки вікна Налаштувань. */
private sealed interface SettingsPage {
    data object Main : SettingsPage
    data object Help : SettingsPage
    data class Topic(val topic: HelpTopic) : SettingsPage
}

private val SettingsPage.depth: Int get() = when (this) {
    SettingsPage.Main -> 0
    SettingsPage.Help -> 1
    is SettingsPage.Topic -> 2
}

private fun SettingsPage.up(): SettingsPage = when (this) {
    is SettingsPage.Topic -> SettingsPage.Help
    else -> SettingsPage.Main
}

private const val PAGE_IN_MS = 300
private const val PAGE_OUT_MS = 180

/** Теми довідки «Як користуватися»: назва й докладний текст. */
private enum class HelpTopic(val title: Int, val body: Int, val glyph: Glyph) {
    Create(R.string.help_create_t, R.string.help_create, Glyph.Keyboard),
    Camera(R.string.help_camera_t, R.string.help_camera, Glyph.ScanQr),
    Saved(R.string.help_list_t, R.string.help_list, Glyph.Search),
    Folders(R.string.help_folders_t, R.string.help_folders, Glyph.Folder),
    Favorites(R.string.help_fav_t, R.string.help_fav, Glyph.Star),
    Print(R.string.help_print_t, R.string.help_print, Glyph.Print),
    Settings(R.string.help_settings_t, R.string.help_settings, Glyph.Settings),
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
        if (selected) GlyphIcon(Glyph.Save, null, tint = SelectedInk)
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
    // Усі форми — вікнами однакової сталої висоти (не ростуть і не стрибають, коли додаєш поля).
    SheetDialog(onDismissRequest = onDismiss, title = title, tall = true, buttons = {
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
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (kind) {
                FormKind.Wifi -> {
                    SheetTextField(a, { a = it }, stringResource(R.string.form_wifi_ssid), focusRequester = focus)
                    // Поле пароля — завжди на місці (без пароля просто не враховується): нічого не стрибає.
                    SheetTextField(b, { b = it }, stringResource(R.string.form_wifi_password), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                    // Кнопки — рівними парами на всю ширину.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("WPA/WPA2/WPA3", security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA, Modifier.weight(1f)) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA }
                        ChoiceChip(stringResource(R.string.form_wifi_wpa3_only), security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA3, Modifier.weight(1f)) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WPA3 }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("WEP", security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WEP, Modifier.weight(1f)) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.WEP }
                        ChoiceChip(stringResource(R.string.form_wifi_open), security == com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.NONE, Modifier.weight(1f)) { security = com.blacklaunch.qrcodes.qr.Payloads.WifiSecurity.NONE }
                    }
                    ChoiceChip(stringResource(R.string.form_wifi_hidden), hidden, Modifier.fillMaxWidth()) { hidden = !hidden }
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

/** Назва папки (нова чи перейменування). */
@Composable
fun FolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, suggested: String? = null, onApply: (String) -> Unit) {
    // Нова папка: поле порожнє, а типова назва («Папка 2») — лише підказкою; почав писати — вона зникає, нічого не
    // дописується. Не ввів нічого — береться саме вона.
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val result = name.trim().ifEmpty { suggested.orEmpty() }
    SheetDialog(onDismissRequest = onDismiss, title = title, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onApply(result) }, tone = Tone.Confirm, enabled = result.isNotBlank())
    }) {
        SheetTextField(name, { name = it }, suggested ?: stringResource(R.string.folder_name_hint), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), focusRequester = focus)
    }
}

/** Меню папки: відкрити, назва, друк усіх її кодів, розпустити. */
@Composable
fun FolderMenu(folder: com.blacklaunch.qrcodes.data.Folder, onDismiss: () -> Unit, onRename: () -> Unit, onPrintAll: (() -> Unit)?, onUngroup: () -> Unit, onDelete: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = folder.name) {
        Column {
            MenuRow(Glyph.Edit, stringResource(R.string.folder_rename), onRename)
            if (onPrintAll != null) MenuRow(Glyph.Print, stringResource(R.string.folder_print_all), onPrintAll)
            MenuRow(Glyph.Close, stringResource(R.string.folder_ungroup), onUngroup)
            MenuRow(Glyph.Delete, stringResource(R.string.folder_delete), onDelete, tone = Tone.Danger)
        }
    }
}

/** Попередження перед видаленням папки з усім вмістом — назавжди, тому червона кнопка й окреме питання. */
@Composable
fun FolderDeleteDialog(folder: com.blacklaunch.qrcodes.data.Folder, count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.folder_delete_title, folder.name), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.folder_delete_confirm), onConfirm, tone = Tone.Danger)
    }) {
        Text(
            if (count > 0) stringResource(R.string.folder_delete_text, count) else stringResource(R.string.folder_delete_empty),
            fontSize = 16.sp,
        )
    }
}

/** «У папку…»: наявні папки й «Нова папка». */
@Composable
fun FolderPicker(folders: List<com.blacklaunch.qrcodes.data.Folder>, current: Long?, onDismiss: () -> Unit, onPick: (com.blacklaunch.qrcodes.data.Folder) -> Unit, onNew: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.folder_move)) {
        Column(Modifier.bouncyScroll()) {
            folders.filter { it.id != current }.forEach { f -> MenuRow(Glyph.Folder, f.name, { onPick(f) }) }
            MenuRow(Glyph.Plus, stringResource(R.string.folder_new), onNew)
        }
    }
}

/**
 * Зміст коду: усе, що закодовано, — видно цілком і можна виправити (одна буква не та — не треба створювати заново).
 * Якщо такий зміст цим кодом не закодувати (задовгий QR, кирилиця в штрихкоді) — «Застосувати» неактивна й пояснення.
 */
@Composable
fun ContentDialog(code: QrCode, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(code.content) }
    val format = code.codeFormat
    val valid = remember(text, code.format) { com.blacklaunch.qrcodes.qr.Symbol.encode(text.trim(), code.codeKind) != null }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.content_edit), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onApply(text) }, tone = Tone.Confirm, enabled = valid && text.isNotBlank())
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (AppStyle.glass) {
                            Modifier
                                .clip(shape)
                                .background(if (focused) Color(0x0FFFFFFF) else Color(0x08FFFFFF))
                                .border(1.dp, if (focused) Color(0x99FFFFFF) else Color(0x38FFFFFF), shape)
                        } else {
                            Modifier.pencilFrame(14.dp, seed = 29, alpha = if (focused) 0.9f else 0.6f)
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = fieldStyle().copy(color = Oled.TextPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(if (AppStyle.glass) Oled.TextPrimary else Oled.TextSecondary),
                    minLines = 3,
                    maxLines = 12,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                if (valid || text.isBlank()) stringResource(R.string.content_edit_hint, text.trim().length) else stringResource(kindHint(code.codeKind)),
                color = if (valid || text.isBlank()) Oled.TextSecondary else Oled.Danger,
                fontSize = 14.sp,
            )
        }
    }
}

/** Вибір виду коду: квадратні (QR, Data Matrix, Aztec) і штрихкоди (авто чи конкретний), з тим, що кожен приймає. */
@Composable
fun KindPicker(current: com.blacklaunch.qrcodes.qr.CodeKind, onDismiss: () -> Unit, onPick: (com.blacklaunch.qrcodes.qr.CodeKind) -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(if (current.format == com.blacklaunch.qrcodes.qr.CodeFormat.BARCODE) R.string.kind_title_bar else R.string.kind_title_qr), tall = true) {
        Column(Modifier.bouncyScroll()) {
            // Лише види вибраної категорії: для QR — квадратні, для штрихкоду — лінійні (типово «Авто»).
            com.blacklaunch.qrcodes.qr.CodeKind.entries.filter { it.format == current.format }.forEach { k ->
                val title = if (k == com.blacklaunch.qrcodes.qr.CodeKind.AUTO) stringResource(R.string.kind_auto) else k.title
                ChoiceRow(title, stringResource(kindHint(k)), selected = k == current) { onPick(k) }
            }
        }
    }
}

internal fun kindHint(k: com.blacklaunch.qrcodes.qr.CodeKind): Int = when (k) {
    com.blacklaunch.qrcodes.qr.CodeKind.QR -> R.string.kind_hint_qr
    com.blacklaunch.qrcodes.qr.CodeKind.DATA_MATRIX -> R.string.kind_hint_datamatrix
    com.blacklaunch.qrcodes.qr.CodeKind.AZTEC -> R.string.kind_hint_aztec
    com.blacklaunch.qrcodes.qr.CodeKind.AUTO -> R.string.kind_hint_auto
    com.blacklaunch.qrcodes.qr.CodeKind.CODE128 -> R.string.kind_hint_code128
    com.blacklaunch.qrcodes.qr.CodeKind.CODE39 -> R.string.kind_hint_code39
    com.blacklaunch.qrcodes.qr.CodeKind.CODE93 -> R.string.kind_hint_code93
    com.blacklaunch.qrcodes.qr.CodeKind.EAN13 -> R.string.kind_hint_ean13
    com.blacklaunch.qrcodes.qr.CodeKind.EAN8 -> R.string.kind_hint_ean8
    com.blacklaunch.qrcodes.qr.CodeKind.UPCA -> R.string.kind_hint_upca
    com.blacklaunch.qrcodes.qr.CodeKind.UPCE -> R.string.kind_hint_upce
    com.blacklaunch.qrcodes.qr.CodeKind.ITF -> R.string.kind_hint_itf
    com.blacklaunch.qrcodes.qr.CodeKind.CODABAR -> R.string.kind_hint_codabar
}

/**
 * Багато кодів одразу. Серія: початок + номер (з нулями) + кінець — напр. «ПАЛЕТА-001…100». Список: кожен рядок — код.
 * Видно, що вийде, і скільки не підходить для вибраного виду коду; усі нові коди — в одну папку.
 */
@Composable
fun BatchDialog(list: Boolean, kind: com.blacklaunch.qrcodes.qr.CodeKind, onDismiss: () -> Unit, onCreate: (List<String>, String) -> Unit) {
    var prefix by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("1") }
    var count by remember { mutableStateOf("10") }
    var digits by remember { mutableStateOf("3") }
    var suffix by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    val contents = remember(list, prefix, first, count, digits, suffix, text) {
        if (list) {
            com.blacklaunch.qrcodes.qr.Batch.lines(text)
        } else {
            com.blacklaunch.qrcodes.qr.Batch.series(prefix, first.toLongOrNull() ?: 1, count.toIntOrNull() ?: 0, digits.toIntOrNull() ?: 0, suffix)
        }
    }
    val invalid = remember(contents, kind) { contents.count { com.blacklaunch.qrcodes.qr.Symbol.encode(it, kind) == null } }
    val folderDefault = (if (list) stringResource(R.string.batch_list_folder) else prefix.trim().trimEnd('-', '_', ' ', '/', '.').ifEmpty { stringResource(R.string.batch_series_folder) })
    val number = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = ImeAction.Next)
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(if (list) R.string.batch_list else R.string.batch_series), tall = true, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.batch_create), { onCreate(contents, folder.trim().ifEmpty { folderDefault }) }, tone = Tone.Confirm, enabled = contents.isNotEmpty() && invalid == 0)
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list) {
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (AppStyle.glass) {
                                Modifier.clip(shape).background(if (focused) Color(0x0FFFFFFF) else Color(0x08FFFFFF)).border(1.dp, if (focused) Color(0x99FFFFFF) else Color(0x38FFFFFF), shape)
                            } else {
                                Modifier.pencilFrame(14.dp, seed = 31, alpha = if (focused) 0.9f else 0.6f)
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (text.isEmpty()) Text(stringResource(R.string.batch_list_hint), color = Oled.TextSecondary, fontSize = 16.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = fieldStyle().copy(color = Oled.TextPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (AppStyle.glass) Oled.TextPrimary else Oled.TextSecondary),
                        minLines = 5,
                        maxLines = 12,
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                SheetTextField(prefix, { prefix = it }, stringResource(R.string.batch_prefix), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetTextField(first, { first = it.filter(Char::isDigit).take(12) }, stringResource(R.string.batch_first), Modifier.weight(1f), keyboardOptions = number)
                    SheetTextField(count, { count = it.filter(Char::isDigit).take(4) }, stringResource(R.string.batch_count), Modifier.weight(1f), keyboardOptions = number)
                    SheetTextField(digits, { digits = it.filter(Char::isDigit).take(2) }, stringResource(R.string.batch_digits), Modifier.weight(1f), keyboardOptions = number)
                }
                Text(stringResource(R.string.batch_numbers_hint), color = Oled.TextSecondary, fontSize = 13.sp)
                SheetTextField(suffix, { suffix = it }, stringResource(R.string.batch_suffix), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            }
            SheetTextField(folder, { folder = it }, folderDefault, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            if (contents.isNotEmpty()) {
                val sample = if (contents.size <= 3) contents.joinToString(", ") else contents.take(2).joinToString(", ") + " … " + contents.last()
                Text(stringResource(R.string.batch_preview, contents.size, sample), color = Oled.TextPrimary, fontSize = 15.sp)
            }
            if (invalid > 0) {
                Text(stringResource(R.string.batch_invalid, invalid) + " " + stringResource(kindHint(kind)), color = Oled.Danger, fontSize = 14.sp)
            }
        }
    }
}


/** Вибір одного зі збережених кодів (з пошуком за назвою чи змістом). */
@Composable
fun SavedCodePicker(saved: List<QrCode>, onDismiss: () -> Unit, onPick: (QrCode) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(saved, query) {
        val q = query.trim()
        if (q.isEmpty()) saved else saved.filter { it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true) }
    }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.fields_from_saved), tall = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetTextField(query, { query = it }, stringResource(R.string.search_hint))
            Column(Modifier.bouncyScroll()) {
                shown.forEach { code ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { onPick(code) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(code.title, color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        // Своя назва ховає зміст — покажемо, що саме підставиться.
                        if (code.name != null) Text(code.content, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
