package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.global.AppSettingKind
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.ResultGroup
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.commands.CustomCommand
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun SearchResults(
    query: String,
    results: List<AppInfo>,
    groups: List<ResultGroup>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    actions: List<VoiceAction> = emptyList(),
    onAction: (VoiceAction) -> Unit = {},
    custom: List<CustomCommand> = emptyList(),
    onCustom: (CustomCommand) -> Unit = {},
    suggestions: List<AppInfo> = emptyList(),
    recent: List<String> = emptyList(),
    onRecent: (String) -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    allApps: List<AppInfo> = emptyList(),
    /** Швидкі дії біля контакту: true — подзвонити, false — написати. */
    onContactAction: (GlobalResult.Contact, Boolean) -> Unit = { _, _ -> },
    /** Які кнопки біля контакту дозволені профілем. */
    canCall: Boolean = true,
    canMessage: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (query.isBlank()) {
        EmptySearch(suggestions, recent, allApps, iconFor, onLaunch, onLongPress, onRecent, onRemoveRecent, modifier)
        return
    }
    if (results.isEmpty() && groups.isEmpty() && actions.isEmpty() && custom.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.BottomStart) {
            Text(stringResource(R.string.nothing_found), color = Oled.TextSecondary, modifier = Modifier.padding(16.dp))
        }
        return
    }
    // Ключі LazyColumn мають бути унікальними, інакше Compose падає: однакові SMS
    // від одного відправника чи повторений ярлик дали б однаковий ключ.
    val seen = HashSet<String>()
    val apps = results.filter { seen.add(it.key) }
    val unique = groups.mapNotNull { group ->
        group.items.filter { seen.add(it.key) }.takeIf { it.isNotEmpty() }?.let { group.copy(items = it) }
    }
    val calculator = unique.filter { it.kind == GroupKind.CALCULATOR }
    val others = unique.filter { it.kind != GroupKind.CALCULATOR }.sortedBy { it.kind.ordinal }
    // Найкращий результат — унизу, ближче до поля пошуку й великого пальця:
    // калькулятор, програми, далі групи; заголовок групи — над її рядками.
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom,
    ) {
        calculator.forEach { group ->
            items(group.items, key = { it.key }) { GlobalRow(it, onGlobal, onContactAction, canCall, canMessage) }
        }
        // Команди (як голосом) — одразу над полем пошуку.
        items(custom, key = { "custom:${it.id}" }) { command ->
            CommandRow(Icons.Filled.PlayArrow, command.phrase, command.steps.joinToString(" → ")) { onCustom(command) }
        }
        items(actions.distinct(), key = { "action:$it" }) { action ->
            CommandRow(action.icon(), action.title(), action.subtitle()) { onAction(action) }
        }
        items(apps, key = { it.key }) { app ->
            AppRow(app, iconFor = iconFor, onLaunch = onLaunch, onLongPress = onLongPress)
        }
        others.forEach { group ->
            items(group.items, key = { it.key }) { GlobalRow(it, onGlobal, onContactAction, canCall, canMessage) }
            item(key = "header:${group.kind}") { GroupHeader(group.kind) }
        }
    }
}

/** Порожній пошук: останні запити над полем, вище — підказки програм. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptySearch(
    suggestions: List<AppInfo>,
    recent: List<String>,
    allApps: List<AppInfo>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHaptics.current
    var showAll by remember { mutableStateOf(false) }
    if (showAll) {
        AllAppsList(allApps, iconFor, onLaunch, onLongPress, onHide = { showAll = false }, modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier, reverseLayout = true, verticalArrangement = Arrangement.Bottom) {
        // Видно два останні запити; решту (до 5) — гортанням у цьому ж блоці.
        if (recent.isNotEmpty()) item(key = "recent") {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(RecentRowHeight * minOf(recent.size, RECENT_VISIBLE)),
                reverseLayout = true,
            ) {
                items(recent, key = { "recent:$it" }) { text -> RecentRow(text, onRecent, onRemoveRecent) }
            }
        }
        if (suggestions.isNotEmpty()) {
            items(suggestions.chunked(SUGGESTION_COLUMNS).reversed(), key = { row -> "suggest:" + row.first().key }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { app ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { haptics.tap(); onLaunch(app) },
                                    onLongClick = { haptics.longPress(); onLongPress(app) },
                                )
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val icon by produceState<ImageBitmap?>(null, app.key, iconFor) { value = iconFor(app) }
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(app.label, color = Oled.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(SUGGESTION_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "suggest:header") {
                Text(
                    stringResource(R.string.suggestions_header),
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
                )
            }
        }
        if (allApps.isNotEmpty()) item(key = "all") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { haptics.tap(); showAll = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Menu, contentDescription = null, tint = Oled.TextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.all_apps, allApps.size), color = Oled.TextSecondary, fontSize = 16.sp)
            }
        }
    }
}

/** Усі програми за абеткою з літерами-розділювачами (P3). */
@Composable
private fun AllAppsList(
    apps: List<AppInfo>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHaptics.current
    val sections = remember(apps) { apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' } }
    LazyColumn(modifier = modifier) {
        item(key = "all:back") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { haptics.tap(); onHide() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Oled.TextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.all_apps, apps.size), color = Oled.TextPrimary, fontSize = 16.sp)
            }
        }
        sections.forEach { (letter, group) ->
            item(key = "letter:$letter") {
                Text(
                    letter.toString(),
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            items(group, key = { "all:" + it.key }) { app ->
                AppRow(app, iconFor = iconFor, onLaunch = onLaunch, onLongPress = onLongPress)
            }
        }
    }
}

