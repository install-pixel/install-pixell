package com.blacklaunch.diary.ui

import androidx.compose.foundation.layout.fillMaxHeight

import kotlinx.coroutines.launch

import androidx.compose.runtime.snapshotFlow

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.core.Animatable

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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.foundation.layout.offset
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
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.theme.Oled

private val FieldShape = RoundedCornerShape(28.dp)

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
internal fun fieldStyle(): TextStyle = if (LocalSheet.current) HandFieldStyle else TextStyle(fontFamily = GlassFont, fontSize = 18.sp, lineHeight = 22.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))

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

/** Час появи й зникнення вікон і розмиття — як у Black Glass. */
const val OPEN_MS = 180
const val CLOSE_MS = 280

/** Вікна: з'являються й ховаються трохи повільніше за дрібні елементи — плавно, без ривка. */
private const val DIALOG_OPEN_MS = 320
private const val DIALOG_CLOSE_MS = 240
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
    val scope = rememberCoroutineScope()
    // 0 → 1: вікно з'являється (проявляється, трохи підіймається й «доростає»), разом плавно наростає розмиття фону.
    val shown = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        // Закриття жестом «назад» чи дотиком повз вікно — спершу плавно ховаємо, потім прибираємо.
        if (!closing) {
            closing = true
            scope.launch {
                shown.animateTo(0f, tween(DIALOG_CLOSE_MS, easing = FastOutSlowInEasing))
                onDismissRequest()
                closing = false
            }
        }
    }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(DIALOG_OPEN_MS, easing = FastOutSlowInEasing)) }
    // Вікно на весь екран (без системних полів): клавіатура піднімає вміст плавно, разом з анімацією системи.
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        // Рядок стану і у вікні — як у всій програмі (сховано — лише свайпом згори).
        androidx.compose.runtime.LaunchedEffect(window, StatusBar.hidden) { StatusBar.apply(window) }
        // Вікно відкривається плавно — висока частота на час анімації; далі — на час дотиків (Modifier.boostFrameRate).
        androidx.compose.runtime.LaunchedEffect(window) { FrameRate.boost(window) }
        // Фон за вікном — розмитий самою системою (Android 12+), як у Black Glass; сила розмиття йде разом з анімацією,
        // тож воно не «вмикається» різко.
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LaunchedEffect(window) {
                var last = -1
                snapshotFlow { (DIALOG_BLUR_PX * shown.value).toInt() }.collect { r ->
                    if (r != last && window.decorView.isAttachedToWindow) {
                        last = r
                        // Вікно могло вже зникнути (закрили кнопкою) — тоді просто пропускаємо.
                        runCatching { window.attributes = window.attributes.also { it.blurBehindRadius = r } }
                    }
                }
            }
        }
        // Тло на весь екран: дотик повз вікно — закрити; саме вікно дотики «забирає» собі.
        Box(
            Modifier
                .fillMaxSize()
                .boostFrameRate(window)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { dismiss() }
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
        Box(
            Modifier
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .graphicsLayer {
                val k = shown.value
                alpha = k
                val sc = 0.94f + 0.06f * k
                scaleX = sc
                scaleY = sc
                translationY = (1f - k) * 24.dp.toPx()
            },
        ) {
            content()
        }
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
    /** Вікно сталої висоти (Налаштування з довідкою): не міняє розміру, коли міняється вміст. */
    tall: Boolean = false,
    content: @Composable () -> Unit,
) {
    SmoothDialog(onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .then(if (tall) Modifier.fillMaxHeight(0.82f) else Modifier)
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
            Box(Modifier.weight(1f, fill = tall)) {
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
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
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
            visualTransformation = visualTransformation,
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
fun Notice(text: String, modifier: Modifier = Modifier, action: String? = null, danger: Boolean = false, onAction: () -> Unit = {}) {
    val glass = AppStyle.glass
    val font = if (glass) GlassFont else HandFont
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NOTICE_HEIGHT)
            // Віконце без рамки: графітова пляма з розмитими краями — рамка щоденника під ним лишається видно.
            .drawBehind {
                val soft = NOTICE_SOFT.toPx()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = if (glass) 0xFA141518.toInt() else 0xFA1B1A17.toInt()
                        maskFilter = android.graphics.BlurMaskFilter(soft, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    val r = soft * 1.5f
                    // Розмитий край виходить трохи назовні, а всередині — суцільний графіт (текст під ним не просвічує).
                    canvas.nativeCanvas.drawRoundRect(-soft * 0.3f, -soft * 0.3f, size.width + soft * 0.3f, size.height + soft * 0.3f, r, r, paint)
                    paint.maskFilter = null
                    canvas.nativeCanvas.drawRoundRect(soft * 0.5f, soft * 0.5f, size.width - soft * 0.5f, size.height - soft * 0.5f, r, r, paint)
                } else {
                    drawRoundRect(if (glass) Color(0xF2141518) else Color(0xF21B1A17), cornerRadius = androidx.compose.ui.geometry.CornerRadius(soft))
                }
            }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            color = if (danger) Oled.Danger else Oled.TextPrimary,
            fontFamily = font,
            fontSize = if (glass) 16.sp else 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                action,
                color = Oled.TextPrimary,
                fontFamily = font,
                fontSize = if (glass) 16.sp else 19.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Де зараз щоденник на екрані (у координатах вікна): сповіщення з'являються ВСЕРЕДИНІ його верхнього краю, не
 * виходячи за рамку. null — щоденника немає (полиця): тоді звичне місце вгорі.
 */
object NoticeArea {
    var rect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
}

private val NOTICE_HEIGHT = 56.dp
private val NOTICE_SOFT = 10.dp

/** Місце сповіщень — одне на всю програму: угорі, під рядком стану. */
fun Modifier.noticePlace(): Modifier = statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)

/** Сповіщення, що з'являється й тане («Збережено», «Видалено» + «Повернути», «Немає місця»…). */
@Composable
fun Banner(text: String?, action: String? = null, danger: Boolean = false, onAction: () -> Unit = {}) {
    var shown by remember { mutableStateOf("") }
    var shownAction by remember { mutableStateOf<String?>(null) }
    if (text != null) {
        shown = text
        shownAction = action
    }
    val area = NoticeArea.rect
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = text != null,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { -it / 4 },
            exit = fadeOut(tween(CLOSE_MS)) + slideOutVertically(tween(CLOSE_MS)) { -it / 4 },
            modifier = if (area == null) Modifier.noticePlace()
            else with(density) {
                // Усередині верхнього краю щоденника, з відступом від рамки.
                val inset = 6.dp.toPx()
                Modifier
                    .align(Alignment.TopStart)
                    .offset { androidx.compose.ui.unit.IntOffset((area.left + inset).toInt(), (area.top + inset).toInt()) }
                    .width((area.width - 2 * inset).toDp())
            },
        ) {
            Notice(shown, action = shownAction, danger = danger, onAction = onAction)
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

/**
 * Для [androidx.compose.foundation.ScrollState] (звичайна прокрутка). [active] — 0…1, наскільки краї тануть через
 * сам жест: так край не обрізається різко, коли вміст гойдається, хоч у спокої він чіткий.
 */
fun Modifier.fadingEdges(state: androidx.compose.foundation.ScrollState, active: () -> Float = { 0f }, edge: Dp = EdgeFade): Modifier = fadeMask(
    top = { maxOf(minOf(state.value.toFloat(), edge.toPx()), edge.toPx() * active()) },
    bottom = {
        val max = state.maxValue
        val left = if (max == Int.MAX_VALUE) 0f else minOf((max - state.value).toFloat(), edge.toPx())
        maxOf(left, edge.toPx() * active())
    },
)

/** Для списку. */
@Composable
fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.LazyListState, edge: Dp = EdgeFade): Modifier {
    val active = edgeActivity(state.isScrollInProgress)
    return fadeMask(
        top = { maxOf(if (state.firstVisibleItemIndex > 0) edge.toPx() else minOf(state.firstVisibleItemScrollOffset.toFloat(), edge.toPx()), edge.toPx() * active()) },
        bottom = {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val left = when {
                last == null -> 0f
                last.index < info.totalItemsCount - 1 -> edge.toPx()
                else -> minOf((last.offset + last.size - info.viewportEndOffset + info.afterContentPadding).toFloat().coerceAtLeast(0f), edge.toPx())
            }
            maxOf(left, edge.toPx() * active())
        },
    )
}

/** Для сітки. */
@Composable
fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.grid.LazyGridState, edge: Dp = EdgeFade): Modifier {
    val active = edgeActivity(state.isScrollInProgress)
    return fadeMask(
        top = { maxOf(if (state.firstVisibleItemIndex > 0) edge.toPx() else minOf(state.firstVisibleItemScrollOffset.toFloat(), edge.toPx()), edge.toPx() * active()) },
        bottom = {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val left = when {
                last == null -> 0f
                last.index < info.totalItemsCount - 1 -> edge.toPx()
                else -> minOf((last.offset.y + last.size.height - info.viewportEndOffset + info.afterContentPadding).toFloat().coerceAtLeast(0f), edge.toPx())
            }
            maxOf(left, edge.toPx() * active())
        },
    )
}

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
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = text.length + 3, alpha = if (selected) 0.9f else 0.35f, ink = if (selected) SelectedInk else SheetInk)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Місце під ✓ є завжди (невибрана — просто невидима), тож кнопка не міняє розмір і нічого не смикається.
        GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary.copy(alpha = if (selected) 1f else 0f), size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Короткий підпис під значком/зразком: до двох рядків, переноси лише між словами. Якщо найдовше слово не вміщається
 * в ширину (наприклад, «Завантажити»), шрифт трохи зменшується — слово не рветься посередині.
 */
