package com.blacklaunch.diary.book

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.RoundButton
import com.blacklaunch.diary.ui.SelectedInk
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.theme.Oled

/**
 * Своя камера (як у QRaft), а не системна: на весь екран видошукач, унизу — закрити, затвор, ліхтарик і інша камера
 * (задня / фронтальна). Знімок нікуди не зберігається — одразу віддається щоденнику ([onShot]), який його шифрує.
 */
@Composable
fun DiaryCamera(onShot: (Bitmap) -> Unit, onClose: () -> Unit, onFail: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Спалах затвора — сторінка ледь блимає, як справжній знімок.
    var flash by remember { mutableStateOf(false) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(2400, 1800), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(front) {
        // Перемикати камеру можна лише після того, як вона ініціалізувалась.
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c ->
            controller.initializationFuture.addListener({ if (c.isActive) c.resume(Unit) {} }, ContextCompat.getMainExecutor(context))
        }
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (runCatching { controller.hasCamera(sel) }.getOrDefault(false)) controller.cameraSelector = sel
    }
    LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }
    LaunchedEffect(flash) { if (flash) { kotlinx.coroutines.delay(120); flash = false } }
    BackHandler(onBack = onClose)

    fun take() {
        if (busy) return
        busy = true
        flash = true
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.use { it.toUprightBitmap(mirror = front) }
                    busy = false
                    onShot(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    onFail()
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // TextureView: видошукач поводиться як звичайна частина екрана (у вікні, з анімаціями).
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (flash) 0.55f else 0f }.background(Oled.Black))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Close, stringResource(R.string.close), onClose)
            RoundButton(Glyph.Shutter, stringResource(R.string.photo_from_camera), { take() }, size = 72.dp, enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundButton(Glyph.Flash, stringResource(R.string.flashlight), { torch = !torch }, ink = if (torch) SelectedInk else SheetInk)
                RoundButton(Glyph.RotateRight, stringResource(R.string.camera_switch), { torch = false; front = !front })
            }
        }
    }
}

/** Знімок у правильному положенні (і з фронтальної — як у дзеркалі, як бачиш себе на екрані). */
private fun ImageProxy.toUprightBitmap(mirror: Boolean): Bitmap {
    val raw = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0 && !mirror) return raw
    val m = Matrix().apply {
        postRotate(degrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    if (out != raw) raw.recycle()
    return out
}
