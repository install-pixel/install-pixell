package com.offlinekeyboard.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.KeyboardTheme
import com.offlinekeyboard.keyboard.Pencil
import com.offlinekeyboard.keyboard.ThemeMode
import kotlin.math.sin

/**
 * The settings screens in the two styles shared by all the user's apps (THEMES.md), drawn like the launcher's
 * settings: «Скло» (black glass lit from above, Exo 2) and «Аркуш» (white pencil on black paper, Caveat ×1.22).
 * Components read [LocalLook] and draw themselves accordingly.
 */
enum class Look { GLASS, SHEET, TYPEWRITER }

val LocalLook = staticCompositionLocalOf { Look.GLASS }

/** The colours of the settings for one keyboard style: the page, texts, ink and lines. */
internal data class Palette(
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val confirm: Color,
    val danger: Color,
    val ink: Color,
    val page: Color,
    val divider: Color,
    /** A light page (dark texts and system buttons). */
    val light: Boolean = false,
    /** The milky typewriter's bronze and cream plates. */
    val milk: Boolean = false,
    /** Milky glass: white glass plates on a pale page, cool greys instead of the typewriter's bronze. */
    val frost: Boolean = false,
)

/** Tokens of THEMES.md, section 2, and the typewriters' own: what [SettingsTheme] puts in use. */
internal object Tone {
    /** Black glass and pencil. */
    val Dark = Palette(
        text = Color(0xFFE6E6E6), secondary = Color(0xFF8C8C8C), accent = Color(0xFFBDBDBD),
        confirm = Color(KeyboardTheme.CONFIRM), danger = Color(KeyboardTheme.DANGER), ink = Color(0xFFEDEDED),
        page = Color.Black, divider = Color(0x1FFFFFFF),
    )
    /** The black typewriter: ivory letters on graphite, brass for what is chosen. */
    val Typewriter = Palette(
        text = Color(KeyboardTheme.IVORY), secondary = Color(0xFF9A907C), accent = Color(0xFFC8B48A),
        confirm = Color(KeyboardTheme.CONFIRM), danger = Color(KeyboardTheme.DANGER), ink = Color(KeyboardTheme.IVORY),
        page = Color(0xFF0E0D0B), divider = Color(0x24E9DFC6),
    )
    /** The milky typewriter: brown letters on light coffee, bronze. */
    val Milk = Palette(
        text = Color(KeyboardTheme.MILK_INK), secondary = Color(0xFF8C7354), accent = Color(0xFF9A6B35),
        confirm = Color(0xFF7A4A1C), danger = Color(0xFFB03A2E), ink = Color(KeyboardTheme.MILK_INK),
        page = Color(0xFFE9DCC4), divider = Color(0x334A3220), light = true, milk = true,
    )

    /** The dark sheet: white pencil on graphite paper. */
    val SheetDark = Palette(
        // Ink, not stickers: the card's creases show through the words a little.
        text = Color(0xE6EDEDED), secondary = Color(0xFF8F8C86), accent = Color(0xFFBDBDBD),
        confirm = Color(KeyboardTheme.CONFIRM), danger = Color(KeyboardTheme.DANGER), ink = Color(0xFFEDEDED),
        page = Color(0xFF0D0D0C), divider = Color(0x1FFFFFFF),
    )
    /** The light sheet: graphite pencil on parchment. */
    val SheetLight = Palette(
        text = Color(0xE63A3128), secondary = Color(0xFF7D6E58), accent = Color(0xFF6B5A44),
        confirm = Color(0xFF2F5B8C), danger = Color(0xFFB03A2E), ink = Color(KeyboardTheme.SHEET_INK),
        page = Color(0xFFCBC1AE), divider = Color(0x333A3128), light = true,
    )

    /** Milky glass: graphite letters on pale glass, blue for the main action. */
    val GlassLight = Palette(
        text = Color(KeyboardTheme.GLASS_INK), secondary = Color(0xFF6B717A), accent = Color(0xFF5F6670),
        confirm = Color(0xFF2B63C6), danger = Color(0xFFC0392B), ink = Color(KeyboardTheme.GLASS_INK),
        page = Color(KeyboardTheme.GLASS_MILK), divider = Color(0x261D2127), light = true, frost = true,
    )

    var palette by androidx.compose.runtime.mutableStateOf(Dark)

