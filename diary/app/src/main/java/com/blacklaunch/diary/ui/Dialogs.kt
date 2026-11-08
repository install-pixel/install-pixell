package com.blacklaunch.diary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.R
import com.blacklaunch.diary.settings.AppLanguage
import com.blacklaunch.diary.settings.AppSettings
import com.blacklaunch.diary.settings.InkDrying
import com.blacklaunch.diary.ui.theme.Oled

/** Пункт меню «⋯» (null у [MenuEntry.glyph] — сірий підзаголовок групи). */
class MenuEntry(val glyph: Glyph?, val text: String, val tone: Tone = Tone.Neutral, val onClick: () -> Unit = {})

/** Меню «⋯»: пункти, поділені сірими підзаголовками (THEMES.md, розділ 7). */
@Composable
fun MenuDialog(title: String?, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = title) {
        Column(Modifier.bouncyScroll()) {
            entries.forEach { e ->
                if (e.glyph == null) {
                    Text(e.text, color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
                } else {
                    MenuRow(e.glyph, e.text, { onDismiss(); e.onClick() }, e.tone)
                }
            }
        }
    }
}

/** Вікно з одним полем (назва щоденника, місце). [suggestions] — раніше вписані варіанти. */
@Composable
fun TextDialog(
    title: String,
    initial: String,
    placeholder: String,
    limit: Int,
    onDismiss: () -> Unit,
    suggestions: List<String> = emptyList(),
    hint: String? = null,
    allowEmpty: Boolean = false,
    onApply: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.save), { onApply(text.trim()); onDismiss() }, tone = Tone.Confirm, enabled = allowEmpty || text.isNotBlank())
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTextField(text, { text = it.take(limit) }, placeholder, focusRequester = focus)
            if (suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.take(6).forEach { s ->
                        Text(
                            s,
                            color = Oled.TextSecondary,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { text = s }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (hint != null) FieldHint(hint)
        }
    }
}

/** Підтвердження: червона кнопка — для безповоротного, синя — для головної дії. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, tone: Tone, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SheetDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(confirm, { onDismiss(); onConfirm() }, tone = tone)
        },
    ) {
        Text(text, color = Oled.TextPrimary.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.bouncyScroll())
    }
}

/** Пароль резервної копії (двічі, коли робимо копію; раз — коли відновлюємо). */
@Composable
fun PasswordDialog(restore: Boolean, wrong: Boolean, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = a.length >= 4 && (restore || a == b)
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (restore) R.string.restore_title else R.string.backup_title),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(if (restore) R.string.restore_go else R.string.backup_go), { onDismiss(); onApply(a) }, tone = Tone.Confirm, enabled = ok)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PasswordField(a, { a = it }, stringResource(R.string.backup_password), focus)
            if (!restore) PasswordField(b, { b = it }, stringResource(R.string.backup_password_again), null)
            if (wrong) FieldHint(stringResource(R.string.backup_wrong_password))
            WindowHint(stringResource(if (restore) R.string.restore_hint else R.string.backup_hint))
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, placeholder: String, focus: FocusRequester?) {
    SheetTextField(
        value,
        { onChange(it.take(128)) },
        placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        focusRequester = focus,
        visualTransformation = PasswordVisualTransformation(),
    )
}

