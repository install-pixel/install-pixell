package com.blacklaunch.qrcodes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.os.Build
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.qr.Symbol
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
    // Висота рядка = висота курсора: трохи менша, щоб курсор не стирчав над рукописними літерами.
    lineHeight = 20.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Стиль тексту в полях: «Аркуш» — від руки, «Скло» — звичайний шрифт. */
@Composable
private fun fieldStyle(): TextStyle = if (LocalSheet.current) HandFieldStyle else TextStyle(fontFamily = GlassFont, fontSize = 18.sp, lineHeight = 22.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))

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
    /** Пошук у збережених: та сама капсула, лупа ліворуч, кругла кнопка — «закрити пошук». */
    searching: Boolean = false,
    onSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
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
            textStyle = fieldStyle(),
            placeholder = {
                Text(
                    stringResource(if (searching) R.string.search_hint else R.string.input_hint),
                    color = Oled.TextSecondary,
                    // Невеликий проміжок від курсора; якщо раптом задовго — «…», а не обрізане слово.
                    modifier = Modifier.padding(start = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !searching) { haptics.tap(); onModeClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(if (searching) Glyph.Search else mode.glyph(), stringResource(if (searching) R.string.search else R.string.mode_choose), tint = Oled.TextSecondary)
                }
            },
            trailingIcon = when {
                value.text.isNotEmpty() -> {
                    {
                        Text(
                            stringResource(R.string.clear),
                            color = Oled.TextSecondary,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { haptics.tap(); onClear() }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                // Порожня капсула — лупа в кінці: дотик — пошук у збережених (дотик у поле — створення коду).
                !searching -> {
                    {
                        Box(
                            Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).clickable { haptics.tap(); onSearch() },
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Search, stringResource(R.string.search), tint = Oled.TextSecondary)
                        }
                    }
                }
                else -> null
            },
            keyboardOptions = KeyboardOptions(imeAction = if (searching) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Oled.TextPrimary,
                unfocusedTextColor = Oled.TextPrimary,
                cursorColor = if (AppStyle.glass) Oled.TextPrimary else Oled.TextSecondary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(10.dp))
        if (searching) {
            RoundButton(glyph = Glyph.Close, description = stringResource(R.string.close), onClick = onCloseSearch)
        } else {
            RoundButton(
                glyph = Glyph.Save,
                description = stringResource(R.string.save),
                onClick = onSave,
                ink = if (canSave) Oled.Confirm else SheetInk,
                enabled = canSave,
            )
        }
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
 * Код на екрані: чорне на світлому «папірці» в рамці (олівцем чи склом). Світле тло — щоб код читав будь-який сканер
 * (інвертований, світлий на чорному, читають не всі). QR — квадрат, штрихкод — широка плашка зі смугами.
 * [symbol] null — порожній папірець потрібної форми ([barcode]).
 */
@Composable
fun CodeImage(
    symbol: Symbol?,
    modifier: Modifier = Modifier,
    seed: Int = 5,
    radius: Dp = 14.dp,
    barcode: Boolean = symbol is Symbol.Bars,
    /** false — без власної рамки й папірця (код малюється всередині чужої картки). */
    framed: Boolean = true,
) {
    Box(
        modifier
            .aspectRatio(if (barcode) BAR_ASPECT else 1f)
            .then(
                if (framed) {
                    Modifier.pencilFrame(radius, seed = seed).padding(4.dp).clip(RoundedCornerShape(radius - 2.dp)).background(QrPaper)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (symbol) {
            is Symbol.Qr -> Canvas(Modifier.fillMaxSize()) {
                val matrix = symbol.matrix
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
            is Symbol.Bars -> Canvas(Modifier.fillMaxSize()) {
                val bars = symbol.modules
                val cell = size.width / (bars.size + 2 * Symbol.BAR_QUIET)
                val top = size.height * 0.12f
                val h = size.height * 0.76f
                var x = 0
                while (x < bars.size) {
                    if (!bars[x]) { x++; continue }
                    val start = x
                    while (x < bars.size && bars[x]) x++
                    drawRect(
                        QrDark,
                        topLeft = androidx.compose.ui.geometry.Offset((Symbol.BAR_QUIET + start) * cell, top),
                        size = androidx.compose.ui.geometry.Size((x - start) * cell + 0.3f, h),
                    )
                }
            }
            null -> Unit
        }
    }
}

/** Ширина до висоти плашки штрихкоду на екрані. */
const val BAR_ASPECT = 2.2f

/** На екрані поле навколо коду менше за друковане (там — проміжки між кодами). */
private const val PREVIEW_QUIET = 2
val QrPaper = Color(0xFFEDEDED)
private val QrDark = Color(0xFF000000)

/** Час появи й зникнення вікон і розмиття — як у Black Glass. */
const val OPEN_MS = 180
const val CLOSE_MS = 280
private val BACKDROP_BLUR = 10.dp
private const val BACKDROP_DIM = 0.7f

/**
 * Фон під відкритим вікном/меню — розмитий і притемнений (як головний екран Black Glass під пошуком). Розмиття
 * плавно з'являється й тане; на Android 11 і старших — лише притемнення.
 */
@Composable
fun backdropAmount(open: Boolean): State<Float> =
    animateFloatAsState(if (open) 1f else 0f, tween(if (open) OPEN_MS else CLOSE_MS), label = "backdrop")

/** Значення читається лише під час малювання — вміст під вікном не перебудовується щокадру. */
fun Modifier.backdrop(amount: State<Float>): Modifier = graphicsLayer {
    val k = amount.value
    val radius = BACKDROP_BLUR.toPx() * k
    renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(radius, radius, TileMode.Decal) else null
    alpha = 1f - (1f - BACKDROP_DIM) * k
}

/**
 * Вікно поверх програми без системного затемнення (фон розмиває [backdrop]), з'являється плавно: проявляється й
 * трохи підіймається.
 */
@Composable
fun SmoothDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            // Фон за вікном — розмитий самою системою (Android 12+), як у Black Glass.
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = DIALOG_BLUR_PX }
            }
        }
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS, easing = FastOutSlowInEasing)) { it / 10 },
        ) {
            content()
        }
    }
}

