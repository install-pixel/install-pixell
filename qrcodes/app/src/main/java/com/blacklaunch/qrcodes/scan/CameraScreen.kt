package com.blacklaunch.qrcodes.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.ui.Glyph
import com.blacklaunch.qrcodes.ui.LocalHaptics
import com.blacklaunch.qrcodes.ui.RoundButton
import com.blacklaunch.qrcodes.ui.SheetButton
import com.blacklaunch.qrcodes.ui.SheetInk
import com.blacklaunch.qrcodes.ui.pencilFrame
import com.blacklaunch.qrcodes.ui.noticePlace
import com.blacklaunch.qrcodes.ui.fadingEdges
import com.blacklaunch.qrcodes.ui.pencilLoop
import com.blacklaunch.qrcodes.ui.taperedPath
import com.blacklaunch.qrcodes.ui.theme.Oled
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Рядок тексту, знайдений на фото, і де він (у пікселях фото). */
data class FoundWord(val text: String, val box: Rect)

/** Рядок тексту з фото — словами (кожне можна вибрати окремо). */
data class FoundLine(val words: List<FoundWord>) {
    val text get() = words.joinToString(" ") { it.text }
}

private sealed interface PhotoState {
    data object Live : PhotoState
    data object Working : PhotoState
    data class Done(val bitmap: Bitmap, val found: List<FoundLine>) : PhotoState
}

/**
 * Камера на весь екран.
 * - [scanQr] = true: наводиш на QR-код (чи штрихкод) — його зміст одразу повертається в генератор, і на екрані
 *   з'являється дубль коду.
 * - [scanQr] = false: фото → ML Kit знаходить тексти (лише довші за 5 символів) → торкаєшся потрібного → він
 *   іде в поле, де його можна підправити.
 * Усе розпізнавання — на телефоні, без мережі.
 */
@Composable
fun CameraScreen(scanQr: Boolean, onResult: (String) -> Unit, onClose: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var torch by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<PhotoState>(PhotoState.Live) }
    var delivered by remember { mutableStateOf(false) }
    val notFoundQr = stringResource(R.string.scan_not_found)
    val notFoundText = stringResource(R.string.text_not_found)

    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
        }
    }

    fun deliver(text: String) {
        if (delivered) return
        delivered = true
        haptics.longPress()
        result(text)
    }

    DisposableEffect(scanQr) {
        val executor = ContextCompat.getMainExecutor(context)
        if (scanQr) {
            controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            controller.setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { r ->
                    val value = r.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotEmpty() } }
                    if (value != null) deliver(value)
                },
            )
        } else {
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            recognizer.close()
        }
    }
    LaunchedEffect(torch) { controller.enableTorch(torch) }

    suspend fun recognize(bitmap: Bitmap) {
        photo = PhotoState.Working
        val found = withContext(Dispatchers.Default) {
            val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            val lines = text?.textBlocks.orEmpty().flatMap { block -> block.lines }.mapNotNull { line ->
                val words = line.elements.mapNotNull { e -> e.boundingBox?.let { FoundWord(e.text.trim(), it) } }.filter { it.text.isNotEmpty() }
                if (words.isEmpty()) null else FoundLine(words)
            }
            // Рядки, коротші за 6 символів, не показуємо — у довших можна вибрати й окремі слова.
            TextPick.filter(lines) { it.text }
        }
        if (found.isEmpty()) onMessage(notFoundText)
        photo = PhotoState.Done(bitmap, found)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (scanQr) {
                val value = withContext<String?>(Dispatchers.Default) {
                    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() ?: return@withContext null
                    suspendCancellableCoroutine<String?> { cont ->
                        scanner.process(image)
                            .addOnSuccessListener { list -> cont.resume(list.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotEmpty() } }) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                if (value != null) deliver(value) else onMessage(notFoundQr)
            } else {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
                if (bitmap != null) recognize(bitmap) else onMessage(notFoundText)
            }
        }
    }

    fun takePhoto() {
        photo = PhotoState.Working
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.use { it.toUprightBitmap() }
                    scope.launch { recognize(bitmap) }
                }

                override fun onError(exception: ImageCaptureException) {
                    photo = PhotoState.Live
                    onMessage(notFoundText)
                }
            },
        )
    }

    val done = photo as? PhotoState.Done
    BackHandler { if (done != null) photo = PhotoState.Live else onClose() }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        if (done == null) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                modifier = Modifier.fillMaxSize(),
            )
            if (scanQr) ScanFrame()
            // Підказка — як усі сповіщення програми: те саме місце, той самий вигляд.
            com.blacklaunch.qrcodes.ui.Notice(
                stringResource(
                    when {
                        photo == PhotoState.Working -> R.string.recognizing
                        scanQr -> R.string.scan_hint
                        else -> R.string.photo_hint
                    },
                ),
                modifier = Modifier.align(Alignment.TopCenter).then(Modifier.noticePlace()),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Glyph.Close, stringResource(R.string.close), onClose)
                if (!scanQr) {
                    RoundButton(Glyph.Shutter, stringResource(R.string.take_photo), { if (photo == PhotoState.Live) takePhoto() }, size = 72.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundButton(Glyph.Flash, stringResource(R.string.flashlight), { torch = !torch }, ink = if (torch) Oled.Confirm else SheetInk)
                    RoundButton(Glyph.Gallery, stringResource(R.string.from_gallery), {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }
        } else {
            FoundTextView(done, onPick = { deliver(it) }, onRetake = { photo = PhotoState.Live }, onClose = onClose)
        }
    }
}

