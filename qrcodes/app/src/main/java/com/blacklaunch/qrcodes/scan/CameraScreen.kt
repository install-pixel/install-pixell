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
data class FoundText(val text: String, val box: Rect)

private sealed interface PhotoState {
    data object Live : PhotoState
    data object Working : PhotoState
    data class Done(val bitmap: Bitmap, val found: List<FoundText>) : PhotoState
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
                line.boundingBox?.let { FoundText(line.text.trim(), it) }
            }
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
            Text(
                stringResource(if (scanQr) R.string.scan_hint else R.string.photo_hint),
                color = Oled.TextPrimary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                    .background(Oled.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .pencilFrame(16.dp, seed = 31, alpha = 0.6f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (photo == PhotoState.Working) {
                Text(
                    stringResource(R.string.recognizing),
                    color = Oled.TextPrimary,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center).background(Oled.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(16.dp),
                )
            }
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
 * Фото зі знайденими текстами: кожен — у рамці олівцем, торкнись потрібного. Нижче — ті самі тексти списком
 * (зручніше, коли рамки дрібні).
 */
@Composable
private fun FoundTextView(state: PhotoState.Done, onPick: (String) -> Unit, onRetake: () -> Unit, onClose: () -> Unit) {
    val haptics = LocalHaptics.current
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
            Image(state.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(state) {
                    detectTapGestures { tap ->
                        val hit = state.found.firstOrNull { f ->
                            val r = f.box
                            val pad = 8.dp.toPx()
                            tap.x >= offX + r.left * scale - pad && tap.x <= offX + r.right * scale + pad &&
                                tap.y >= offY + r.top * scale - pad && tap.y <= offY + r.bottom * scale + pad
                        }
                        if (hit != null) onPick(hit.text)
                    }
                },
            ) {
                state.found.forEachIndexed { i, f ->
                    val r = f.box
                    val pad = 3.dp.toPx()
                    val w = r.width() * scale + 2 * pad
                    val h = r.height() * scale + 2 * pad
                    translate(offX + r.left * scale - pad, offY + r.top * scale - pad) {
                        drawRect(Color.Black.copy(alpha = 0.25f), size = Size(w, h))
                        drawPath(taperedPath(pencilLoop(Size(w, h), 6.dp.toPx(), i + 50, 0.8.dp.toPx()), 2.2.dp.toPx()), Oled.Confirm)
                    }
                }
            }
        }
        if (state.found.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 10.dp)) {
                items(state.found) { f ->
                    Text(
                        f.text,
                        color = Oled.TextPrimary,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { haptics.tap(); onPick(f.text) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SheetButton(stringResource(R.string.close), onClose)
            SheetButton(stringResource(R.string.retake), onRetake, tone = com.blacklaunch.qrcodes.ui.Tone.Confirm)
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