private const val SUGGESTION_COLUMNS = 4

private val RecentRowHeight = 56.dp
private const val RECENT_VISIBLE = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(text: String, onRecent: (String) -> Unit, onRemoveRecent: (String) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RecentRowHeight)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { haptics.tap(); onRecent(text) },
                onLongClick = { haptics.longPress(); onRemoveRecent(text) },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Oled.TextSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Рядок програми: значок + назва. Спільний для результатів пошуку й закріплених програм. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: AppInfo,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val icon by produceState<ImageBitmap?>(null, app.key, iconFor) { value = iconFor(app) }
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .combinedClickable(
                onClick = { haptics.tap(); onLaunch(app) },
                onLongClick = { haptics.longPress(); onLongPress(app) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val current = icon
        if (current != null) {
            Image(bitmap = current, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Oled.TextPrimary, fontSize = 18.sp)
    }
}

@Composable
private fun GroupHeader(kind: GroupKind) {
    Text(
        text = stringResource(kind.title()),
        color = Oled.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun GlobalRow(
    result: GlobalResult,
    onClick: (GlobalResult) -> Unit,
    onContactAction: (GlobalResult.Contact, Boolean) -> Unit,
    canCall: Boolean,
    canMessage: Boolean,
) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .clickable { haptics.tap(); onClick(result) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (result is GlobalResult.Calculation) {
                // Без додаткових відступів шрифту — щоб «=» стояв рівно по центру рядка.
                Text(
                    "=",
                    color = Oled.TextPrimary,
                    style = TextStyle(
                        fontSize = 26.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                )
            } else {
                Icon(result.icon(), contentDescription = null, tint = Oled.TextSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val title = if (result is GlobalResult.AppSetting) stringResource(result.kind.title(), result.appLabel) else result.title
            Text(title, color = Oled.TextPrimary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            result.subtitle?.let {
                Text(it, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Недавній дзвінок — «Передзвонити» одним дотиком (номер відкривається в «Телефоні»).
        if (result is GlobalResult.Call && canCall) {
            IconButton(onClick = { haptics.tap(); onClick(result) }) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.call_back), tint = Oled.TextPrimary)
            }
        }
        // S2: біля контакту — «Подзвонити» й «Написати» одним дотиком.
        if (result is GlobalResult.Contact) {
            if (canCall) {
                IconButton(onClick = { haptics.tap(); onContactAction(result, true) }) {
                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.contact_call), tint = Oled.TextPrimary)
                }
            }
            if (canMessage) {
                IconButton(onClick = { haptics.tap(); onContactAction(result, false) }) {
                    Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.contact_message), tint = Oled.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun CommandRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Oled.TextPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Oled.TextPrimary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun GlobalResult.icon(): ImageVector = when (this) {
    is GlobalResult.Calculation -> Icons.Filled.Check
    is GlobalResult.Profile -> Icons.Filled.AccountCircle
    is GlobalResult.Setting -> Icons.Filled.Settings
    is GlobalResult.Shortcut -> Icons.Filled.Star
    is GlobalResult.Contact -> Icons.Filled.Person
    is GlobalResult.Event -> Icons.Filled.DateRange
    is GlobalResult.Sms -> Icons.Filled.Email
    is GlobalResult.AppSetting -> Icons.Filled.Settings
    is GlobalResult.Call -> Icons.Filled.Call
    is GlobalResult.NotificationText -> Icons.Filled.Notifications
    is GlobalResult.Media -> when {
        mime.startsWith("image/") -> Icons.Filled.AccountBox
        else -> Icons.Filled.PlayArrow
    }
}

@StringRes
private fun AppSettingKind.title(): Int = when (this) {
    AppSettingKind.NOTIFICATIONS -> R.string.app_setting_notifications
    AppSettingKind.BATTERY -> R.string.app_setting_battery
    AppSettingKind.INFO -> R.string.app_setting_info
    AppSettingKind.DATA -> R.string.app_setting_data
    AppSettingKind.LINKS -> R.string.app_setting_links
}

@StringRes
private fun GroupKind.title(): Int = when (this) {
    GroupKind.CALCULATOR -> R.string.group_calculator
    GroupKind.PROFILES -> R.string.group_profiles
    GroupKind.SETTINGS -> R.string.group_settings
    GroupKind.SHORTCUTS -> R.string.group_shortcuts
    GroupKind.CONTACTS -> R.string.group_contacts
    GroupKind.CALENDAR -> R.string.group_calendar
    GroupKind.SMS -> R.string.group_sms
    GroupKind.CALLS -> R.string.group_calls
    GroupKind.NOTIFICATIONS -> R.string.group_notifications
    GroupKind.MUSIC -> R.string.group_music
    GroupKind.VIDEO -> R.string.group_video
    GroupKind.PHOTOS -> R.string.group_photos
}
