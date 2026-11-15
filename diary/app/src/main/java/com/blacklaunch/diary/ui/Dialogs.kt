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
import kotlinx.coroutines.launch
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

/**
 * Загальні налаштування — для всіх щоденників — як книжечка зі сторінок сталого розміру, між ними — те саме гортання, що й
 * у щоденнику. 0 — інструкція (першим рядком), щоденники загалом, вигляд (стиль, мова, екран); 1 — кожен щоденник (до його
 * налаштувань є й коротший шлях — довге натискання на корінець). Інструкція — окремі сторінки за рядком «Інструкція».
 */
@Composable
fun SettingsBook(
    onLanguageChanged: () -> Unit,
    onNewDiary: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    onBackup: () -> Unit = {},
    diaries: List<Pair<String, String>> = emptyList(),
    onDiarySettings: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    var haptics by remember { mutableStateOf(settings.haptics) }
    val state = remember { com.blacklaunch.diary.book.FlipState(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val topics = HelpTopic.entries
    val diariesPage = 1
    val helpPage = 2
    fun go(i: Int) { scope.launch { state.flipTo(i) } }
    BackHandler(enabled = state.index > 0) { go(if (state.index > helpPage) helpPage else 0) }
    com.blacklaunch.diary.book.FlipBook(
        state = state,
        count = helpPage + 1 + topics.size,
        enabled = true,
        modifier = modifier,
        canFlipForward = { it < diariesPage },
        prevOf = { if (it > helpPage) helpPage else if (it == helpPage) 0 else it - 1 },
    ) { i ->
        val title = when {
            i == 0 -> stringResource(R.string.settings)
            i == diariesPage -> stringResource(R.string.diaries)
            i == helpPage -> stringResource(R.string.help)
            else -> stringResource(topics[i - helpPage - 1].title)
        }
        com.blacklaunch.diary.book.PaperPage(
            title = title,
            subtitle = null,
            seed = 57 + i * 13,
            turnHint = i == 0 && state.index == 0 && diaries.isNotEmpty(),
        ) {
            when {
                i == 0 -> {
                    MenuRow(Glyph.Help, stringResource(R.string.help), { go(helpPage) })
                    MenuRow(Glyph.Plus, stringResource(R.string.new_diary), onNewDiary)
                    MenuRow(Glyph.Backup, stringResource(R.string.backup_all), onBackup)
                    MenuRow(Glyph.Restore, stringResource(R.string.restore), onRestore)
                    Header(stringResource(R.string.settings_style))
                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(stringResource(R.string.style_glass), AppStyle.glass, Modifier.weight(1f)) {
                            AppStyle.glass = true
                            settings.glass = true
                        }
                        ChoiceChip(stringResource(R.string.style_sheet), !AppStyle.glass, Modifier.weight(1f)) {
                            AppStyle.glass = false
                            settings.glass = false
                        }
                    }
                    Header(stringResource(R.string.settings_language))
                    AppLanguage.entries.chunked(2).forEach { pair ->
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { lang ->
                                val name = when (lang) {
                                    AppLanguage.System -> stringResource(R.string.lang_system)
                                    AppLanguage.Ukrainian -> "Українська"
                                    AppLanguage.Polish -> "Polski"
                                    AppLanguage.English -> "English"
                                }
                                ChoiceChip(name, language == lang, Modifier.weight(1f)) {
                                    if (language != lang) {
                                        language = lang
                                        settings.language = lang
                                        onLanguageChanged()
                                    }
                                }
                            }
                        }
                    }
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
                }
                i == diariesPage -> {
                    diaries.forEach { (id, name) -> MenuRow(Glyph.Book, name, { onDiarySettings(id) }) }
                }
                i == helpPage -> {
                    MenuRow(Glyph.Back, stringResource(R.string.settings), { go(0) })
                    topics.forEachIndexed { k, t -> MenuRow(t.glyph, stringResource(t.title), { go(helpPage + 1 + k) }) }
                }
                else -> {
                    MenuRow(Glyph.Back, stringResource(R.string.help), { go(helpPage) })
                    Text(
                        stringResource(topics[i - helpPage - 1].body),
                        color = Oled.TextPrimary.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Інструкція: що де і як — за темами, від першого знайомства до резервної копії. */
private enum class HelpTopic(val title: Int, val body: Int, val glyph: Glyph) {
    Start(R.string.help_start_t, R.string.help_start, Glyph.Help),
    Turning(R.string.help_turn_t, R.string.help_turn, Glyph.Book),
    Writing(R.string.help_write_t, R.string.help_write, Glyph.Edit),
    Drying(R.string.help_dry_t, R.string.help_dry, Glyph.TextT),
    Ink(R.string.help_ink_t, R.string.help_ink, Glyph.Pen),
    Media(R.string.help_media_t, R.string.help_media, Glyph.Photo),
    Search(R.string.help_search_t, R.string.help_search, Glyph.Search),
    Cover(R.string.help_cover_t, R.string.help_cover, Glyph.Gallery),
    Lock(R.string.help_lock_t, R.string.help_lock, Glyph.Lock),
    Shelf(R.string.help_shelf_t, R.string.help_shelf, Glyph.Shelf),
    Settings(R.string.help_settings_t, R.string.help_settings, Glyph.Settings),
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