    val TextPrimary get() = palette.text
    val TextSecondary get() = palette.secondary
    val Accent get() = palette.accent
    val Confirm get() = palette.confirm
    val Danger get() = palette.danger
    val Ink get() = palette.ink
    val Page get() = palette.page
    val Divider get() = palette.divider
    val light get() = palette.light
    val frost get() = palette.frost
}

/** What a button does, for its colour: the one main finishing action, an irreversible one, or anything else. */
enum class ButtonTone { NEUTRAL, CONFIRM, DANGER }

private val GlassFont = FontFamily(
    Font(R.font.exo2_light, FontWeight.Light),
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_medium, FontWeight.Medium),
    Font(R.font.exo2_semibold, FontWeight.SemiBold),
    Font(R.font.exo2_semibold, FontWeight.Bold),
)

private val HandFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_semibold, FontWeight.Medium),
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_semibold, FontWeight.Bold),
)

private val TypewriterFont = FontFamily(
    Font(R.font.cousine_regular, FontWeight.Normal),
    Font(R.font.cousine_regular, FontWeight.Medium),
    Font(R.font.cousine_bold, FontWeight.SemiBold),
    Font(R.font.cousine_bold, FontWeight.Bold),
)

/** Handwriting reads smaller: all text of the sheet style is this much larger. */
private const val HAND_SCALE = 1.22f

internal fun lookOf(mode: ThemeMode?): Look = when (mode) {
    ThemeMode.SHEET, ThemeMode.SHEET_LIGHT -> Look.SHEET
    ThemeMode.TYPEWRITER, ThemeMode.MILK_TYPEWRITER -> Look.TYPEWRITER
    else -> Look.GLASS
}

/** The settings theme for [mode]: black tokens and the style's font for all text. */
@Composable
internal fun SettingsTheme(mode: ThemeMode?, content: @Composable () -> Unit) {
    val look = lookOf(mode)
    val palette = when (mode) {
        ThemeMode.MILK_TYPEWRITER -> Tone.Milk
        ThemeMode.TYPEWRITER -> Tone.Typewriter
        ThemeMode.SHEET_LIGHT -> Tone.SheetLight
        ThemeMode.SHEET -> Tone.SheetDark
        ThemeMode.GLASS_LIGHT -> Tone.GlassLight
        else -> Tone.Dark
    }
    if (Tone.palette != palette) Tone.palette = palette
    // The sketch's cards are scraps of the owner's paper photographs: load them (once) for this tone.
    if (look == Look.SHEET) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(palette.light) { PaperState.prepare(context, palette.light) }
    }
    // Dark system buttons on the light page.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = palette.light
        }
    }
    val colors = if (palette.frost) androidx.compose.material3.lightColorScheme(
        primary = palette.accent, onPrimary = Color.White,
        primaryContainer = Color(0xFFF4F6F8), onPrimaryContainer = palette.text,
        secondaryContainer = Color(0xFFEEF1F4), onSecondaryContainer = palette.text,
        background = palette.page, surface = palette.page,
        surfaceVariant = Color(0xFFF2F4F6), surfaceContainer = Color(0xFFF2F4F6),
        surfaceContainerLow = Color(0xFFECEFF2), surfaceContainerHigh = Color(0xFFF6F7F9),
        surfaceContainerHighest = Color(0xFFFAFBFC),
        onBackground = palette.text, onSurface = palette.text, onSurfaceVariant = palette.secondary,
        outline = Color(0x661D2127), outlineVariant = Color(0x331D2127),
        error = palette.danger,
    ) else if (palette.light) androidx.compose.material3.lightColorScheme(
        primary = palette.accent, onPrimary = palette.page,
        primaryContainer = Color(0xFFF3EADA), onPrimaryContainer = palette.text,
        secondaryContainer = Color(0xFFEFE4D0), onSecondaryContainer = palette.text,
        background = palette.page, surface = palette.page,
        surfaceVariant = Color(0xFFF1E7D5), surfaceContainer = Color(0xFFF1E7D5),
        surfaceContainerLow = Color(0xFFEDE2CE), surfaceContainerHigh = Color(0xFFF4EBDC),
        surfaceContainerHighest = Color(0xFFF7F0E2),
        onBackground = palette.text, onSurface = palette.text, onSurfaceVariant = palette.secondary,
        outline = Color(0x664A3220), outlineVariant = Color(0x334A3220),
        error = palette.danger,
    ) else darkColorScheme(
        primary = palette.accent, onPrimary = Color.Black,
        primaryContainer = Color(0xFF1B1B1B), onPrimaryContainer = Tone.TextPrimary,
        secondaryContainer = Color(0xFF141414), onSecondaryContainer = Tone.TextPrimary,
        background = palette.page, surface = palette.page,
        surfaceVariant = Color(0xFF0D0D0D), surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF080808), surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1B1B1B),
        onBackground = palette.text, onSurface = palette.text, onSurfaceVariant = palette.secondary,
        outline = Color(0x38FFFFFF), outlineVariant = Color(0x24FFFFFF),
        error = Tone.Danger,
    )
    val font = TextStyle(
        fontFamily = when (look) {
            Look.SHEET -> HandFont
            Look.TYPEWRITER -> TypewriterFont
            Look.GLASS -> GlassFont
        },
    )
    val density = LocalDensity.current
    // Fields and small surfaces are rounded 16 dp (THEMES.md: no rectangular frames).
    val shapes = MaterialTheme.shapes.copy(
        extraSmall = RoundedCornerShape(16.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
    )
    // Sizes as in the launcher's settings: headings 17–19 sp, text 15–17 sp, hints 13 sp; no bold.
    val type = MaterialTheme.typography.withFont(font).let {
        it.copy(
            titleMedium = it.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Normal),
            titleSmall = it.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
            bodyLarge = it.bodyLarge.copy(fontSize = 17.sp),
            bodyMedium = it.bodyMedium.copy(fontSize = 15.sp),
            bodySmall = it.bodySmall.copy(fontSize = 13.sp),
        )
    }
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes) {
        CompositionLocalProvider(
            LocalLook provides look,
            // The sketch is written on paper: a tap leaves no grey highlight on it (a chosen option is ringed in pencil).
            androidx.compose.foundation.LocalIndication provides (if (look == Look.SHEET) NoHighlight else androidx.compose.foundation.LocalIndication.current),
            LocalTextStyle provides LocalTextStyle.current.merge(font),
            LocalDensity provides if (look == Look.SHEET) Density(density.density, density.fontScale * HAND_SCALE) else density,
            content = content,
        )
    }
}

