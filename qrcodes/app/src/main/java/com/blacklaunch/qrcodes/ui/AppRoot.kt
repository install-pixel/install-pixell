package com.blacklaunch.qrcodes.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacklaunch.qrcodes.QrViewModel
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.SaveResult
import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.label.LabelScreen
import com.blacklaunch.qrcodes.scan.CameraScreen
import com.blacklaunch.qrcodes.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class BannerMsg(val text: String, val action: String? = null, val onAction: () -> Unit = {}, val id: Long = System.nanoTime())

/**
 * Дві сторінки, гортаються свайпом: «Вибране» і «Створити». Поверх — камера, друк, код на весь екран, меню.
 */
@Composable
fun AppRoot(vm: QrViewModel, sharedText: String?, onSharedConsumed: () -> Unit, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val saved by vm.all.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    // Папки: меню папки, назва нової (з двох кодів чи з «У папку…»), перейменування, вибір папки.
    var folderMenuFor by remember { mutableStateOf<com.blacklaunch.qrcodes.data.Folder?>(null) }
    var combine by remember { mutableStateOf<Pair<QrCode, QrCode>?>(null) }
    var folderDelete by remember { mutableStateOf<com.blacklaunch.qrcodes.data.Folder?>(null) }
    var folderRename by remember { mutableStateOf<com.blacklaunch.qrcodes.data.Folder?>(null) }
    var moveFor by remember { mutableStateOf<QrCode?>(null) }
    var newFolderFor by remember { mutableStateOf<QrCode?>(null) }
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val focus = remember { FocusRequester() }

    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by rememberSaveable { mutableStateOf(InputMode.Manual) }
    // Що створюємо: QR чи штрихкод (зберігається з кодом).
    val appSettings = remember { com.blacklaunch.qrcodes.settings.AppSettings(context) }
    var format by rememberSaveable { mutableStateOf(appSettings.lastFormat) }
    // Запам'ятовуємо вибір — хто робить лише штрихкоди, не перемикає щоразу.
    LaunchedEffect(format) { appSettings.lastFormat = format }
    // Назва з форми (Wi‑Fi, контакт, сайт) — стане назвою коду при збереженні.
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    var modeMenu by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Пошук у збережених — у капсулі внизу (лупа в кінці порожньої капсули).
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var formOpen by remember { mutableStateOf<FormKind?>(null) }
    var camera by rememberSaveable { mutableStateOf<InputMode?>(null) }
    var pendingCamera by remember { mutableStateOf<InputMode?>(null) }
    var fullscreen by remember { mutableStateOf<QrCode?>(null) }
    val dropZone = remember { DropZone() }
    var menuFor by remember { mutableStateOf<QrCode?>(null) }
    var renameFor by remember { mutableStateOf<QrCode?>(null) }
    var contentFor by remember { mutableStateOf<QrCode?>(null) }
    // Серія (false) чи список (true); null — закрито.
    var batchOpen by remember { mutableStateOf<Boolean?>(null) }
    var labelFor by remember { mutableStateOf<List<QrCode>?>(null) }
    var banner by remember { mutableStateOf<BannerMsg?>(null) }
    var focusAfterCamera by remember { mutableIntStateOf(0) }

    val textSaved = stringResource(R.string.saved)
    val textAlready = stringResource(R.string.already_saved)
    val textDeleted = stringResource(R.string.deleted)
    val textUndo = stringResource(R.string.undo)
    val textCopied = stringResource(R.string.copied)
    val textUnfavorited = stringResource(R.string.unfavorited)
    val textMovedTo = stringResource(R.string.folder_moved)
    val textNewFolder = stringResource(R.string.folder_new)
    val textFolderDefault = stringResource(R.string.folder_default_name)
    // Типова назва нової папки: «Папка», далі «Папка 1», «Папка 2»… — перша вільна.
    val nextFolderName = remember(folders, textFolderDefault) {
        val taken = folders.map { it.name.trim() }.toSet()
        if (textFolderDefault !in taken) textFolderDefault else generateSequence(1) { it + 1 }.map { "$textFolderDefault $it" }.first { it !in taken }
    }
    val textNoCamera = stringResource(R.string.camera_denied)

    fun show(text: String, action: String? = null, onAction: () -> Unit = {}) {
        banner = BannerMsg(text, action, onAction)
    }
    LaunchedEffect(banner) {
        val b = banner ?: return@LaunchedEffect
        delay(if (b.action != null) 4500 else 2200)
        if (banner?.id == b.id) banner = null
    }

    fun delete(code: QrCode) {
        vm.delete(code)
        show(textDeleted, textUndo) {
            vm.restore(code)
            banner = null
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            camera = pendingCamera
        } else {
            // Без дозволу камера не відкрилась — повертаємо значок «вручну».
            mode = InputMode.Manual
            show(textNoCamera)
        }
        pendingCamera = null
    }

    // Експорт / імпорт кодів: файл вибирає користувач (системне вікно), доступ — лише до нього.
    val textExported = stringResource(R.string.backup_exported)
    val textImported = stringResource(R.string.backup_imported)
    val textBadFile = stringResource(R.string.backup_bad_file)
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = runCatching { context.contentResolver.openOutputStream(uri)?.use { vm.exportTo(it) } }.getOrNull()
            show(if (n != null) textExported.format(n) else textBadFile)
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = runCatching { context.contentResolver.openInputStream(uri)?.use { vm.importFrom(it) } }.getOrNull() ?: -1
            show(if (n >= 0) textImported.format(n) else textBadFile)
        }
    }

    fun openCamera(m: InputMode) {
        focusManager.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera = m
        } else {
            pendingCamera = m
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // Текст, яким поділились з іншої програми, — у поле, на сторінку створення.
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        input = TextFieldValue(text, TextRange(text.length))
        pager.scrollToPage(1)
        onSharedConsumed()
    }
    // Пішли на «Вибране» — клавіатуру ховаємо.
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage == 0) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    // Текст із фото — одразу в поле з клавіатурою, щоб підправити.
    LaunchedEffect(focusAfterCamera) {
        if (focusAfterCamera == 0) return@LaunchedEffect
        delay(150)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    BackHandler(enabled = searching && camera == null && labelFor == null) {
        searching = false
        searchValue = TextFieldValue("")
    }
    BackHandler(enabled = !searching && pager.currentPage == 1 && camera == null && labelFor == null) {
        scope.launch { pager.animateScrollToPage(0, animationSpec = PAGE_ANIM) }
    }

    // Під меню й вікнами — розмитий і притемнений фон, як у Black Glass. Камера, друк і код на весь екран закривають
    // усе чорним — під ними нічого не розмиваємо (зайва робота для відеочипа й батареї).
    val blur = backdropAmount(folderMenuFor != null || folderDelete != null || combine != null || folderRename != null || moveFor != null || newFolderFor != null || formOpen != null || settingsOpen || modeMenu || menuFor != null || renameFor != null || contentFor != null || batchOpen != null)
    // Останні значення лишаються, поки екран плавно зникає.
    var lastCamera by remember { mutableStateOf(InputMode.ScanQr) }
    camera?.let { lastCamera = it }
    var lastLabel by remember { mutableStateOf<List<QrCode>>(emptyList()) }
    labelFor?.let { lastLabel = it }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().backdrop(blur).statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Положення сторінки (0 — «Вибране», 1 — «Створити», між ними — поки їдуть): назви вгорі міняються разом із рухом.
            val position = { pager.currentPage + pager.currentPageOffsetFraction }
            PageTabs(position, selected = { pager.targetPage }, onSettings = { settingsOpen = true }) { scope.launch { pager.animateScrollToPage(it, animationSpec = PAGE_ANIM) } }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
                // Доїжджає м'яко, з плавним гальмуванням (і після свайпу, і після дотику на назву).
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = PAGE_FLING),
            ) { page ->
                // Сторінка, що йде, трохи тьмяніє й зменшується; нова — проявляється.
                val pageModifier = Modifier.graphicsLayer {
                    val away = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    alpha = 1f - 0.45f * away
                    val sc = 1f - 0.05f * away
                    scaleX = sc
                    scaleY = sc
                }
                Box(pageModifier) {
                when (page) {
                    0 -> FavoritesPage(
                        favorites,
                        onOpen = { fullscreen = it },
                        onMenu = { menuFor = it },
                        onReorder = { vm.reorderFavorites(it) },
                        zone = dropZone,
                        onUnfavorite = { code ->
                            vm.unfavorite(code)
                            show(textUnfavorited, textUndo) {
                                vm.refavorite(code)
                                banner = null
                            }
                        },
                    )
                    else -> CreatePage(
                        input = input,
                        onInputChange = {
                            // Поле очистили — і запропонована формою назва вже ні до чого.
                            if (it.text.isEmpty()) pendingName = null
                            input = it
                        },
                        mode = mode,
                        onModeClick = { modeMenu = true },
                        onSave = {
                            vm.save(input.text, format, pendingName) { show(if (it == SaveResult.Saved) textSaved else textAlready) }
                            // Збережено — назад на старт: поле порожнє, клавіатура схована, код уже в списку.
                            input = TextFieldValue("")
                            pendingName = null
                            mode = InputMode.Manual
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                        saved = saved,
                        folders = folders,
                        listActions = SavedListActions(
                            onOpen = { fullscreen = it },
                            onCodeMenu = { menuFor = it },
                            onFolderMenu = { folderMenuFor = it },
                            onDelete = ::delete,
                            onReorder = { vm.reorder(it) },
                            onCombine = { dragged, target -> combine = dragged to target },
                            onIntoFolder = { code, folder ->
                                vm.moveToFolder(code, folder.id)
                                show(textMovedTo.format(folder.name))
                            },
                        ),
                        focusRequester = focus,
                        format = format,
                        onFormatChange = { format = it },
                        searching = searching,
                        searchValue = searchValue,
                        onSearchChange = { searchValue = it },
                        onSearch = {
                            searching = true
                            focusAfterCamera++
                        },
                        onCloseSearch = {
                            searching = false
                            searchValue = TextFieldValue("")
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                    )
                }
                }
            }
        }

        AnimatedVisibility(
            visible = camera != null,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { it / 10 },
            exit = fadeOut(tween(CLOSE_MS)) + slideOutVertically(tween(CLOSE_MS)) { it / 6 },
        ) {
            val m = lastCamera
            CameraScreen(
                scanQr = m == InputMode.ScanQr,
                onResult = { text, scanned ->
                    input = TextFieldValue(text, TextRange(text.length))
                    pendingName = null
                    // Дубль — того самого виду: зісканували штрихкод — створюємо штрихкод.
                    if (scanned != null) format = scanned
                    camera = null
                    mode = InputMode.Manual
                    if (m == InputMode.PhotoText) focusAfterCamera++
                },
                onClose = {
                    camera = null
                    mode = InputMode.Manual
                },
                onMessage = { show(it) },
            )
        }

        AnimatedVisibility(
            visible = labelFor != null,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { it / 10 },
            exit = fadeOut(tween(CLOSE_MS)) + slideOutVertically(tween(CLOSE_MS)) { it / 6 },
        ) {
            // Новий набір кодів — новий макет наклейки.
            androidx.compose.runtime.key(lastLabel) {
                LabelScreen(start = lastLabel, saved = saved.orEmpty(), onClose = { labelFor = null }, onMessage = { show(it) })
            }
        }

        DropZonePill(dropZone, stringResource(R.string.favorite_remove))

        // Сповіщення — поверх усього (і камери), завжди вгорі.
        Banner(banner?.text, banner?.action, banner?.onAction ?: {})
    }

    formOpen?.let { kind ->
        PayloadForm(kind, onDismiss = { formOpen = null }) { content, name ->
            formOpen = null
            // Готовий текст — у поле (код одразу видно вгорі), назва — при збереженні. Такі коди бувають лише QR.
            input = TextFieldValue(content, TextRange(content.length))
            pendingName = name
            format = com.blacklaunch.qrcodes.qr.CodeKind.QR
            mode = InputMode.Manual
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            onDismiss = { settingsOpen = false },
            onLanguageChanged = onLanguageChanged,
            onExport = {
                settingsOpen = false
                exportFile.launch("qraft-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())}.json")
            },
            onImport = {
                settingsOpen = false
                importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
    }

    if (modeMenu) {
        ModeMenu(
            onDismiss = { modeMenu = false },
            onPick = { picked ->
                modeMenu = false
                mode = picked
                when (picked) {
                    InputMode.Manual -> focusAfterCamera++
                    InputMode.ScanQr, InputMode.PhotoText -> openCamera(picked)
                }
            },
            onBatch = { list ->
                modeMenu = false
                batchOpen = list
            },
            onForm = {
                modeMenu = false
                formOpen = it
            },
        )
    }

    fullscreen?.let { shown ->
        // Свіжа версія запису (після перейменування чи зірочки).
        val code = saved?.firstOrNull { it.id == shown.id } ?: shown
        FullscreenCode(
            code,
            covered = menuFor != null || renameFor != null,
            onDismiss = { fullscreen = null },
            onMenu = { menuFor = code },
        )
    }

    menuFor?.let { shown ->
        val code = saved?.firstOrNull { it.id == shown.id } ?: shown
        ItemMenu(
            code,
            onDismiss = { menuFor = null },
            onFavorite = {
                vm.toggleFavorite(code)
                menuFor = null
            },
            onRename = {
                menuFor = null
                renameFor = code
            },
            onContent = {
                menuFor = null
                contentFor = code
            },
            onShare = {
                menuFor = null
                fullscreen = null
                labelFor = listOf(code)
            },
            onCopy = {
                copy(context, code)
                menuFor = null
                show(textCopied)
            },
            onDelete = {
                menuFor = null
                fullscreen = null
                delete(code)
            },
            onToFolder = {
                menuFor = null
                moveFor = code
            },
            onFromFolder = if (code.folderId != null) {
                {
                    menuFor = null
                    vm.moveToFolder(code, null)
                }
            } else {
                null
            },
        )
    }

    // Код кинули на інший код — нова папка з обох (назву питаємо).
    combine?.let { (dragged, target) ->
        FolderNameDialog(textNewFolder, "", onDismiss = { combine = null }, suggested = nextFolderName) { name ->
            vm.createFolder(name, listOf(target.id, dragged.id), target.sortOrder)
            combine = null
            show(textMovedTo.format(name))
        }
    }
    folderMenuFor?.let { folder ->
        val inside = saved.orEmpty().filter { it.folderId == folder.id }
        FolderMenu(
            folder,
            onDismiss = { folderMenuFor = null },
            onRename = {
                folderMenuFor = null
                folderRename = folder
            },
            onPrintAll = if (inside.isNotEmpty()) {
                {
                    folderMenuFor = null
                    labelFor = inside
                }
            } else {
                null
            },
            onUngroup = {
                folderMenuFor = null
                vm.ungroup(folder.id)
            },
            onDelete = {
                folderMenuFor = null
                folderDelete = folder
            },
        )
    }
    folderDelete?.let { folder ->
        val count = saved.orEmpty().count { it.folderId == folder.id }
        val done = stringResource(R.string.folder_deleted)
        FolderDeleteDialog(folder, count, onDismiss = { folderDelete = null }) {
            folderDelete = null
            vm.deleteFolder(folder.id)
            show(done)
        }
    }
    folderRename?.let { folder ->
        FolderNameDialog(stringResource(R.string.folder_rename), folder.name, onDismiss = { folderRename = null }) {
            vm.renameFolder(folder.id, it)
            folderRename = null
        }
    }
    moveFor?.let { code ->
        FolderPicker(
            folders,
            code.folderId,
            onDismiss = { moveFor = null },
            onPick = { folder ->
                moveFor = null
                vm.moveToFolder(code, folder.id)
                show(textMovedTo.format(folder.name))
            },
            onNew = {
                moveFor = null
                newFolderFor = code
            },
        )
    }
    newFolderFor?.let { code ->
        FolderNameDialog(textNewFolder, "", onDismiss = { newFolderFor = null }, suggested = nextFolderName) { name ->
            vm.createFolder(name, listOf(code.id), code.sortOrder)
            newFolderFor = null
            show(textMovedTo.format(name))
        }
    }

    renameFor?.let { code ->
        RenameDialog(code, onDismiss = { renameFor = null }) {
            vm.rename(code, it)
            renameFor = null
        }
    }
    batchOpen?.let { list ->
        val doneText = stringResource(R.string.batch_done)
        val printText = stringResource(R.string.print)
        BatchDialog(list, format, onDismiss = { batchOpen = null }) { contents, folderName ->
            batchOpen = null
            vm.createBatch(contents, format, folderName) { codes ->
                show(doneText.format(codes.size, folderName), printText) { labelFor = codes }
            }
        }
    }
    contentFor?.let { code ->
        val changed = stringResource(R.string.content_saved)
        val exists = stringResource(R.string.content_exists)
        ContentDialog(code, onDismiss = { contentFor = null }) { text ->
            vm.editContent(code, text) { ok -> show(if (ok) changed else exists) }
            contentFor = null
        }
    }
}

/** Назви сторінок угорі: вибрана — підкреслена олівцем. Дотик — перейти. */
@Composable
private fun PageTabs(position: () -> Float, selected: () -> Int, onSettings: () -> Unit, onPick: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(R.string.page_favorites, R.string.page_create).forEachIndexed { i, title ->
                // Підкреслення — окремою анімацією, не від руху сторінок: щойно вкладку вибрано, лінія пишеться від
                // початку слова до кінця; з попередньої вкладки — просто тане.
                val grow = remember { androidx.compose.animation.core.Animatable(if (selected() == i) 1f else 0f) }
                val shown = remember { androidx.compose.animation.core.Animatable(if (selected() == i) 1f else 0f) }
                LaunchedEffect(Unit) {
                    androidx.compose.runtime.snapshotFlow { selected() == i }.collect { on ->
                        if (on) {
                            shown.snapTo(1f)
                            grow.snapTo(0f)
                            grow.animateTo(1f, androidx.compose.animation.core.tween(UNDERLINE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        } else {
                            shown.animateTo(0f, androidx.compose.animation.core.tween(UNDERLINE_OUT_MS))
                        }
                    }
                }
                Text(
                    stringResource(title),
                    color = Oled.TextPrimary,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { haptics.tap(); onPick(i) }
                        // Підкреслення — ПОЗА шаром прозорості назви: напівпрозорий шар обрізається по межах тексту, і
                        // лінія під ним (трохи нижче межі) зникала посередині, поки сторінки їдуть.
                        .drawBehind {
                            val a = shown.value
                            if (a > 0.02f) pencilStroke(size.height + 1.dp.toPx(), 0f, size.width, seed = i + 2, alpha = 0.8f * a, progress = grow.value)
                        }
                        // Яскравість назви — плавно, поки сторінки їдуть.
                        .graphicsLayer {
                            val near = 1f - kotlin.math.abs(position() - i).coerceIn(0f, 1f)
                            alpha = 0.5f + 0.5f * near
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        // Налаштування (мова, стиль) — шестірня праворуч, непомітна, щоб не заважала.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable { haptics.tap(); onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Settings, stringResource(R.string.settings), tint = Oled.TextSecondary, size = 22.dp)
        }
    }
}

private const val UNDERLINE_MS = 420
private const val UNDERLINE_OUT_MS = 220

/**
 * Перехід між «Вибране» і «Створити» з дотику на назву: починається повільно, розганяється й так само плавно гальмує.
 */
private val PAGE_ANIM = androidx.compose.animation.core.tween<Float>(
    durationMillis = 520,
    easing = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f),
)

/**
 * Після свайпу — пружина без підскоку: підхоплює швидкість пальця (без ривка на початку) і м'яко зупиняється.
 */
private val PAGE_FLING = androidx.compose.animation.core.spring<Float>(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
    stiffness = 220f,
)

/** Копіювати зміст; якщо в коду своя назва (може бути пароль) — позначити як приватне, щоб система не показувала його. */
private fun copy(context: Context, code: QrCode) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(code.title, code.content)
    if (code.name != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
}
