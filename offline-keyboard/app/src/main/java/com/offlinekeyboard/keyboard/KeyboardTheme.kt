package com.offlinekeyboard.keyboard


/**
 * The two styles shared by all the user's apps (THEMES.md): black glass lit from above, and white pencil on
 * black paper. Since 1.2 they are the only themes; the old system/light/dark/OLED settings open as GLASS.
 */
enum class ThemeMode {
    GLASS, SHEET, TYPEWRITER, MILK_TYPEWRITER, SHEET_LIGHT, GLASS_LIGHT;

    /** Either glass (dark or milky). */
    val glass: Boolean get() = this == GLASS || this == GLASS_LIGHT

    /** Either typewriter: they share the machine, the paper strip and the sounds. */
    val typewriter: Boolean get() = this == TYPEWRITER || this == MILK_TYPEWRITER

    /** Either sheet (pencil on paper). */
    val sheet: Boolean get() = this == SHEET || this == SHEET_LIGHT

    /** A light tone of its style. */
    val light: Boolean get() = this == MILK_TYPEWRITER || this == SHEET_LIGHT || this == GLASS_LIGHT
}

/**
 * How surfaces (keys, buttons, popups) are drawn: flat colour, black glass, pencil on paper, or the round
 * worn metal-rimmed keys of an old typewriter.
 */
enum class SurfaceStyle { FLAT, GLASS, SHEET, TYPEWRITER }

/** Key vibration: light tick, normal click, or a firm click (for thick cases and gloves). */
enum class VibrationStrength { LIGHT, MEDIUM, STRONG }