/** No highlight at all under a tap (the sketch). */
private object NoHighlight : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
            override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() = drawContent()
        }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}

private fun Typography.withFont(font: TextStyle) = Typography(
    displayLarge = displayLarge.merge(font), displayMedium = displayMedium.merge(font), displaySmall = displaySmall.merge(font),
    headlineLarge = headlineLarge.merge(font), headlineMedium = headlineMedium.merge(font), headlineSmall = headlineSmall.merge(font),
    titleLarge = titleLarge.merge(font), titleMedium = titleMedium.merge(font), titleSmall = titleSmall.merge(font),
    bodyLarge = bodyLarge.merge(font), bodyMedium = bodyMedium.merge(font), bodySmall = bodySmall.merge(font),
    labelLarge = labelLarge.merge(font), labelMedium = labelMedium.merge(font), labelSmall = labelSmall.merge(font),
)

// ------------------------------------------------------------------ surfaces

private val GlassSurface = Brush.verticalGradient(0f to Color(0xFF1B1B1B), 0.45f to Color(0xFF0D0D0D), 1f to Color(0xFF080808))
// The rim colours of THEMES.md (white at 50/14/7/15 %, chosen 70/25/20 %) flattened onto the black page as
// opaque greys, and at most three stops: the Android 14/15 emulator renderer dies on a four-stop gradient
// while the page scrolls (CI bisect), so the dim middle and the faint bottom light are merged into one stop.
/** A chosen option or a small button: glass lit from inside (the launcher's knob, as a two-stop gradient). */
private val GlassLit = Brush.verticalGradient(0f to Color(0xFF2E2E2E), 1f to Color(0xFF121212))
private val GlassRim = Brush.verticalGradient(0f to Color(0xFF808080), 0.25f to Color(0xFF242424), 1f to Color(0xFF1A1A1A))
private val GlassRimChosen = Brush.verticalGradient(0f to Color(0xFFB3B3B3), 0.3f to Color(0xFF404040), 1f to Color(0xFF333333))
// Milky glass (the keyboard's light glass): white glass, a rim white on top and cool grey below, chosen blue.
private val MilkSurface = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.45f to Color(0xFFF5F6F8), 1f to Color(0xFFEAEDF0))
private val MilkLit = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 1f to Color(0xFFDDE6F3))
private val MilkRim = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.3f to Color(0xFFD5DAE0), 1f to Color(0xFFAEB5BF))
private val MilkRimChosen = Brush.verticalGradient(0f to Color(0xFF9CC0F5), 0.3f to Color(0xFF6E97D8), 1f to Color(0xFF5B82C2))

