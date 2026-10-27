package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Done
import com.blacklaunch.launcher.system.GlanceParts
import com.blacklaunch.launcher.profiles.ProfileDisplay
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import com.blacklaunch.launcher.profiles.SearchScope
import com.blacklaunch.launcher.profiles.GlobalMode
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.profiles.FocusRules
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.profiles.NotificationMode
import com.blacklaunch.launcher.profiles.PinnedEdits
import com.blacklaunch.launcher.profiles.SoundMode
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.ui.theme.Oled

/** Каркас екрана налаштувань: стрілка «Назад», заголовок, прокручуваний вміст. */
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Oled.TextPrimary)
            }
            Text(title, color = Oled.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                content()
                Spacer(Modifier.height(16.dp))
            },
        )
    }
}

@Composable
fun ProfilesScreen(
    profiles: List<LauncherProfile>,
    activeId: String?,
    onBack: () -> Unit,
    onSetActive: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCreate: (String) -> Unit,
    /** Новий режим за шаблоном «Зосередження» (етап 16d). */
    onCreateFocus: () -> Unit = {},
    /** Фокус-таймер: вибір режиму й тривалості. */
    onStartFocus: () -> Unit = {},
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.profiles_title), onBack) {
        Card {
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeId,
                    onSetActive = { onSetActive(profile.id) },
                    onEdit = { onEdit(profile.id) },
                )
            }
            ActionOption(R.string.profile_new, Icons.Filled.Add) { creating = true }
            ActionOption(R.string.focus_template_new, Icons.Filled.Star, onCreateFocus)
        }
        Note(stringResource(R.string.profiles_note))
        Card {
            Text(stringResource(R.string.focus_timer_title), color = Oled.TextPrimary, fontSize = 17.sp)
            Note(stringResource(R.string.focus_timer_note))
            ActionOption(R.string.focus_start_action, Icons.Filled.Lock, onStartFocus)
        }
    }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.profile_new),
            initial = "",
            confirm = stringResource(R.string.profile_create),
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ProfileRow(profile: LauncherProfile, active: Boolean, onSetActive: () -> Unit, onEdit: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Oled.Knob else Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
            .clickable { haptics.tap(); onSetActive() }
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(active)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, color = Oled.TextPrimary, fontSize = 17.sp)
            val apps = pluralStringResource(R.plurals.pinned_count, profile.pinnedAppKeys.size, profile.pinnedAppKeys.size)
            Text("$apps · ${stringResource(profile.soundMode.label())}", color = Oled.TextSecondary, fontSize = 13.sp)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, stringResource(R.string.profile_edit), tint = Oled.TextSecondary)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Oled.TextPrimary else Oled.TextSecondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Oled.TextPrimary))
    }
}