/** Налаштування: стиль, мова, щоденник (закладка, чорнило), екран, довідка. */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    var openAtBookmark by remember { mutableStateOf(settings.openAtBookmark) }
    var drying by remember { mutableStateOf(settings.inkDrying) }
    var haptics by remember { mutableStateOf(settings.haptics) }
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    val current = page
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            when (current) {
                SettingsPage.Main -> R.string.settings
                SettingsPage.Help -> R.string.help
                is SettingsPage.Topic -> current.topic.title
            },
        ),
        buttons = {
            if (current == SettingsPage.Main) SheetButton(stringResource(R.string.close), onDismiss)
            else SheetButton(stringResource(R.string.back), { page = current.up() })
        },
        tall = true,
    ) {
        BackHandler(enabled = current != SettingsPage.Main) { page = current.up() }
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                ((fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 8 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 8 }))
                    .using(null)
            },
            label = "settingsPage",
        ) { shown ->
            when (shown) {
                SettingsPage.Main -> Column(Modifier.fillMaxSize().bouncyScroll()) {
                    MenuRow(Glyph.Help, stringResource(R.string.help), { page = SettingsPage.Help })
                    Spacer(Modifier.height(12.dp))
                    Header(stringResource(R.string.settings_style))
                    ChoiceRow(stringResource(R.string.style_glass), stringResource(R.string.style_glass_desc), AppStyle.glass) {
                        AppStyle.glass = true
                        settings.glass = true
                    }
                    ChoiceRow(stringResource(R.string.style_sheet), stringResource(R.string.style_sheet_desc), !AppStyle.glass) {
                        AppStyle.glass = false
                        settings.glass = false
                    }
                    Header(stringResource(R.string.settings_language))
                    AppLanguage.entries.forEach { lang ->
                        val title = when (lang) {
                            AppLanguage.System -> stringResource(R.string.lang_system)
                            AppLanguage.Ukrainian -> "Українська"
                            AppLanguage.Polish -> "Polski"
                            AppLanguage.English -> "English"
                        }
                        ChoiceRow(title, null, language == lang) {
                            if (language != lang) {
                                language = lang
                                settings.language = lang
                                onLanguageChanged()
                            }
                        }
                    }
                    Header(stringResource(R.string.settings_diary))
                    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(stringResource(R.string.open_bookmark_title), openAtBookmark, subtitle = stringResource(R.string.open_bookmark_desc)) {
                            openAtBookmark = it
                            settings.openAtBookmark = it
                        }
                    }
                    Text(stringResource(R.string.drying_title), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
                    InkDrying.entries.forEach { d ->
                        ChoiceRow(stringResource(if (d == InkDrying.Day) R.string.drying_day else R.string.drying_week), null, drying == d) {
                            drying = d
                            settings.inkDrying = d
                        }
                    }
                    FieldHint(stringResource(R.string.drying_desc), Modifier.padding(horizontal = 8.dp))
                    Header(stringResource(R.string.settings_screen))
                    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(stringResource(R.string.status_hide_title), StatusBar.hidden, subtitle = stringResource(R.string.status_hide_desc)) {
                            StatusBar.hidden = it
                            settings.hideStatusBar = it
                        }
                        SwitchRow(stringResource(R.string.haptics_title), haptics, subtitle = stringResource(R.string.haptics_desc)) {
                            haptics = it
                            settings.haptics = it
                            HapticsSwitch.on = it
                        }
                    }
                    WindowHint(stringResource(R.string.settings_hint))
                }
                SettingsPage.Help -> Column(Modifier.fillMaxSize().bouncyScroll()) {
                    HelpTopic.entries.forEach { t -> MenuRow(t.glyph, stringResource(t.title), { page = SettingsPage.Topic(t) }) }
                }
                is SettingsPage.Topic -> Text(
                    stringResource(shown.topic.body),
                    color = Oled.TextPrimary.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.fillMaxSize().bouncyScroll(),
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

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

private fun SettingsPage.up(): SettingsPage = if (this is SettingsPage.Topic) SettingsPage.Help else SettingsPage.Main

/** Довідка: усі приховані жести — за темами. */
private enum class HelpTopic(val title: Int, val body: Int, val glyph: Glyph) {
    Turning(R.string.help_turn_t, R.string.help_turn, Glyph.Book),
    Writing(R.string.help_write_t, R.string.help_write, Glyph.Edit),
    Ink(R.string.help_ink_t, R.string.help_ink, Glyph.Pen),
    Drying(R.string.help_dry_t, R.string.help_dry, Glyph.TextT),
    Media(R.string.help_media_t, R.string.help_media, Glyph.Photo),
    Bookmark(R.string.help_bookmark_t, R.string.help_bookmark, Glyph.Bookmark),
    Search(R.string.help_search_t, R.string.help_search, Glyph.Search),
    Lock(R.string.help_lock_t, R.string.help_lock, Glyph.Lock),
    Shelf(R.string.help_shelf_t, R.string.help_shelf, Glyph.Grid),
    Backup(R.string.help_backup_t, R.string.help_backup, Glyph.Backup),
}

/** Рядок вибору: назва, пояснення і галочка, якщо вибрано (вибране — галочкою, не кольором). */
@Composable
fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Oled.TextSecondary, fontSize = 14.sp)
        }
        GlyphIcon(Glyph.Save, null, tint = SelectedInk.copy(alpha = if (selected) 1f else 0f))
    }
}