/**
 * Glass drawn in one pass behind the content: the 1 dp rim as a filled rounded rectangle with the body
 * (and the top light of large surfaces, [sheen]) filled 1 dp inside it.
 */
private fun Modifier.glassFrame(radius: Dp, chosen: Boolean, sheen: Boolean, lit: Boolean): Modifier = drawWithCache {
    val r = radius.toPx()
    val rim = 1.dp.toPx()
    val inner = CornerRadius(maxOf(0f, r - rim))
    val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * rim, size.height - 2 * rim)
    val light = minOf(size.height / 2, 120.dp.toPx())
    val frost = Tone.frost
    val sheenBrush = Brush.verticalGradient(0f to (if (frost) Color(0x80FFFFFF) else Color(0x14FFFFFF)), 1f to Color.Transparent, startY = 0f, endY = light)
    val shadow = 1.dp.toPx()
    onDrawBehind {
        if (frost) {
            // A soft shadow under the white glass, so it stands off the pale page.
            drawRoundRect(Color(0x14202A36), Offset(0f, shadow), size, CornerRadius(r))
            drawRoundRect(Color(0x14202A36), Offset(0f, 2 * shadow), size, CornerRadius(r))
        }
        drawRoundRect(
            when {
                frost && chosen -> MilkRimChosen
                frost -> MilkRim
                chosen -> GlassRimChosen
                else -> GlassRim
            },
            cornerRadius = CornerRadius(r),
        )
        drawRoundRect(
            when {
                frost && lit -> MilkLit
                frost -> MilkSurface
                lit -> GlassLit
                else -> GlassSurface
            },
            Offset(rim, rim), innerSize, inner,
        )
        if (sheen || frost) drawRoundRect(sheenBrush, Offset(rim, rim), innerSize, inner)
    }
}

/**
 * A card, button or field surface: black glass with a rim bright on top ([chosen]: brighter rim; [lit]: glass
 * lit from inside, for chosen options and small buttons), or a pencil frame drawn twice, different per [seed].
 */
@Composable
internal fun Modifier.lookSurface(
    radius: Dp,
    seed: Int = 0,
    chosen: Boolean = false,
    sheen: Boolean = false,
    ink: Color = Tone.Ink,
    lit: Boolean = false,
    alpha: Float = 0.8f,
): Modifier = when (LocalLook.current) {
    Look.GLASS -> glassFrame(radius, chosen, sheen, lit)
    // One sheet of paper: buttons and boxes are drawn on it in pencil, not laid on it.
    Look.SHEET -> pencilFrame(radius, seed, if (chosen) 1f else alpha, if (chosen) 2.6.dp else 2.2.dp, ink)
    Look.TYPEWRITER -> typewriterFrame(radius, seed, chosen || lit)
}

/**
 * The typewriter style: a plate of the old machine (the keyboard's [com.offlinekeyboard.keyboard.SurfacePainter]):
 * a worn nickel rim with a few rust spots around a dark cap with scratches; chosen is a brass rim.
 */
internal fun Modifier.typewriterFrame(radius: Dp, seed: Int, chosen: Boolean): Modifier = drawWithCache {
    val painter = com.offlinekeyboard.keyboard.SurfacePainter(density).apply {
        brightness = KeyboardTheme.DEFAULT_BRIGHTNESS
        milk = Tone.palette.milk
        // The window's reflection lies on the lacquer over the card's text, not behind it (see below).
        glossOnTop = true
    }
    val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
    val r = radius.toPx()
    onDrawWithContent {
        drawIntoCanvas {
            painter.draw(
                it.nativeCanvas, rect, r, com.offlinekeyboard.keyboard.SurfaceStyle.TYPEWRITER, 0,
                if (chosen) com.offlinekeyboard.keyboard.SurfaceState.SELECTED else com.offlinekeyboard.keyboard.SurfaceState.NORMAL,
                seed = seed, plate = true,
            )
        }
        drawContent()
        drawIntoCanvas { painter.drawPlateGloss(it.nativeCanvas, rect) }
    }
}