private const val DIALOG_BLUR_PX = 48

/** Вікно в стилі «Аркуша»: чорна плашка в рамці олівцем, кнопки праворуч. */
@Composable
fun SheetDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    buttons: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SmoothDialog(onDismissRequest) {
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
            if (title != null) Text(title, color = Oled.TextPrimary, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Довгий вміст гортається всередині; біля заголовка й кнопок плавно тане, без різкої межі.
            // Плавні краї — у самій прокрутці вмісту (bouncyScroll): лише коли є що гортати.
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
fun SheetButton(text: String, onClick: () -> Unit, tone: Tone = Tone.Neutral, enabled: Boolean = true, compact: Boolean = false, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val color = if (enabled) tone.color() else Oled.TextSecondary.copy(alpha = 0.45f)
    val glass = AppStyle.glass
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, text.length, alpha = if (enabled) 0.75f else 0.3f, ink = if (tone == Tone.Neutral) SheetInk else color)
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            // Компактна — для рядка з кількох рівних кнопок: менші поля, щоб напис не обрізався.
            .padding(horizontal = if (compact) 4.dp else 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // «Скло»: головна дія (синя) — ще й з галочкою, як у Black Glass.
        if (tone == Tone.Confirm && glass && !compact) {
            GlyphIcon(Glyph.Save, null, tint = color, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        // Завжди один рядок: кнопка не росте в висоту й не «ламається» на два рядки.
        Text(text, color = color, fontSize = if (compact) 14.sp else 15.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Пункт меню. «Скло»: значок, назва й «›». «Аркуш»: просто написаний рядок зі стрілкою «→», без значка
 * (THEMES.md, розділ 4). Червоний — лише те, що безповоротно видаляє.
 */
@Composable
fun MenuRow(glyph: Glyph, text: String, onClick: () -> Unit, tone: Tone = Tone.Neutral) {
    val haptics = LocalHaptics.current
    val sheet = LocalSheet.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = if (sheet) 6.dp else 8.dp, vertical = if (sheet) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!sheet) {
            GlyphIcon(glyph, null, tint = if (tone == Tone.Neutral) Oled.TextSecondary else tone.color(), size = 22.dp)
            Spacer(Modifier.width(16.dp))
        }
        Text(text, color = if (tone == Tone.Neutral) Oled.TextPrimary else tone.color(), fontSize = if (sheet) 19.sp else 17.sp, modifier = Modifier.weight(1f))
        Text(if (sheet) "→" else "›", color = Oled.TextSecondary, fontSize = if (sheet) 20.sp else 22.sp)
    }
}

/**
 * Поле вводу у вікні (назва, свій розмір). «Аркуш» — у рамці олівцем. «Скло» — заокруглене 16 dp, тонкий кант
 * (під час введення яскравіший), ледь світле тло, курсор — основним кольором тексту (THEMES.md, розділ 3).
 */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (AppStyle.glass) {
                    Modifier
                        .clip(shape)
                        .background(if (focused) Color(0x0FFFFFFF) else Color(0x08FFFFFF))
                        .border(1.dp, if (focused) Color(0x99FFFFFF) else Color(0x38FFFFFF), shape)
                } else {
                    Modifier.pencilFrame(14.dp, seed = 23, alpha = if (focused) 0.9f else 0.6f)
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Oled.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = fieldStyle().copy(color = Oled.TextPrimary),
            cursorBrush = SolidColor(if (AppStyle.glass) Oled.TextPrimary else Oled.TextSecondary),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

/**
 * Сповіщення — завжди однакове: на всю ширину (з полями), сталої висоти, вгорі під рядком стану, на чорному клаптику
 * в рамці олівцем. Дія («Повернути») — праворуч. Так само виглядають і підказки камери.
 */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NOTICE_HEIGHT)
            // М'яка темна «тінь» довкола: те, що під сповіщенням (кнопки, заголовок), плавно притемнюється до його краю,
            // тож плашка лежить зверху, а не «наліплена» на чужі елементи.
            .drawBehind {
                // Розмиту тінь апаратне малювання вміє з Android 9; на старіших — без неї.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) drawIntoCanvas { canvas ->
                    val halo = NOTICE_HALO.toPx()
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.BLACK
                        alpha = 235
                        maskFilter = android.graphics.BlurMaskFilter(halo, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    val grow = halo * 0.6f
                    val r = size.height / 2 + grow
                    canvas.nativeCanvas.drawRoundRect(-grow, -grow, size.width + grow, size.height + grow, r, r, paint)
                }
            }
            .background(Oled.Black, RoundedCornerShape(NOTICE_HEIGHT / 2))
            .pencilFrame(NOTICE_HEIGHT / 2, seed = 17)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            color = Oled.TextPrimary,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            SheetButton(action, onAction)
        }
    }
}

private val NOTICE_HEIGHT = 56.dp
private val NOTICE_HALO = 22.dp

/** Місце сповіщень — одне на всю програму: угорі, під рядком стану. */
fun Modifier.noticePlace(): Modifier = statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)

