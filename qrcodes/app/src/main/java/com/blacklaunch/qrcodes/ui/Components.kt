package com.blacklaunch.qrcodes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.qr.QrMatrix
import com.blacklaunch.qrcodes.ui.theme.Oled

private val FieldShape = RoundedCornerShape(28.dp)

/** Звідки береться текст для коду. */
enum class InputMode { Manual, ScanQr, PhotoText }

fun InputMode.glyph(): Glyph = when (this) {
    InputMode.Manual -> Glyph.Keyboard
    InputMode.ScanQr -> Glyph.ScanQr
    InputMode.PhotoText -> Glyph.Photo
}

/** Стиль тексту в полі «Аркуша»: рядок обрізаний до літер, щоб курсор був звичайної висоти (як у Black Glass). */
private val HandFieldStyle = TextStyle(
    fontSize = 18.sp,
    fontFamily = HandFont,
    lineHeight = 22.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * Капсула внизу — як поле пошуку Black Glass, на тому самому місці: поле з рамкою олівцем, усередині ліворуч —
 * вибір способу (вручну / сканувати QR / текст із фото), праворуч — «Очистити»; збоку — кругла кнопка «Зберегти».
 */
@Composable
fun InputCapsule(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    mode: InputMode,
    onModeClick: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .pencilFrame(28.dp, seed = 11)
                .clip(FieldShape)
                .focusRequester(focusRequester),
            maxLines = 4,
            textStyle = HandFieldStyle,
            placeholder = {
                Text(stringResource(R.string.input_hint), color = Oled.TextSecondary, modifier = Modifier.padding(start = 3.dp), maxLines = 1)
            },
            leadingIcon = {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { haptics.tap(); onModeClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(mode.glyph(), stringResource(R.string.mode_choose), tint = Oled.TextSecondary)
                }
            },
            trailingIcon = if (value.text.isNotEmpty()) {
                {
                    Text(
                        stringResource(R.string.clear),
                        color = Oled.TextSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { haptics.tap(); onClear() }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Oled.TextPrimary,
                unfocusedTextColor = Oled.TextPrimary,
                cursorColor = Oled.TextSecondary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(10.dp))
        RoundButton(
            glyph = Glyph.Save,
            description = stringResource(R.string.save),
            onClick = onSave,
            ink = if (canSave) Oled.Confirm else SheetInk,
            enabled = canSave,
        )
    }
}

/** Кругла кнопка 56 dp з рамкою олівцем — як мікрофон у Black Glass. */
@Composable
fun RoundButton(glyph: Glyph, description: String, onClick: () -> Unit, ink: Color = SheetInk, enabled: Boolean = true, size: Dp = 56.dp) {
    val haptics = LocalHaptics.current
    Box(
        modifier = Modifier
            .size(size)
            .background(Oled.Black, CircleShape)
            .pencilFrame(size / 2, seed = 13, ink = ink, alpha = if (enabled) 0.85f else 0.35f)
            .clip(CircleShape)
            .clickable(enabled = enabled) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, description, tint = if (enabled) ink else Oled.TextSecondary.copy(alpha = 0.5f))
    }
}

/**
 * QR-код на екрані: чорні модулі на світлому «папірці» в рамці олівцем. Світле тло — щоб код читав будь-який
 * сканер (інвертований, світлий на чорному, читають не всі).
 */
@Composable
fun QrImage(matrix: QrMatrix?, modifier: Modifier = Modifier, seed: Int = 5, radius: Dp = 14.dp) {
    Box(
        modifier
            .aspectRatio(1f)
            .pencilFrame(radius, seed = seed)
            .padding(4.dp)
            .clip(RoundedCornerShape(radius - 2.dp))
            .background(QrPaper),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val total = matrix.size + 2 * PREVIEW_QUIET
                val cell = size.minDimension / total
                val off = (size.minDimension - cell * matrix.size) / 2f
                for (y in 0 until matrix.size) {
                    var x = 0
                    while (x < matrix.size) {
                        if (!matrix[x, y]) { x++; continue }
                        val start = x
                        while (x < matrix.size && matrix[x, y]) x++
                        drawRect(
                            QrDark,
                            topLeft = androidx.compose.ui.geometry.Offset(off + start * cell, off + y * cell),
                            size = androidx.compose.ui.geometry.Size((x - start) * cell + 0.5f, cell + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/** На екрані поле навколо коду менше за друковане (там — проміжки між кодами). */
private const val PREVIEW_QUIET = 2
val QrPaper = Color(0xFFEDEDED)
private val QrDark = Color(0xFF000000)

/** Вікно в стилі «Аркуша»: чорна плашка в рамці олівцем, кнопки праворуч. */
@Composable
fun SheetDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    buttons: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding()
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0A0A0A))
                .pencilFrame(24.dp, seed = 7)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (title != null) Text(title, color = Oled.TextPrimary, fontSize = 20.sp)
            Box(Modifier.weight(1f, fill = false)) {
                CompositionLocalProvider(LocalContentColor provides Oled.TextSecondary) { content() }
            }
            if (buttons != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    buttons()
                }
            }
        }
    }
}

/** Значення кнопки: звичайна, «підтвердити» (синя рамка) чи «видалити» (червона). */
enum class Tone { Neutral, Confirm, Danger }

fun Tone.color(): Color = when (this) {
    Tone.Neutral -> Oled.TextPrimary
    Tone.Confirm -> Oled.Confirm
    Tone.Danger -> Oled.Danger
}

/** Кнопка-«табличка» з рамкою олівцем (як кнопки вікон Black Glass в «Аркуші»). */
@Composable
fun SheetButton(text: String, onClick: () -> Unit, tone: Tone = Tone.Neutral, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val color = if (enabled) tone.color() else Oled.TextSecondary.copy(alpha = 0.45f)
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, text.length, alpha = if (enabled) 0.75f else 0.3f, ink = if (tone == Tone.Neutral) SheetInk else color)
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** Рядок меню: значок олівцем і назва. */
@Composable
fun MenuRow(glyph: Glyph, text: String, onClick: () -> Unit, tone: Tone = Tone.Neutral) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, null, tint = if (tone == Tone.Neutral) SheetInk else tone.color())
        Spacer(Modifier.width(16.dp))
        Text(text, color = tone.color(), fontSize = 17.sp)
    }
}

/** Поле вводу у вікні (назва, свій розмір): рядок, підкреслений олівцем. */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .pencilFrame(14.dp, seed = 23, alpha = 0.6f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Oled.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = HandFieldStyle.copy(color = Oled.TextPrimary),
            cursorBrush = SolidColor(Oled.TextSecondary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

/**
 * Підказка («Збережено», «Видалено» + «Повернути») — по центру екрана, на чорному клаптику в рамці олівцем;
 * дія — під текстом.
 */
@Composable
fun Banner(text: String?, action: String? = null, onAction: () -> Unit = {}) {
    var shown by remember { mutableStateOf("") }
    var shownAction by remember { mutableStateOf<String?>(null) }
    if (text != null) {
        shown = text
        shownAction = action
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier
                    .background(Oled.Black, RoundedCornerShape(20.dp))
                    .pencilFrame(20.dp, seed = shown.length)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(shown, color = Oled.TextPrimary, fontSize = 19.sp, textAlign = TextAlign.Center)
                shownAction?.let { label -> SheetButton(label, onAction, tone = Tone.Confirm) }
            }
        }
    }
}