/**
 * The sketch style: a scrap of the keyboard's paper (parchment, or the same paper in charcoal) with an uneven
 * hand-cut edge and a soft shadow, as the keys are drawn ([com.offlinekeyboard.keyboard.SurfacePainter]);
 * chosen, its edge gone over in pencil.
 */
internal fun Modifier.sheetFrame(radius: Dp, seed: Int, chosen: Boolean, ink: Color): Modifier = drawWithCache {
    // Drawn again once the paper is made (the plain paper colour until then).
    PaperState.ready
    val painter = com.offlinekeyboard.keyboard.SurfacePainter(density).apply {
        brightness = 1f
        sheetLight = Tone.light
        // Each card from its own part of the sheet.
        sheetOffsetX = ((seed and 0x7FFF) % 97) * 11f * density
        sheetOffsetY = ((seed and 0x7FFF) % 53) * 13f * density
    }
    val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
    val r = radius.toPx()
    val inkArgb = ink.toArgb()
    onDrawBehind {
        drawIntoCanvas {
            painter.draw(
                it.nativeCanvas, rect, r, com.offlinekeyboard.keyboard.SurfaceStyle.SHEET, 0,
                if (chosen) com.offlinekeyboard.keyboard.SurfaceState.SELECTED else com.offlinekeyboard.keyboard.SurfaceState.NORMAL,
                seed = seed, inkColor = inkArgb,
            )
        }
    }
}

/** When the sketch's paper (the key sheet and the settings page) is ready: read to be drawn again then. */
internal object PaperState {
    /** [v] (a [w] × [h] grid) averaged over a (2[r]+1)² square round each point, twice (close to a soft blur). */
    private fun smooth(v: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        var src = v
        repeat(2) {
            val tmp = FloatArray(w * h)
            val out = FloatArray(w * h)
            val k = 2 * r + 1
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -r..r) sum += src[y * w + (x + d).coerceIn(0, w - 1)]
                tmp[y * w + x] = sum / k
            }
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -r..r) sum += tmp[(y + d).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = sum / k
            }
            src = out
        }
        return src
    }

    var ready by androidx.compose.runtime.mutableIntStateOf(0)
    private val pages = HashMap<Boolean, android.graphics.Bitmap>()
    private val making = HashSet<Boolean>()
    private val reliefs = HashMap<String, android.graphics.Bitmap>()
    private val reliefMaking = HashSet<String>()

    /**
     * The relief of the settings page's paper for the [light] tone as a small picture for the notebook's bend
     * (red, green: the slope across and down, 0.5 flat; blue: light 0.5 neutral), the same size as the page
     * photograph divided by [reliefScale]; null until the page is made.
     */
    fun relief(light: Boolean): android.graphics.Bitmap? {
        val smoothness = NotebookBend.current.smooth
        val key = "$light/$smoothness"
        reliefs[key]?.let { return it }
        val page = pages[light] ?: return null
        if (reliefMaking.add(key)) {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            Thread {
                val made = makeRelief(page, smoothness)
                main.post {
                    reliefMaking.remove(key)
                    if (made != null) {
                        reliefs[key] = made.first
                        reliefScale = made.second
                    }
                    ready++
                }
            }.apply { name = "settings relief"; isDaemon = true }.start()
        }
        return null
    }

    /**
     * The page photograph's relief as the small picture [relief] gives: the slope smoothed over [smoothness]
     * points (only the big folds bend the writing; a slope that turns within a stroke stretches and tears it),
     * the light and shade of the creases kept fine (the pencil fades in a crease's shadow).
     */
    private fun makeRelief(p: android.graphics.Bitmap, smoothness: Int): Pair<android.graphics.Bitmap, Float>? = runCatching {
        val r = com.offlinekeyboard.keyboard.PaperSheets.reliefOf(p)
        val gx = smooth(r.gx, r.width, r.height, smoothness)
        val gy = smooth(r.gy, r.width, r.height, smoothness)
        val shade = smooth(r.shade, r.width, r.height, 1)
        // The smoothed slope is gentler: bring the strongest folds back to full strength.
        var top = 1e-6f
        for (i in gx.indices) top = maxOf(top, kotlin.math.abs(gx[i]), kotlin.math.abs(gy[i]))
        val lift = (1.5f / top).coerceIn(1f, 4f)
        val px = IntArray(r.width * r.height) { i ->
            fun c(v: Float, k: Float) = ((v * k + 0.5f).coerceIn(0f, 1f) * 255).toInt()
            (0xFF shl 24) or (c(gx[i] * lift, 1f / 3f) shl 16) or (c(gy[i] * lift, 1f / 3f) shl 8) or c(shade[i], 0.5f)
        }
        android.graphics.Bitmap.createBitmap(px, r.width, r.height, android.graphics.Bitmap.Config.ARGB_8888) to (1f / r.scale)
    }.getOrNull()

    /** How many page-photograph pixels one pixel of [relief] spans. */
    var reliefScale = 1f
        private set

    /** Loads the papers of the cards for the [light] tone; the cards are drawn again when they are there. */
    fun prepare(context: android.content.Context, light: Boolean) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        com.offlinekeyboard.keyboard.PaperSheets.prepare(context.applicationContext, light) { main.post { ready++ } }
    }

    /** The settings page's paper for the [light] tone, or null while it is made (it is asked for again then). */
    fun page(context: android.content.Context, light: Boolean): android.graphics.Bitmap? {
        pages[light]?.let { return it }
        if (making.add(light)) {
            val app = context.applicationContext
            val metrics = app.resources.displayMetrics
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            com.offlinekeyboard.keyboard.PaperSheets.prepare(app, light) { main.post { ready++ } }
            Thread {
                // The owner's photograph of the paper (the keyboard's black paper, or a burnt-edged parchment),
                // turned upright for the tall page.
                val page = runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
                    val photo = android.graphics.BitmapFactory.decodeResource(
                        app.resources,
                        if (light) com.offlinekeyboard.R.drawable.sheet_page_light else com.offlinekeyboard.R.drawable.sheet_keys_dark,
                        options,
                    )
                    if (photo.width > photo.height && metrics.heightPixels > metrics.widthPixels) {
                        val turn = android.graphics.Matrix().apply { postRotate(90f) }
                        android.graphics.Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, turn, true)
                    } else {
                        photo
                    }
                }.getOrNull()
                main.post {
                    making.remove(light)
                    if (page != null) pages[light] = page
                    ready++
                }
            }.apply { name = "settings paper"; isDaemon = true }.start()
        }
        return null
    }
}