@Composable
fun ProfileEditScreen(
    profile: LauncherProfile,
    appsByKey: Map<String, AppInfo>,
    canDelete: Boolean,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onBack: () -> Unit,
    onChange: (LauncherProfile) -> Unit,
    onAddApp: () -> Unit,
    onDelete: () -> Unit,
    /** Доступ «Не турбувати» (потрібен для «Без звуку» й «Не турбувати»). */
    hasDndAccess: Boolean = true,
    onGrantDnd: () -> Unit = {},
    searchScope: SearchScope = SearchScope.ALL,
    onSearchScopeChange: (SearchScope) -> Unit = {},
    /** Глобальний пошук у цьому профілі: контакти, SMS, календар, налаштування, ярлики. */
    globalMode: GlobalMode = GlobalMode.ON,
    onGlobalModeChange: (GlobalMode) -> Unit = {},
    /** Сповіщення лише від програм профілю (через «Доступ до сповіщень»). */
    notificationFilter: Boolean = false,
    onNotificationFilterChange: (Boolean) -> Unit = {},
    hasNotificationAccess: Boolean = false,
    onGrantNotificationAccess: () -> Unit = {},
    /** «Також дозволити сповіщення від…»: програми поза профілем (назви вже готові). */
    /** Екран і пошук цього профілю. */
    display: ProfileDisplay = ProfileDisplay(),
    /** Загальний вибір «Короткого огляду» — з нього починається свій вибір профілю. */
    globalGlance: GlanceParts = GlanceParts(),
    onDisplayChange: (ProfileDisplay) -> Unit = {},
    onApplyDisplayToAll: () -> Unit = {},
    notificationExtras: List<AppInfo> = emptyList(),
    onAddNotificationExtras: () -> Unit = {},
    onRemoveNotificationExtra: (AppInfo) -> Unit = {},
    /** «Важкий вихід»: секунди відліку перед виходом з режиму (0 — одразу). */
    exitDelay: Int = 0,
    onExitDelayChange: (Int) -> Unit = {},
    onStartFocus: () -> Unit = {},
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    SettingsPage(profile.name, onBack) {
        Card {
            ActionOption(R.string.profile_rename, Icons.Filled.Edit) { renaming = true }
        }

        Card {
            Text(stringResource(R.string.pinned_title), color = Oled.TextPrimary, fontSize = 17.sp)
            if (profile.pinnedAppKeys.isEmpty()) Note(stringResource(R.string.pinned_empty))
            // Багато програм не розтягує сторінку: віконце фіксованої висоти з власною прокруткою.
            Column(Modifier.heightIn(max = PinnedListMaxHeight).verticalScroll(rememberScrollState())) {
                profile.pinnedAppKeys.forEachIndexed { index, key ->
                    val app = appsByKey[key]
                    PinnedRow(
                        label = app?.label ?: stringResource(R.string.pinned_missing),
                        app = app,
                        iconFor = iconFor,
                        canMoveUp = index > 0,
                        canMoveDown = index < profile.pinnedAppKeys.lastIndex,
                        onMove = { delta -> onChange(profile.copy(pinnedAppKeys = PinnedEdits.move(profile.pinnedAppKeys, key, delta))) },
                        onRemove = { onChange(profile.copy(pinnedAppKeys = PinnedEdits.remove(profile.pinnedAppKeys, key))) },
                    )
                }
            }
            ActionOption(R.string.pinned_add, Icons.Filled.Add, onAddApp)
        }

        Card {
            Text(stringResource(R.string.display_title), color = Oled.TextPrimary, fontSize = 17.sp)
            SwitchRow(stringResource(R.string.glance_title), stringResource(R.string.glance_subtitle), display.glance) {
                onDisplayChange(display.copy(glance = it))
            }
            if (display.glance) {
                // Що показує огляд у цьому профілі: як у загальних налаштуваннях або своє.
                val own = display.ownGlance
                SwitchRow(stringResource(R.string.glance_own_title), stringResource(R.string.glance_own_subtitle), own != null) { on ->
                    onDisplayChange(display.copy(ownGlance = if (on) globalGlance else null))
                }
                if (own != null) {
                    SwitchRow(stringResource(R.string.glance_part_clock), "", own.clock) { onDisplayChange(display.copy(ownGlance = own.copy(clock = it))) }
                    SwitchRow(stringResource(R.string.glance_part_alarm), "", own.alarm) { onDisplayChange(display.copy(ownGlance = own.copy(alarm = it))) }
                    SwitchRow(stringResource(R.string.glance_part_event), "", own.event) { onDisplayChange(display.copy(ownGlance = own.copy(event = it))) }
                    SwitchRow(stringResource(R.string.glance_part_battery), "", own.battery) { onDisplayChange(display.copy(ownGlance = own.copy(battery = it))) }
                }
            }
            SwitchRow(stringResource(R.string.profile_label_title), stringResource(R.string.profile_label_subtitle), display.profileLabel) {
                onDisplayChange(display.copy(profileLabel = it))
            }
            SwitchRow(stringResource(R.string.hide_status_title), stringResource(R.string.hide_status_subtitle), display.hideStatusBar) {
                onDisplayChange(display.copy(hideStatusBar = it))
            }
            SwitchRow(stringResource(R.string.suggestions_title), stringResource(R.string.suggestions_subtitle), display.suggestions) {
                onDisplayChange(display.copy(suggestions = it))
            }
            SwitchRow(stringResource(R.string.recent_queries_title), stringResource(R.string.recent_queries_subtitle), display.recentQueries) {
                onDisplayChange(display.copy(recentQueries = it))
            }
            SwitchRow(stringResource(R.string.all_apps_button_title), stringResource(R.string.all_apps_button_subtitle), display.allAppsButton) {
                onDisplayChange(display.copy(allAppsButton = it))
            }
            SwitchRow(stringResource(R.string.monitor_profile_title), stringResource(R.string.monitor_profile_subtitle), display.monitor) {
                onDisplayChange(display.copy(monitor = it))
            }
            // З моніторингом значків на екрані й так немає — перемикач лише заплутав би.
            if (!display.monitor) {
                SwitchRow(stringResource(R.string.show_apps_title), stringResource(R.string.show_apps_subtitle), display.showApps) {
                    onDisplayChange(display.copy(showApps = it))
                }
            }
            SwitchRow(stringResource(R.string.quick_switch_title), stringResource(R.string.quick_switch_subtitle), display.quickSwitch) {
                onDisplayChange(display.copy(quickSwitch = it))
            }
            ActionOption(R.string.display_apply_all, Icons.Filled.Done, onApplyDisplayToAll)
        }

        Card {
            Text(stringResource(R.string.focus_exit_delay_title), color = Oled.TextPrimary, fontSize = 17.sp)
            FocusRules.EXIT_DELAYS.forEach { seconds ->
                val label = if (seconds == 0) stringResource(R.string.focus_exit_now) else stringResource(R.string.focus_exit_after, seconds)
                TargetOption(label, exitDelay == seconds) { onExitDelayChange(seconds) }
            }
            Note(stringResource(R.string.focus_exit_delay_note))
            ActionOption(R.string.focus_start_here, Icons.Filled.Lock, onStartFocus)
        }

        Card {
            Text(stringResource(R.string.profile_sound), color = Oled.TextPrimary, fontSize = 17.sp)
            SoundMode.entries.forEach { mode ->
                TargetOption(mode.label(), profile.soundMode == mode) { onChange(profile.copy(soundMode = mode)) }
            }
        }

        Card {
            Text(stringResource(R.string.profile_notifications), color = Oled.TextPrimary, fontSize = 17.sp)
            listOf(NotificationMode.SYSTEM_DEFAULT, NotificationMode.DO_NOT_DISTURB).forEach { mode ->
                TargetOption(mode.label(), profile.notificationMode == mode) { onChange(profile.copy(notificationMode = mode)) }
            }
            Note(stringResource(R.string.profile_apply_note))
            SwitchRow(
                title = stringResource(R.string.notif_filter_title),
                subtitle = stringResource(R.string.notif_filter_subtitle),
                checked = notificationFilter,
                onCheckedChange = onNotificationFilterChange,
            )
            if (notificationFilter && !hasNotificationAccess) {
                Note(stringResource(R.string.notif_filter_access))
                ActionOption(R.string.notif_filter_grant, Icons.Filled.Lock, onGrantNotificationAccess)
            } else if (notificationFilter) {
                // Доступ уже є — кнопка зникає, лишається лише підтвердження.
                Note(stringResource(R.string.notif_filter_granted))
            }
            if (notificationFilter) {
                Text(stringResource(R.string.notif_extra_title), color = Oled.TextPrimary, fontSize = 16.sp)
                notificationExtras.forEach { extra ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(extra.label, color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveNotificationExtra(extra) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear), tint = Oled.TextSecondary)
                        }
                    }
                }
                ActionOption(R.string.notif_extra_add, Icons.Filled.Add, onAddNotificationExtras)
            }
            val needsAccess = profile.soundMode == SoundMode.SILENT || profile.notificationMode != NotificationMode.SYSTEM_DEFAULT
            if (needsAccess && !hasDndAccess) {
                Note(stringResource(R.string.dnd_access_needed))
                ActionOption(R.string.dnd_access_grant, Icons.Filled.Lock, onGrantDnd)
            }
        }

        Card {
            Text(stringResource(R.string.search_scope_title), color = Oled.TextPrimary, fontSize = 17.sp)
            SearchScope.entries.forEach { scope ->
                TargetOption(scope.label(), searchScope == scope) { onSearchScopeChange(scope) }
            }
            Note(stringResource(R.string.search_scope_note))
            Text(stringResource(R.string.profile_global_title), color = Oled.TextPrimary, fontSize = 16.sp)
            GlobalMode.entries.forEach { mode ->
                TargetOption(mode.label(), globalMode == mode) { onGlobalModeChange(mode) }
            }
            Note(stringResource(if (globalMode == GlobalMode.BY_APPS) R.string.global_by_apps_note else R.string.profile_global_subtitle))
        }

        Card {
            if (canDelete) {
                ActionOption(R.string.profile_delete, Icons.Filled.Delete, Tone.Danger) { confirmDelete = true }
            } else {
                Note(stringResource(R.string.profile_last))
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.profile_rename),
            initial = profile.name,
            confirm = stringResource(R.string.alias_done),
            onConfirm = { name ->
                renaming = false
                onChange(profile.copy(name = name))
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        GlassDialog(
            onDismissRequest = { confirmDelete = false },
            title = { DialogTitle(stringResource(R.string.profile_delete_confirm, profile.name)) },
            confirmButton = {
                DialogButton(stringResource(R.string.model_delete), {
                    confirmDelete = false
                    onDelete()
                }, Tone.Danger)
            },
            dismissButton = { DialogButton(stringResource(R.string.cancel), { confirmDelete = false }) },
        )
    }
}

