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
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val focus = remember { FocusRequester() }

    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by rememberSaveable { mutableStateOf(InputMode.Manual) }
    var modeMenu by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var camera by rememberSaveable { mutableStateOf<InputMode?>(null) }
    var pendingCamera by remember { mutableStateOf<InputMode?>(null) }
    var fullscreen by remember { mutableStateOf<QrCode?>(null) }
    val dropZone = remember { DropZone() }
    var menuFor by remember { mutableStateOf<QrCode?>(null) }
    var renameFor by remember { mutableStateOf<QrCode?>(null) }
    var labelFor by remember { mutableStateOf<List<QrCode>?>(null) }
    var banner by remember { mutableStateOf<BannerMsg?>(null) }
    var focusAfterCamera by remember { mutableIntStateOf(0) }

    val textSaved = stringResource(R.string.saved)
    val textAlready = stringResource(R.string.already_saved)
    val textDeleted = stringResource(R.string.deleted)
    val textUndo = stringResource(R.string.undo)
    val textCopied = stringResource(R.string.copied)
    val textUnfavorited = stringResource(R.string.unfavorited)
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
        if (granted) camera = pendingCamera else show(textNoCamera)
        pendingCamera = null
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

    BackHandler(enabled = pager.currentPage == 1 && camera == null && labelFor == null) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    // Під меню, вікнами, камерою й друком — розмитий і притемнений фон, як у Black Glass.
    val blur = backdropAmount(settingsOpen || modeMenu || menuFor != null || renameFor != null || fullscreen != null || camera != null || labelFor != null)
    // Останні значення лишаються, поки екран плавно зникає.
    var lastCamera by remember { mutableStateOf(InputMode.ScanQr) }
    camera?.let { lastCamera = it }
    var lastLabel by remember { mutableStateOf<List<QrCode>>(emptyList()) }
    labelFor?.let { lastLabel = it }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().backdrop(blur).statusBarsPadding().navigationBarsPadding().imePadding()) {
            PageTabs(pager.currentPage, onSettings = { settingsOpen = true }) { scope.launch { pager.animateScrollToPage(it) } }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
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
                        onInputChange = { input = it },
                        mode = mode,
                        onModeClick = { modeMenu = true },
                        onSave = {
                            vm.save(input.text) { show(if (it == SaveResult.Saved) textSaved else textAlready) }
                        },
                        saved = saved,
                        onOpen = { fullscreen = it },
                        onMenu = { menuFor = it },
                        onDelete = ::delete,
                        focusRequester = focus,
                    )
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
                onResult = { text ->
                    input = TextFieldValue(text, TextRange(text.length))
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

        // Підказки — поверх усього (і камери), по центру.
        Banner(banner?.text, banner?.action, banner?.onAction ?: {})
    }

    if (settingsOpen) {
        SettingsDialog(onDismiss = { settingsOpen = false }, onLanguageChanged = onLanguageChanged)
    }

    if (modeMenu) {
        ModeMenu(onDismiss = { modeMenu = false }) { picked ->
            modeMenu = false
            mode = picked
            when (picked) {
                InputMode.Manual -> focusAfterCamera++
                InputMode.ScanQr, InputMode.PhotoText -> openCamera(picked)
            }
        }
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
        )
    }

    renameFor?.let { code ->
        RenameDialog(code, onDismiss = { renameFor = null }) {
            vm.rename(code, it)
            renameFor = null
        }
    }
}

/** Назви сторінок угорі: вибрана — підкреслена олівцем. Дотик — перейти. */
@Composable
private fun PageTabs(current: Int, onSettings: () -> Unit, onPick: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(R.string.page_favorites, R.string.page_create).forEachIndexed { i, title ->
                val selected = current == i
                Text(
                    stringResource(title),
                    color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { haptics.tap(); onPick(i) }
                        .drawBehind { if (selected) pencilStroke(size.height + 1.dp.toPx(), 0f, size.width, seed = i + 2) }
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

/** Копіювати зміст; якщо в коду своя назва (може бути пароль) — позначити як приватне, щоб система не показувала його. */
private fun copy(context: Context, code: QrCode) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(code.title, code.content)
    if (code.name != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
}