/** The sketch style's page: one sheet of paper behind everything, written on like a notebook's page. */
@Composable
internal fun Modifier.paperPage(): Modifier {
    if (LocalLook.current != Look.SHEET) return this
    val context = androidx.compose.ui.platform.LocalContext.current
    val light = Tone.light
    return drawBehind {
        PaperState.ready
        val page = PaperState.page(context, light) ?: return@drawBehind
        // Scaled to cover the page, centred (the paper keeps its proportions).
        val scale = maxOf(size.width / page.width, size.height / page.height)
        val srcW = (size.width / scale).toInt().coerceAtMost(page.width)
        val srcH = (size.height / scale).toInt().coerceAtMost(page.height)
        drawImage(
            page.asImageBitmap(),
            srcOffset = androidx.compose.ui.unit.IntOffset((page.width - srcW) / 2, (page.height - srcH) / 2),
            srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
        )
    }
}

/** The pencil frame of the sheet style (the keyboard's [Pencil] outline as a Compose path). */
internal fun Modifier.pencilFrame(radius: Dp, seed: Int, alpha: Float = 0.8f, width: Dp = 2.2.dp, ink: Color = Tone.Ink): Modifier =
    drawWithCache {
        val w = width.toPx()
        val r = radius.toPx()
        val main = Pencil.tapered(Pencil.loop(size.width, size.height, r, seed, 1.3.dp.toPx()), w).asComposePath()
        val ghost = Pencil.tapered(Pencil.loop(size.width, size.height, r, seed + 101, 1.6.dp.toPx()), w * 0.55f).asComposePath()
        onDrawBehind {
            drawPath(main, ink.copy(alpha = alpha))
            drawPath(ghost, ink.copy(alpha = alpha * 0.35f))
        }
    }