/** Рамка-видошукач олівцем посередині. */
@Composable
private fun ScanFrame() {
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.62f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        translate(left, top) {
            drawPath(taperedPath(pencilLoop(Size(side, side), 28.dp.toPx(), 41, 2.dp.toPx()), 3.dp.toPx()), SheetInk.copy(alpha = 0.9f))
        }
    }
}

/**
 * Фото з усіма знайденими словами. Торкнись слова — вибрати/зняти; веди пальцем по словах — вибрати кілька поспіль.
 * Нижче — той самий текст рядками: слова-«таблички» і кнопка «весь рядок». Внизу — що вибрано, і «Готово»:
 * вибране йде в поле, де його ще можна підправити.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FoundTextView(state: PhotoState.Done, onPick: (String) -> Unit, onRetake: () -> Unit, onClose: () -> Unit) {
    val haptics = LocalHaptics.current
    var selected by remember(state) { mutableStateOf<Set<WordRef>>(emptySet()) }
    val words = state.found.map { line -> line.words.map { it.text } }
    val chosen = TextPick.join(words, selected)

    fun toggle(ref: WordRef) {
        haptics.tap()
        selected = if (ref in selected) selected - ref else selected + ref
    }

    // Затиснув слово й ведеш — вибирається (або знімається) все від першого слова до того, що під пальцем.
    val order = remember(state) { state.found.flatMapIndexed { li, line -> line.words.indices.map { WordRef(li, it) } } }
    val drag = remember(state) { RangeDrag(order) }
    fun startRange(ref: WordRef?) {
        if (ref == null) return
        haptics.longPress()
        selected = drag.start(ref, selected)
    }
    fun moveRange(ref: WordRef?) {
        val next = drag.move(ref) ?: return
        if (next != selected) {
            haptics.tick()
            selected = next
        }
    }
    // Де стоїть кожне слово-«табличка» у списку (для вибору пальцем і там).
    val chipBounds = remember(state) { HashMap<WordRef, androidx.compose.ui.geometry.Rect>() }
    var listOrigin by remember(state) { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Text(
            stringResource(if (state.found.isEmpty()) R.string.text_not_found else R.string.text_pick),
            color = Oled.TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val bw = state.bitmap.width.toFloat()
            val bh = state.bitmap.height.toFloat()
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val scale = minOf(boxW / bw, boxH / bh)
            val offX = (boxW - bw * scale) / 2f
            val offY = (boxH - bh * scale) / 2f
            val padPx = with(density) { 6.dp.toPx() }
            fun hit(p: Offset): WordRef? {
                state.found.forEachIndexed { li, line ->
                    line.words.forEachIndexed { wi, w ->
                        val r = w.box
                        if (p.x >= offX + r.left * scale - padPx && p.x <= offX + r.right * scale + padPx &&
                            p.y >= offY + r.top * scale - padPx && p.y <= offY + r.bottom * scale + padPx
                        ) return WordRef(li, wi)
                    }
                }
                return null
            }
            Image(state.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(state) { detectTapGestures { tap -> hit(tap)?.let(::toggle) } }
                    .pointerInput(state) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { p -> startRange(hit(p)) },
                            onDrag = { change, _ -> moveRange(hit(change.position)) },
                            onDragEnd = { drag.end() },
                            onDragCancel = { drag.end() },
                        )
                    },
            ) {
                state.found.forEachIndexed { li, line ->
                    line.words.forEachIndexed { wi, w ->
                        val on = WordRef(li, wi) in selected
                        val r = w.box
                        val pad = 2.dp.toPx()
                        val ww = r.width() * scale + 2 * pad
                        val hh = r.height() * scale + 2 * pad
                        translate(offX + r.left * scale - pad, offY + r.top * scale - pad) {
                            drawRect(if (on) Oled.Confirm.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.2f), size = Size(ww, hh))
                            drawPath(
                                taperedPath(pencilLoop(Size(ww, hh), 4.dp.toPx(), li * 31 + wi, 0.5.dp.toPx()), (if (on) 2.4 else 1.4).dp.toPx()),
                                if (on) Oled.Confirm else SheetInk.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
        if (state.found.isNotEmpty()) {
            fun chipAt(p: Offset): WordRef? {
                val abs = p + listOrigin
                return chipBounds.entries.firstOrNull { it.value.inflate(4f).contains(abs) }?.key
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(top = 10.dp)
                    .fadingEdges(14.dp)
                    .verticalScroll(rememberScrollState())
                    // Усередині прокрутки: після затискання тягнемо вибір, а не прокручуємо список.
                    .onGloballyPositioned { listOrigin = it.positionInRoot() }
                    .pointerInput(state) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { p -> startRange(chipAt(p)) },
                            onDrag = { change, _ ->
                                change.consume()
                                moveRange(chipAt(change.position))
                            },
                            onDragEnd = { drag.end() },
                            onDragCancel = { drag.end() },
                        )
                    },
            ) {
                for (li in state.found.indices) {
                    val line = state.found[li]
                    val all = line.words.indices.all { WordRef(li, it) in selected }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        // Весь рядок одним дотиком.
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .pencilFrame(10.dp, seed = li + 70, alpha = if (all) 0.9f else 0.4f, ink = if (all) Oled.Confirm else SheetInk)
                                .clickable {
                                    haptics.tap()
                                    val refs = line.words.indices.map { WordRef(li, it) }.toSet()
                                    selected = if (all) selected - refs else selected + refs
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("≡", color = if (all) Oled.Confirm else Oled.TextSecondary, fontSize = 18.sp)
                        }
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.weight(1f).padding(start = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            line.words.forEachIndexed { wi, w ->
                                val on = WordRef(li, wi) in selected
                                Text(
                                    w.text,
                                    color = if (on) Oled.TextPrimary else Oled.TextSecondary,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) Oled.Confirm.copy(alpha = 0.18f) else Color.Transparent)
                                        .pencilFrame(10.dp, seed = li * 13 + wi, alpha = if (on) 0.9f else 0.3f, ink = if (on) Oled.Confirm else SheetInk)
                                        .onGloballyPositioned { chipBounds[WordRef(li, wi)] = androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize()) }
                                        .clickable { toggle(WordRef(li, wi)) }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Що піде в код.
        Text(
            if (chosen.isEmpty()) stringResource(R.string.text_pick_none) else chosen,
            color = if (chosen.isEmpty()) Oled.TextSecondary else Oled.TextPrimary,
            fontSize = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).pencilFrame(14.dp, seed = 77, alpha = 0.5f).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            SheetButton(stringResource(R.string.close), onClose)
            SheetButton(stringResource(R.string.retake), onRetake)
            if (selected.isNotEmpty()) SheetButton(stringResource(R.string.clear), { selected = emptySet() })
            SheetButton(stringResource(R.string.done), { onPick(chosen) }, tone = com.blacklaunch.qrcodes.ui.Tone.Confirm, enabled = chosen.isNotEmpty())
        }
    }
}

/** Кадр з камери — повернутий так, як тримали телефон. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return raw
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (rotated != raw) raw.recycle()
    return rotated
}

/** Картинка з галереї, зменшена до ~2000 px (для розпізнавання більше не треба). */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val max = maxOf(info.size.width, info.size.height)
            if (max > MAX_SIDE) {
                val k = MAX_SIDE / max.toFloat()
                decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}.getOrNull()

private const val MAX_SIDE = 2000
