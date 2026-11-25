package com.blacklaunch.supps.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacklaunch.supps.AppViewModel
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.JsonIO
import com.blacklaunch.supps.settings.AppSettings
import com.blacklaunch.supps.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Повноекранні сторінки поверх головного екрана (стек: «Назад» — на крок). */
sealed interface Screen {
    data object Settings : Screen
    data class Edit(val id: Long?) : Screen
}

private data class BannerMsg(val text: String, val action: String? = null, val onAction: () -> Unit = {}, val id: Long = System.nanoTime())

/** Найбільший файл схеми, який читаємо (захист від випадково вибраного величезного файлу). */
private const val MAX_FILE_BYTES = 5 * 1024 * 1024

/**
 * Дві сторінки — «Сьогодні» (з нею спілкуєшся щодня) й «Історія»; поруч — одна кнопка налаштувань, де зібране все
 * інше: аптечка, банки, цикли, схема JSON, питання оцінки, вигляд.
 */
@Composable
fun AppRoot(vm: AppViewModel, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var settingsVersion by remember { mutableIntStateOf(0) }
    val questions = activeQuestions(settingsVersion)
    val settings = remember { AppSettings(context) }
    val currency = remember(settingsVersion) { settings.currency }
    val sleepOffset = remember(settingsVersion) { settings.sleepOffsetMinutes }
    val stack = remember { mutableStateListOf<Screen>() }
    val pager = rememberPagerState(initialPage = 0) { 2 }

    var importing by remember { mutableStateOf<JsonIO.Parsed?>(null) }
    var groupFor by remember { mutableStateOf<Long?>(null) }
    var dayFor by remember { mutableStateOf<Long?>(null) }
    var effectFor by remember { mutableStateOf<Long?>(null) }
    var banner by remember { mutableStateOf<BannerMsg?>(null) }

    fun show(text: String, action: String? = null, onAction: () -> Unit = {}) {
        banner = BannerMsg(text, action, onAction)
    }
    LaunchedEffect(banner?.id) {
        if (banner != null) {
            delay(if (banner?.action != null) 5000 else 2600)
            banner = null
        }
    }

    val msgSaved = stringResource(R.string.saved)
    val msgDeleted = stringResource(R.string.deleted)
    val msgUndo = stringResource(R.string.undo)
    val msgBad = stringResource(R.string.import_bad)
    val msgEmptyClip = stringResource(R.string.import_empty_clipboard)
    val msgExported = stringResource(R.string.backup_done)
    val msgExportFail = stringResource(R.string.backup_fail)
    val msgRestored = stringResource(R.string.restore_done)
    val msgAddedFmt = stringResource(R.string.import_done)
    val msgTemplate = stringResource(R.string.template_copied)

    fun openParsed(text: String?) {
        if (text.isNullOrBlank()) {
            show(msgEmptyClip)
            return
        }
        try {
            importing = JsonIO.parse(text)
        } catch (e: Exception) {
            show(msgBad)
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > MAX_FILE_BYTES) null else String(bytes, Charsets.UTF_8)
                }
            }.getOrNull()
            if (text == null) show(msgBad) else openParsed(text)
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            show(if (ok) msgExported else msgExportFail)
        }
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun paste() {
        val text = runCatching { clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        openParsed(text)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> TodayPage(
                        data = data,
                        today = today,
                        now = now,
                        questions = questions,
                        onToggle = { vm.toggleIntake(it, today, live = true) },
                        onRate = { part, i, v -> vm.setRating(today, part, i, v, live = true) },
                        onOpenSettings = { stack.add(Screen.Settings) },
                    )
                    else -> HistoryPage(
                        data = data,
                        today = today,
                        questions = questions,
                        currency = currency,
                        sleepOffset = sleepOffset,
                        onDay = { dayFor = it },
                        onSupplement = { effectFor = it.id },
                    )
                }
            }
            // Нижня панель: дві сторінки й налаштування.
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf(R.string.tab_today, R.string.tab_history)
                tabs.forEachIndexed { i, label ->
                    val selected = pager.currentPage == i
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                scope.launch { pager.animateScrollToPage(i) }
                            }
                            .drawBehind {
                                if (selected) pencilStroke(size.height - 6.dp.toPx(), size.width * 0.25f, size.width * 0.75f, seed = i + 5, alpha = 0.9f)
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FitLabel(stringResource(label), color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundButton(Glyph.Settings, stringResource(R.string.settings_title), { stack.add(Screen.Settings) }, size = 48.dp)
            }
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = {
                val forward = targetState != null && (initialState == null || targetState is Screen.Edit)
                ((fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 8 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 8 }))
                    .using(null)
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                null -> Box(Modifier.fillMaxSize())
                Screen.Settings -> Cover {
                    SettingsScreen(
                        data = data,
                        today = today,
                        currency = currency,
                        onBack = { pop() },
                        onAdd = { stack.add(Screen.Edit(null)) },
                        onEdit = { stack.add(Screen.Edit(it.id)) },
                        onGroup = { groupFor = it.id },
                        onPaste = { paste() },
                        onFile = { runCatching { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) } },
                        onExport = { runCatching { saveFile.launch("supps-" + dayOf(today) + ".json") } },
                        onTemplate = {
                            runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("supps", JsonIO.TEMPLATE)) }
                            show(msgTemplate)
                        },
                        onLanguageChanged = onLanguageChanged,
                        onChanged = {
                            settingsVersion++
                            vm.refreshToday()
                        },
                    )
                }
                is Screen.Edit -> Cover {
                    val original = screen.id?.let { id -> data.supplements.firstOrNull { it.id == id } }
                    EditorScreen(
                        original = original,
                        data = data,
                        today = today,
                        currency = currency,
                        onBack = { pop() },
                        onSave = { r ->
                            vm.saveSupplement(r.supplement, r.doneDays, r.newGroupName, r.newGroupBreak, r.purchase)
                            pop()
                            show(msgSaved)
                        },
                        onDelete = { s ->
                            vm.deleteSupplement(s) { deleted -> show(msgDeleted, msgUndo) { vm.restore(deleted); banner = null } }
                            pop()
                        },
                    )
                }
            }
        }

        Banner(banner?.text, banner?.action, onAction = { banner?.onAction?.invoke() })
    }

    importing?.let { parsed ->
        ImportDialog(
            parsed = parsed,
            existing = data.supplements,
            onDismiss = { importing = null },
            onImport = { selected -> vm.importSchema(parsed, selected) { n -> show(msgAddedFmt.format(n)) } },
            onRestore = { b -> vm.restoreBackup(b) { show(msgRestored) } },
        )
    }
    groupFor?.let { id ->
        val g = data.groups.firstOrNull { it.id == id }
        if (g != null) {
            GroupDialog(
                group = g,
                data = data,
                onDismiss = { groupFor = null },
                onSave = { vm.saveGroup(it); show(msgSaved) },
                onMove = { s, d -> vm.moveInGroup(s, d) },
                onDelete = { vm.deleteGroup(it) },
            )
        }
    }
    dayFor?.let { day ->
        DayDialog(
            day = day,
            today = today,
            data = data,
            questions = questions,
            sleepOffset = sleepOffset,
            onDismiss = { dayFor = null },
            onToggle = { vm.toggleIntake(it, day, live = day == today) },
            onRate = { part, i, v -> vm.setRating(day, part, i, v, live = day == today) },
        )
    }
    effectFor?.let { id ->
        val s = data.supplements.firstOrNull { it.id == id }
        if (s != null) EffectDialog(s, data, today, questions) { effectFor = null }
    }
}

/** Повноекранна сторінка на чорному тлі, що забирає дотики (під нею — головний екран). */
@Composable
private fun Cover(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Oled.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        content()
    }
}