@Composable
fun FitLabel(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val base = androidx.compose.material3.LocalTextStyle.current.copy(
        lineBreak = androidx.compose.ui.text.style.LineBreak.Heading,
        hyphens = androidx.compose.ui.text.style.Hyphens.None,
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val maxPx = constraints.maxWidth.toFloat()
        val longest = remember(text) { text.split(' ', '\n').maxByOrNull { it.length }.orEmpty() }
        val size = remember(text, maxPx, fontSize, base) {
            var s = fontSize.value
            if (constraints.hasBoundedWidth) {
                while (s > minFontSize.value) {
                    val w = measurer.measure(longest, base.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width
                    if (w <= maxPx) break
                    s -= 0.5f
                }
            }
            s.sp
        }
        val lh = if (lineHeight == androidx.compose.ui.unit.TextUnit.Unspecified) androidx.compose.ui.unit.TextUnit.Unspecified else (lineHeight.value * size.value / fontSize.value).sp
        Text(
            text,
            color = color,
            fontSize = size,
            lineHeight = lh,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            style = base,
        )
    }
}

/**
 * Ряд однакових за шириною кнопок: кожна — як найширша з них (але всі разом не ширші за ряд), по центру.
 * Перемикання чи зміна напису не змінює їхніх розмірів.
 */
@Composable
fun EqualWidthRow(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val n = measurables.size.coerceAtLeast(1)
        val widest = measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val limit = if (constraints.hasBoundedWidth) (constraints.maxWidth - gapPx * (n - 1)) / n else widest
        val w = minOf(widest, limit).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(w)) }
        val h = placeables.maxOfOrNull { it.height } ?: 0
        val total = w * n + gapPx * (n - 1)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else total
        layout(width, h) {
            var x = (width - total) / 2
            placeables.forEach { p ->
                p.placeRelative(x, (h - p.height) / 2)
                x += w + gapPx
            }
        }
    }
}

/*
 * Підказки — за одним правилом:
 *  - [WindowHint] — пояснення до всього вікна чи розділу: сірим, після всіх варіантів (унизу вмісту);
 *  - [FieldHint] — пояснення до одного поля: дрібніше, одразу під ним;
 *  - що вписати в порожнє поле — сірим усередині поля (placeholder);
 *  - на панелі інструментів (малювання) — один рядок угорі панелі.
 */

/** Підказка до всього вікна чи розділу — сірим, унизу вмісту. */
@Composable
fun WindowHint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Oled.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp))
}

/** Підказка до одного поля — дрібніше, одразу під ним. */
@Composable
fun FieldHint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Oled.TextSecondary, fontSize = 13.sp, lineHeight = 17.sp, modifier = modifier.padding(horizontal = 4.dp))
}