@Composable
private fun PinnedRow(
    label: String,
    app: AppInfo?,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (app != null) {
                AppRow(app, iconFor = iconFor, onLaunch = {}, onLongPress = {})
            } else {
                Text(label, color = Oled.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(12.dp))
            }
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up), tint = if (canMoveUp) Oled.TextSecondary else Color(0xFF333333))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down), tint = if (canMoveDown) Oled.TextSecondary else Color(0xFF333333))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.pinned_remove), tint = Oled.TextSecondary)
        }
    }
}

/** Вибір програми для закріплення: усі встановлені, з пошуком. */
@Composable
fun AppPickerScreen(
    apps: List<AppInfo>,
    alreadyPinned: Set<String>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onBack: () -> Unit,
    /** Вибрані програми — кілька одразу, кнопкою «Додати». */
    onPick: (List<AppInfo>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<List<String>>(emptyList()) }
    val haptics = LocalHaptics.current
    val engine = remember { AppSearchEngine() }
    val index = remember(apps) { engine.index(apps) }
    val shown = (if (query.isBlank()) apps else index.search(query)).filter { it.key !in alreadyPinned }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Oled.TextPrimary)
            }
            Text(stringResource(R.string.pick_app_title), color = Oled.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint), color = Oled.TextSecondary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Oled.TextPrimary,
                unfocusedTextColor = Oled.TextPrimary,
                cursorColor = Oled.Accent,
                focusedBorderColor = Color(0xFF5A5A5A),
                unfocusedBorderColor = Color(0xFF3A3A3A),
            ),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(shown, key = { it.key }) { app ->
                val selected = app.key in picked
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AppRow(app, iconFor = iconFor, onLaunch = { picked = if (selected) picked - it.key else picked + it.key }, onLongPress = {})
                    }
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { on ->
                            haptics.tap()
                            picked = if (on) picked + app.key else picked - app.key
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFE6E6E6),
                            uncheckedColor = Color(0xFF5A5A5A),
                            checkmarkColor = Color.Black,
                        ),
                    )
                }
            }
        }
        if (picked.isNotEmpty()) {
            val byKey = apps.associateBy { it.key }
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                SmallButton(stringResource(R.string.pick_apps_add, picked.size), Tone.Confirm) { onPick(picked.mapNotNull { byKey[it] }) }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.profile_name), color = Oled.TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Oled.TextPrimary,
                    unfocusedTextColor = Oled.TextPrimary,
                    cursorColor = Oled.Accent,
                    focusedBorderColor = Color(0xFF5A5A5A),
                    unfocusedBorderColor = Color(0xFF3A3A3A),
                ),
            )
        },
        confirmButton = { DialogButton(confirm, { onConfirm(text.trim()) }, Tone.Confirm, enabled = text.isNotBlank()) },
        dismissButton = { DialogButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@StringRes
internal fun SoundMode.label(): Int = when (this) {
    SoundMode.NORMAL -> R.string.sound_normal
    SoundMode.VIBRATE -> R.string.sound_vibrate
    SoundMode.SILENT -> R.string.sound_silent
}

/** Скільки займає список програм профілю (~5 рядків), далі — прокрутка всередині. */
private val PinnedListMaxHeight = 300.dp

@StringRes
private fun GlobalMode.label(): Int = when (this) {
    GlobalMode.OFF -> R.string.global_off
    GlobalMode.BY_APPS -> R.string.global_by_apps
    GlobalMode.ON -> R.string.global_on
}

@StringRes
private fun SearchScope.label(): Int = when (this) {
    SearchScope.ALL -> R.string.search_scope_all
    SearchScope.PROFILE_FIRST -> R.string.search_scope_first
    SearchScope.PROFILE_ONLY -> R.string.search_scope_only
}

@StringRes
private fun NotificationMode.label(): Int = when (this) {
    NotificationMode.SYSTEM_DEFAULT -> R.string.notif_system
    NotificationMode.DO_NOT_DISTURB -> R.string.notif_dnd
    NotificationMode.ALLOW_SELECTED -> R.string.notif_dnd
}

/** Список усіх програм з поясненням угорі; натискання — дія з програмою (наприклад, її батарея). */
@Composable
fun AppListScreen(
    title: String,
    note: String,
    apps: List<AppInfo>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onBack: () -> Unit,
    onPick: (AppInfo) -> Unit,
) {
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Oled.TextPrimary)
            }
            Text(title, color = Oled.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item(key = "note") { Box(Modifier.padding(vertical = 8.dp)) { Note(note) } }
            items(sorted, key = { it.key }) { app ->
                AppRow(app, iconFor = iconFor, onLaunch = onPick, onLongPress = {})
            }
        }
    }
}