/**
 * A wavy pencil line across the text at [y] from [from] to [to] (a switched-off setting is crossed out; an
 * action is underlined), as the launcher's `pencilStroke`.
 */
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f, ink: Color = Tone.Ink) {
    val wob = 1.1.dp.toPx()
    val steps = 24
    val ax = from
    val ay = y + sin(seed * 1.3f) * wob
    val cx = (from + to) / 2
    val cy = y + sin(seed * 2.1f + 1f) * wob * 2f
    val bx = to
    val by = y + sin(seed * 0.7f + 2f) * wob
    val points = FloatArray((steps + 1) * 2)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val u = 1 - t
        points[i * 2] = u * u * ax + 2 * u * t * cx + t * t * bx
        points[i * 2 + 1] = u * u * ay + 2 * u * t * cy + t * t * by
    }
    drawPath(Pencil.tapered(points, 2.2.dp.toPx()).asComposePath(), ink.copy(alpha = alpha))
}

/** Text colour of a button by what it does (THEMES.md: one blue main action per window, red for the irreversible). */
@Composable
internal fun toneColor(tone: ButtonTone): Color = when (tone) {
    ButtonTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    ButtonTone.CONFIRM -> Tone.Confirm
    ButtonTone.DANGER -> Tone.Danger
}

/** Colours of a text button (dialog actions, a row's "Delete") by what it does. */
@Composable
internal fun toneTextColors(tone: ButtonTone) = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = toneColor(tone))

// ------------------------------------------------------------------ switch, check box

/**
 * The glass switch of the launcher: a 52×30 capsule of black glass with a rim and a glass ball inside. On, the
 * capsule is lit and the bright ball sits right; off, it is dark and the dim ball sits left. The ball slides.
 * (The sheet style has no switch: the setting is written, or crossed out — see SwitchRow.)
 */
@Composable
internal fun LookSwitch(checked: Boolean) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    Box(
        Modifier.size(52.dp, 30.dp).drawWithCache {
            val ball = 24.dp.toPx()
            val pad = (size.height - ball) / 2
            val x = pad + (size.width - 2 * pad - ball) * position
            val capsule = CornerRadius(size.height / 2)
            val light = Tone.light && !Tone.frost
            val frost = Tone.frost
            val body = when {
                frost && checked -> Brush.verticalGradient(listOf(Color(0xFF6E97D8), Color(0xFF3F6FC0)))
                frost -> Brush.verticalGradient(listOf(Color(0xFFDCE0E5), Color(0xFFEEF1F4)))
                light && checked -> Brush.verticalGradient(listOf(Color(0xFFB08A58), Color(0xFF8A6438)))
                light -> Brush.verticalGradient(listOf(Color(0xFFE2D4BA), Color(0xFFF1E7D5)))
                checked -> Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF262626)))
                else -> GlassSurface
            }
            val knob = when {
                frost && checked -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE6ECF5)), startY = pad, endY = pad + ball)
                frost -> Brush.verticalGradient(listOf(Color.White, Color(0xFFD5DAE0)), startY = pad, endY = pad + ball)
                light && checked -> Brush.verticalGradient(listOf(Color(0xFFFFFBF2), Color(0xFFEFE3CC)), startY = pad, endY = pad + ball)
                light -> Brush.verticalGradient(listOf(Color(0xFFF7F0E2), Color(0xFFD8CCB4)), startY = pad, endY = pad + ball)
                checked -> Brush.verticalGradient(listOf(Color.White, Color(0xFFBDBDBD)), startY = pad, endY = pad + ball)
                else -> Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)), startY = pad, endY = pad + ball)
            }
            val rimBrush = when {
                frost -> if (checked) MilkRimChosen else MilkRim
                light -> Brush.verticalGradient(listOf(Color(0xFFF3DDB2), Color(0xFF6E4C26)))
                checked -> GlassRimChosen
                else -> GlassRim
            }
            val ballRim = when {
                frost -> Color(0xFFAEB5BF)
                light -> Color(0xFF9A7A50)
                else -> Color(0xFF555555)
            }
            val rim = 1.dp.toPx()
            val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * rim, size.height - 2 * rim)
            onDrawBehind {
                // Rim as a fill under the body, not a stroke (see glassFrame).
                drawRoundRect(rimBrush, cornerRadius = capsule)
                drawRoundRect(body, Offset(rim, rim), innerSize, CornerRadius(innerSize.height / 2))
                drawCircle(ballRim, ball / 2, Offset(x + ball / 2, pad + ball / 2))
                drawCircle(knob, ball / 2 - rim, Offset(x + ball / 2, pad + ball / 2))
            }
        },
    )
}