/** Сповіщення, що з'являється й тане («Збережено», «Видалено» + «Повернути», «Немає місця»…). */
@Composable
fun Banner(text: String?, action: String? = null, onAction: () -> Unit = {}) {
    var shown by remember { mutableStateOf("") }
    var shownAction by remember { mutableStateOf<String?>(null) }
    if (text != null) {
        shown = text
        shownAction = action
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = text != null,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { -it / 3 },
            exit = fadeOut(tween(CLOSE_MS)) + slideOutVertically(tween(CLOSE_MS)) { -it / 3 },
            modifier = Modifier.noticePlace(),
        ) {
            Notice(shown, action = shownAction, onAction = onAction)
        }
    }
}

/** Наскільки плавно тане край прокрутки (THEMES.md, розділ 5). */
val EdgeFade = 18.dp

/**
 * Список, що гортається, не обрізається різко: вгорі й унизу вміст плавно тане в чорне — але ЛИШЕ з того боку, куди
 * ще можна гортати, і не більше, ніж уже прогорнуто. У спокої (на самому верху чи коли все вміщається) нічого не тане,
 * тож верхній край першого поля чи рамки лишається чітким.
 */
fun Modifier.fadingEdges(top: Dp = EdgeFade, bottom: Dp = EdgeFade): Modifier =
    fadeMask({ top.toPx() }, { bottom.toPx() })

/** Для [androidx.compose.foundation.ScrollState] (звичайна прокрутка). */
fun Modifier.fadingEdges(state: androidx.compose.foundation.ScrollState, edge: Dp = EdgeFade): Modifier = fadeMask(
    top = { minOf(state.value.toFloat(), edge.toPx()) },
    bottom = {
        val max = state.maxValue
        if (max == Int.MAX_VALUE) 0f else minOf((max - state.value).toFloat(), edge.toPx())
    },
)

/** Для списку. */
fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.LazyListState, edge: Dp = EdgeFade): Modifier = fadeMask(
    top = { if (state.firstVisibleItemIndex > 0) edge.toPx() else minOf(state.firstVisibleItemScrollOffset.toFloat(), edge.toPx()) },
    bottom = {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        when {
            last == null -> 0f
            last.index < info.totalItemsCount - 1 -> edge.toPx()
            else -> minOf((last.offset + last.size - info.viewportEndOffset + info.afterContentPadding).toFloat().coerceAtLeast(0f), edge.toPx())
        }
    },
)

/** Для сітки. */
fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.grid.LazyGridState, edge: Dp = EdgeFade): Modifier = fadeMask(
    top = { if (state.firstVisibleItemIndex > 0) edge.toPx() else minOf(state.firstVisibleItemScrollOffset.toFloat(), edge.toPx()) },
    bottom = {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        when {
            last == null -> 0f
            last.index < info.totalItemsCount - 1 -> edge.toPx()
            else -> minOf((last.offset.y + last.size.height - info.viewportEndOffset + info.afterContentPadding).toFloat().coerceAtLeast(0f), edge.toPx())
        }
    },
)

/** Маска країв: [top] і [bottom] — у px, рахуються під час малювання (не перебудовують екран при прокрутці). */
private fun Modifier.fadeMask(top: androidx.compose.ui.unit.Density.() -> Float, bottom: androidx.compose.ui.unit.Density.() -> Float): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val t = top().coerceIn(0f, size.height / 3)
        val b = bottom().coerceIn(0f, size.height / 3)
        if (t > 0.5f) {
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = t),
                size = androidx.compose.ui.geometry.Size(size.width, t),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
        if (b > 0.5f) {
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - b, endY = size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - b),
                size = androidx.compose.ui.geometry.Size(size.width, b),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
    }

/**
 * Вибір-«табличка» (QR / Штрихкод тощо). Вибраний — яскравим кантом і галочкою ✓ (не кольором); текст в один рядок.
 */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = text.length + 3, alpha = if (selected) 0.9f else 0.35f, ink = if (selected) SelectedInk else SheetInk)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Місце під ✓ є завжди (невибрана — просто невидима), тож кнопка не міняє розмір і нічого не смикається.
        GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary.copy(alpha = if (selected) 1f else 0f), size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}
