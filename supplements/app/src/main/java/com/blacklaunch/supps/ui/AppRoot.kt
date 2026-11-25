package com.blacklaunch.supps.ui

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
    data class Edit(val id: Long?) : Screen
    data class Detail(val id: Long) : Screen
    data class Check(val day: Long) : Screen
}

private data class BannerMsg(val text: String, val action: String? = null, val onAction: () -> Unit = {}, val id: Long = System.nanoTime())

/** Найбільший файл схеми, який читаємо (захист від випадково вибраного величезного файлу). */
private const val MAX_FILE_BYTES = 5 * 1024 * 1024

@Composable
fun AppRoot(vm: AppViewModel, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var settingsVersion by remember { mutableIntStateOf(0) }
    val questions = activeQuestions(settingsVersion)
    val currency = remember(settingsVersion) { AppSettings(context).currency }
    val stack = remember { mutableStateListOf<Screen>() }
    val pager = rememberPagerState(initialPage = 0) { 3 }

    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<JsonIO.Parsed?>(null) }
    var groupFor by remember { mutableStateOf<Long?>(null) }
    var dayFor by remember { mutableStateOf<Long?>(null) }
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

    fun paste() {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = runCatching { clip?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
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
                        questions = questions,
                        currency = currency,
                        onToggle = { vm.toggleIntake(it, today) },
                        onOpen = { stack.add(Screen.Detail(it.id)) },
                        onCheckIn = { stack.add(Screen.Check(today)) },
                        onAdd = { stack.add(Screen.Edit(null)) },
                        onPaste = { paste() },
                    )
                    1 -> CabinetPage(
                        data = data,
                        today = today,
                        currency = currency,
                        onOpen = { stack.add(Screen.Detail(it.id)) },
                        onGroup = { groupFor = it.id },
                        onAdd = { stack.add(Screen.Edit(null)) },
                        onPaste = { paste() },
                    )
                    else -> StatsPage(
                        data = data,
                        today = today,
                        questions = questions,
                        currency = currency,
                        onDay = { dayFor = it },
                        onOpen = { stack.add(Screen.Detail(it.id)) },
                    )
                }
            }
            // Нижня панель: сторінки й «⋯».
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf(R.string.tab_today, R.string.tab_cabinet, R.string.tab_stats)
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
                                if (selected) pencilStroke(size.height - 6.dp.toPx(), size.width * 0.22f, size.width * 0.78f, seed = i + 5, alpha = 0.9f)
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FitLabel(stringResource(label), color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundButton(Glyph.More, stringResource(R.string.more), { menuOpen = true }, size = 48.dp)
            }
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = {
                val forward = targetState != null
                ((fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 8 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 8 }))
                    .using(null)
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                null -> Box(Modifier.fillMaxSize())
                is Screen.Edit -> {
                    val original = screen.id?.let { id -> data.supplements.firstOrNull { it.id == id } }
                    Box(Modifier.fillMaxSize().background(Oled.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                        EditorScreen(original, data, today, currency, onBack = { pop() }) { r ->
                            vm.saveSupplement(r.supplement, r.doneDays, r.newGroupName, r.newGroupBreak)
                            pop()
                            show(msgSaved)
                        }
                    }
                }
                is Screen.Detail -> {
                    val s = data.supplements.firstOrNull { it.id == screen.id }
                    Box(Modifier.fillMaxSize().background(Oled.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                        if (s != null) {
                            DetailScreen(
                                s = s,
                                data = data,
                                today = today,
                                questions = questions,
                                currency = currency,
                                onBack = { pop() },
                                onEdit = { stack.add(Screen.Edit(s.id)) },
                                onDelete = {
                                    vm.deleteSupplement(s) { deleted ->
                                        show(msgDeleted, msgUndo) { vm.restore(deleted); banner = null }
                                    }
                                    stack.clear()
                                },
                                onPurchase = { units, price, add -> vm.addPurchase(s, units, price, add); show(msgSaved) },
                                onStock = { left -> vm.saveSupplement(s.copy(unitsLeft = left), null, null, 0) },
                            )
                        }
                    }
                }
                is Screen.Check -> Box(Modifier.fillMaxSize().background(Oled.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                    CheckInScreen(screen.day, data.checkIn(screen.day), questions, onBack = { pop() }) { c ->
                        vm.saveCheckIn(c)
                        pop()
                        show(msgSaved)
                    }
                }
            }
        }

        Banner(banner?.text, banner?.action, onAction = { banner?.onAction?.invoke() })
    }

    if (menuOpen) {
        MainMenu(
            onDismiss = { menuOpen = false },
            onCheckIn = { stack.add(Screen.Check(today)) },
            onAdd = { stack.add(Screen.Edit(null)) },
            onPaste = { paste() },
            onFile = { runCatching { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) } },
            onSettings = { settingsOpen = true },
        )
    }
    if (settingsOpen) {
        SettingsDialog(
            onDismiss = { settingsOpen = false },
            onLanguageChanged = onLanguageChanged,
            onChanged = {
                settingsVersion++
                vm.refreshToday()
            },
            onExport = { runCatching { saveFile.launch("supps-" + dayOf(today) + ".json") } },
            onImport = { runCatching { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) } },
        )
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
        if (g == null) {
            groupFor = null
        } else {
            GroupDialog(
                group = g,
                data = data,
                today = today,
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
            data = data,
            questions = questions,
            onDismiss = { dayFor = null },
            onToggle = { vm.toggleIntake(it, day) },
            onCheckIn = {
                dayFor = null
                stack.add(Screen.Check(day))
            },
        )
    }
}