/** A glass check box: an empty glass square, or a lit one with a ✓. */
@Composable
internal fun LookMark(checked: Boolean) {
    Box(
        Modifier.size(24.dp).lookSurface(7.dp, chosen = checked, lit = checked).drawWithCache {
            val tick = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.53f)
                lineTo(size.width * 0.43f, size.height * 0.70f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
            }
            onDrawWithContent {
                drawContent()
                if (checked) drawPath(tick, Tone.TextPrimary, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        },
    )
}


/** A keyboard style without its tone: what the style list offers. */
internal enum class StyleFamily { GLASS, SHEET, TYPEWRITER }

internal fun familyOf(mode: ThemeMode): StyleFamily = when {
    mode.sheet -> StyleFamily.SHEET
    mode.typewriter -> StyleFamily.TYPEWRITER
    else -> StyleFamily.GLASS
}

/** The mode of [family] in the [light] or dark tone. */
internal fun modeOf(family: StyleFamily, light: Boolean): ThemeMode = when (family) {
    StyleFamily.GLASS -> if (light) ThemeMode.GLASS_LIGHT else ThemeMode.GLASS
    StyleFamily.SHEET -> if (light) ThemeMode.SHEET_LIGHT else ThemeMode.SHEET
    StyleFamily.TYPEWRITER -> if (light) ThemeMode.MILK_TYPEWRITER else ThemeMode.TYPEWRITER
}

/**
 * The style (glass, sketch, typewriter) and, for styles that have one, the tone (dark or light): two short
 * choices instead of one long list. Changing the style keeps the tone where it can.
 */
@Composable
internal fun StylePicker(s: KeyboardSettings, update: UpdateSettings, title: String) {
    val family = familyOf(s.themeMode)
    val light = s.themeMode.light
    Section(title) {
        for ((f, label, description) in listOf(
            Triple(StyleFamily.GLASS, R.string.theme_glass, R.string.theme_glass_desc),
            Triple(StyleFamily.SHEET, R.string.theme_sheet, R.string.theme_sheet_desc),
            Triple(StyleFamily.TYPEWRITER, R.string.theme_typewriter, R.string.theme_typewriter_desc),
        )) {
            RadioRow(androidx.compose.ui.res.stringResource(label), family == f, androidx.compose.ui.res.stringResource(description)) {
                update { it.withStyle(modeOf(f, light)) }
            }
        }
    }
    Section(androidx.compose.ui.res.stringResource(R.string.tone_title)) {
        RadioRow(androidx.compose.ui.res.stringResource(R.string.tone_dark), !light) { update { it.withStyle(modeOf(family, false)) } }
        RadioRow(androidx.compose.ui.res.stringResource(R.string.tone_light), light) { update { it.withStyle(modeOf(family, true)) } }
    }
    if (family == StyleFamily.GLASS) {
        Section(androidx.compose.ui.res.stringResource(R.string.glass_see_through)) {
            SwitchRow(androidx.compose.ui.res.stringResource(R.string.glass_see_through), s.glassSeeThrough) { v ->
                update { it.copy(glassSeeThrough = v) }
            }
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(R.string.glass_see_through_desc),
                color = Tone.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        // Whether the phone lets the glass lie over the blurred app, as the keyboard last found it.
        val context = androidx.compose.ui.platform.LocalContext.current
        val state = androidx.compose.runtime.remember { com.offlinekeyboard.keyboard.BlurState.load(context).first }
        val text = androidx.compose.ui.res.stringResource(
            when (state) {
                com.offlinekeyboard.keyboard.BlurState.ON -> R.string.blur_state_on
                com.offlinekeyboard.keyboard.BlurState.NOT_ALLOWED -> R.string.blur_state_not_allowed
                com.offlinekeyboard.keyboard.BlurState.WINDOW -> R.string.blur_state_window
                com.offlinekeyboard.keyboard.BlurState.OLD_ANDROID -> R.string.blur_state_old
                com.offlinekeyboard.keyboard.BlurState.UNKNOWN -> R.string.blur_state_unknown
            },
        )
        androidx.compose.material3.Text(
            text,
            color = Tone.TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}