/** All colours of the keyboard window; the views never read colour resources directly. */
data class KeyboardTheme(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val function: Int,
    val pressed: Int,
    val accent: Int,
    val text: Int,
    val onAccent: Int,
    val hint: Int,
    val popup: Int,
    val style: SurfaceStyle = SurfaceStyle.FLAT,
    /** The main finishing action (Enter) in the glass and sheet styles; 0 elsewhere (the accent is used). */
    val confirm: Int = 0,
    /** Recording (the voice chip) in the glass and sheet styles. */
    val listening: Int = 0,
    /** Built-in font for every label (Exo 2 for glass, Caveat for sheet), or 0 for the system font. */
    val fontRes: Int = 0,
    /** Handwriting reads smaller: its labels are drawn this much larger. */
    val textScale: Float = 1f,
    /** How bright frames, rims and the light of glass are (0.4–1); see [withBrightness]. */
    val brightness: Float = 1f,
    /** The milky typewriter: cream keys in bronze, a light machine, parchment in the strip. */
    val milk: Boolean = false,
    /**
     * Glass over the app, blurred by the system behind the keyboard (Android 12+, where the phone allows it):
     * the background is see-through (see [overBlur]) and the keys let a little of it through.
     */
    val seeThrough: Boolean = false,
) {
    /**
     * This glass theme laid over the blurred app: the background keeps its colour but lets the blur through
     * (dark glass darkens it, milky glass whitens it). Other styles are returned as they are.
     */
    fun overBlur(): KeyboardTheme {
        if (style != SurfaceStyle.GLASS || seeThrough) return this
        val alpha = if (dark) 0x80 else 0x94
        return copy(background = (background and 0x00FFFFFF) or (alpha shl 24), seeThrough = true)
    }

    /**
     * The same theme dimmed to [value]: the painters scale frames, rims and light by it, labels and icons
     * dim only a quarter as much, so the keys stay easy to read at the lowest setting.
     */
    fun withBrightness(value: Float): KeyboardTheme {
        fun fade(color: Int, keep: Float) = (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * keep).toInt() shl 24)
        val labels = 0.75f + 0.25f * value
        return copy(brightness = value, text = fade(text, labels), hint = fade(hint, labels), confirm = fade(confirm, labels))
    }

    /** Glass and sheet mark active keys with a brighter rim, not a colour (THEMES.md, section 2). */
    val styled: Boolean get() = style != SurfaceStyle.FLAT

    companion object {
        fun resolve(mode: ThemeMode, brightness: Float = DEFAULT_BRIGHTNESS): KeyboardTheme = when (mode) {
            ThemeMode.GLASS -> glass()
            ThemeMode.SHEET -> sheet()
            ThemeMode.TYPEWRITER -> typewriter()
            ThemeMode.MILK_TYPEWRITER -> milkTypewriter()
            ThemeMode.SHEET_LIGHT -> sheetLight()
            ThemeMode.GLASS_LIGHT -> glassLight()
        }.withBrightness(brightness)

        /**
         * A keyboard is forty frames side by side, far denser than a launcher card: at full strength it reads
         * brighter than the other apps, so it starts at 70 % (the pencil then matches the launcher's 0.6).
         */
        const val DEFAULT_BRIGHTNESS = 0.7f

        /**
         * «Скло · Темна»: true black, keys of black glass lit from above with a bright top rim, Exo 2. No
         * colour accent: the grey [ACCENT] marks the cursor; Enter is written in [CONFIRM] blue.
         */
        fun glass() = KeyboardTheme(
            dark = true,
            background = BLACK,
            key = 0xFF0D0D0D.toInt(),
            function = 0xFF0D0D0D.toInt(),
            pressed = 0xFF2A2A2A.toInt(),
            accent = ACCENT,
            text = TEXT,
            onAccent = TEXT,
            hint = HINT,
            popup = 0xFF101010.toInt(),
            style = SurfaceStyle.GLASS,
            confirm = CONFIRM,
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.exo2_regular,
        )

        /**
         * «Скло · Світла»: milky frosted glass: white glass keys lit from above with a bright top rim and a soft
         * shadow, on pale glass that whitens the blurred app behind (where the phone blurs). Graphite letters,
         * Exo 2, Enter in blue.
         */
        fun glassLight() = KeyboardTheme(
            dark = false,
            background = GLASS_MILK,
            key = 0xFFF8F9FB.toInt(),
            function = 0xFFEFF1F4.toInt(),
            pressed = 0xFFD3D8DF.toInt(),
            accent = 0xFF5F6670.toInt(),
            text = GLASS_INK,
            onAccent = 0xFFFFFFFF.toInt(),
            hint = 0xFF6B717A.toInt(),
            popup = 0xFFFAFBFC.toInt(),
            style = SurfaceStyle.GLASS,
            confirm = 0xFF2B63C6.toInt(),
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.exo2_regular,
        )

        /** The milky glass's page and letters. */
        const val GLASS_MILK = 0xFFE6E9ED.toInt()
        const val GLASS_INK = 0xFF1D2127.toInt()

        /**
         * «Аркуш · Темна»: white pencil on near-black graphite paper, as a notebook's black page (fibres, soft
         * folds); every key a piece of that paper cut out by hand, with its pencil outline drawn twice, Caveat.
         */
        fun sheet() = KeyboardTheme(
            dark = true,
            background = PaperTexture.GRAPHITE_EDGE,
            key = 0xFF1E1D19.toInt(),
            function = 0xFF1E1D19.toInt(),
            pressed = 0xFF26241F.toInt(),
            accent = ACCENT,
            text = INK,
            onAccent = INK,
            hint = HINT,
            popup = 0xFF1E1D19.toInt(),
            style = SurfaceStyle.SHEET,
            confirm = CONFIRM,
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.caveat_semibold,
            textScale = 1.22f,
        )

        /**
         * «Black Typewriter»: an old typewriter. Round keys of one size on stems, a nickel rim (rusty here and
         * there) around a glossy black cap, ivory letters in Cousine (a Courier-like typewriter face with
         * Cyrillic), each a little off.
         */
        fun typewriter() = KeyboardTheme(
            dark = true,
            background = BLACK,
            key = 0xFF15130F.toInt(),
            function = 0xFF15130F.toInt(),
            pressed = 0xFF2A2620.toInt(),
            accent = BRASS,
            text = IVORY,
            onAccent = IVORY,
            hint = 0xFF9A907C.toInt(),
            popup = 0xFF12100D.toInt(),
            style = SurfaceStyle.TYPEWRITER,
            confirm = CONFIRM,
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.cousine_bold,
        )

        /**
         * «Аркуш · Світла»: graphite pencil on old parchment (the milky typewriter's paper); keys are cream pieces of
         * paper cut out by hand, Caveat.
         */
        fun sheetLight() = KeyboardTheme(
            dark = false,
            background = PaperTexture.PARCHMENT_EDGE,
            key = 0xFFF0E2C4.toInt(),
            function = 0xFFF0E2C4.toInt(),
            pressed = 0xFFE4D6BA.toInt(),
            accent = 0xFF6B5A44.toInt(),
            text = SHEET_INK,
            onAccent = 0xFFF8F1E2.toInt(),
            hint = 0xFF7D6E58.toInt(),
            popup = 0xFFF4EBD6.toInt(),
            style = SurfaceStyle.SHEET,
            confirm = 0xFF2F5B8C.toInt(),
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.caveat_semibold,
            textScale = 1.22f,
            milk = true,
        )

        /** The graphite of the light sheet's pencil. */
        const val SHEET_INK = 0xFF3A3128.toInt()

        /**
         * «Milky Typewriter»: the same machine in light colours: milk-white enamel caps in bronze rims on a
         * light coffee body, brown letters, and an old map's parchment in the strip.
         */
        fun milkTypewriter() = KeyboardTheme(
            dark = false,
            background = 0xFFE9DCC4.toInt(),
            key = 0xFFF7F0E2.toInt(),
            function = 0xFFF0E5D0.toInt(),
            pressed = 0xFFDCCBAE.toInt(),
            accent = 0xFF9A6B35.toInt(),
            text = MILK_INK,
            onAccent = 0xFFFBF6EC.toInt(),
            hint = 0xFF8C7354.toInt(),
            popup = 0xFFF3EAD9.toInt(),
            style = SurfaceStyle.TYPEWRITER,
            confirm = 0xFF7A4A1C.toInt(),
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.cousine_bold,
            milk = true,
        )

        /** The brown of the milky typewriter's letters. */
        const val MILK_INK = 0xFF4A3220.toInt()

        // Shared tokens of THEMES.md, section 2.
        private const val BLACK = 0xFF000000.toInt()
        private const val TEXT = 0xFFE6E6E6.toInt()
        private const val INK = 0xFFEDEDED.toInt()
        private const val HINT = 0xFF8C8C8C.toInt()
        private const val ACCENT = 0xFFBDBDBD.toInt()
        const val CONFIRM = 0xFF8AB4F8.toInt()
        const val DANGER = 0xFFEF8A80.toInt()
        private const val LISTENING = 0xFFB0244F.toInt()
        const val IVORY = 0xFFE9DFC6.toInt()
        private const val BRASS = 0xFFC8B48A.toInt()
    }
}
