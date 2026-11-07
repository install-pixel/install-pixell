package com.blacklaunch.paleta.scan

import androidx.activity.compose.BackHandler
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.ui.AppStyle
import com.blacklaunch.paleta.ui.Glyph
import com.blacklaunch.paleta.ui.LocalHaptics
import com.blacklaunch.paleta.ui.Notice
import com.blacklaunch.paleta.ui.RoundButton
import com.blacklaunch.paleta.ui.SelectedInk
import com.blacklaunch.paleta.ui.SheetInk
import com.blacklaunch.paleta.ui.noticePlace
import com.blacklaunch.paleta.ui.outline
import com.blacklaunch.paleta.ui.theme.Oled
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning

/**
 * Сканер SKU на весь екран: наводиш камеру на штрихкод (чи QR) — номер одразу повертається. Зчитується лише те, що
 * в рамці (широка — під штрихкод). Розпізнавання — на телефоні, без мережі. Камера працює, лише поки видно цей екран.
 */
@Composable
fun ScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHaptics.current
    val result by rememberUpdatedState(onResult)
    var torch by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val controller = remember { LifecycleCameraController(context) }
    val previewSize = remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { r ->
                val view = previewSize.value
                val frame = scanFrame(view.width.toFloat(), view.height.toFloat())
                val found = r.getValue(scanner)?.firstOrNull { b ->
                    val box = b.boundingBox
                    val inside = box == null || view.width == 0 || frame.contains(Offset(box.exactCenterX(), box.exactCenterY()))
                    !b.rawValue.isNullOrBlank() && inside
                }
                if (found != null && !delivered) {
                    delivered = true
                    haptics.longPress()
                    result(found.rawValue!!.trim())
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }
    LaunchedEffect(torch) { controller.enableTorch(torch) }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize().onSizeChanged { previewSize.value = it },
        )
        ScanFrame()
        Notice(stringResource(R.string.scan_hint), modifier = Modifier.align(Alignment.TopCenter).then(Modifier.noticePlace()))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Close, stringResource(R.string.close), onClose)
            RoundButton(Glyph.Flash, stringResource(R.string.flashlight), { torch = !torch }, ink = if (torch) SelectedInk else SheetInk)
        }
    }
}

/** Видошукач: усе довкола рамки — чорне; рамка — олівцем («Аркуш») або тонкою скляною гранню («Скло»). */
@Composable
private fun ScanFrame() {
    val glass = AppStyle.glass
    Canvas(Modifier.fillMaxSize()) {
        val f = scanFrame(size.width, size.height)
        val r = 24.dp.toPx()
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(f, CornerRadius(r)))
        }
        drawPath(hole, Oled.Black)
        if (glass) {
            drawRoundRect(Oled.GlintBright, f.topLeft, f.size, CornerRadius(r), style = Stroke(1.5.dp.toPx()))
        } else {
            outline(f.topLeft, Size(f.width, f.height), r, 41, SheetInk.copy(alpha = 0.95f), 3.dp.toPx())
            outline(f.topLeft, Size(f.width, f.height), r, 142, SheetInk.copy(alpha = 0.35f), 1.6.dp.toPx())
        }
    }
}

/** Рамка — широка (під штрихкод): 84 % ширини, висота — 55 % від ширини рамки, посередині. */
private fun scanFrame(w: Float, h: Float): Rect {
    val width = w * 0.84f
    val height = (width * 0.55f).coerceAtMost(h * 0.5f)
    val left = (w - width) / 2f
    val top = (h - height) / 2f
    return Rect(left, top, left + width, top + height)
}
