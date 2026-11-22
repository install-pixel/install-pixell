package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.offlinekeyboard.R

/**
 * Two fixed rows above the keys, so nothing shifts while typing:
 *
 * 1. message / live text / word suggestions · computer↔mobile · settings (close while recording)
 * 2. input mode: [Aa Text] [translate Укр→Pol] [clipboard: translate selection/clipboard] [mic Voice]
 *
 * Every element has a fixed size; only texts and colours change.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context) : LinearLayout(context) {

    interface ToolbarListener {
        fun onModeText()
        /** Turns translation on, or swaps the direction when it is already on. */
        fun onModeTranslate()
        /** Translate the selection in place, or paste the clipboard translated. */
        fun onTranslateClipboard()
        /** The clipboard chip: opens the panel of recently copied texts. */
        fun onClipboardChip()
        fun onToolbarVoice()
        fun onToolbarVoiceCancel()
        fun onToolbarMode()
        /** Bluetooth button: start or stop sending keys to the connected computer. */
        fun onToolbarBluetooth()
        /** Bluetooth button held: on to the next of the chosen devices (and the phone itself), in turn. */
        fun onToolbarBluetoothSwitch() {}
        /** Puts the original text back in place of the last translation. */
        fun onUndoTranslation()
        fun onToolbarSettings()
        fun onSuggestionPicked(word: String)
        fun onSuggestionLongPress(word: String)
        /**
         * A word of the text shown in the strip was touched: the cursor goes to its end ([select]: the word
         * is selected). [start] and [end] are where the word is, counted in characters from the cursor
         * (negative before it).
         */
        fun onEchoWord(start: Int, end: Int, select: Boolean) {}
        /** Sound button: key sounds on or off. */
        fun onToolbarSound() {}
        /** Sound button held: the finger chose [volume] (0.1–1); [done] when it is let go. */
        fun onSoundVolume(volume: Float, done: Boolean) {}
        /** Remote button: the touchpad and remote keys in place of the keys, or back. */
        fun onToolbarRemote() {}
    }

    data class ToolbarState(
        val computerMode: Boolean,
        val translateActive: Boolean,
        val translateLabel: String,
        val translateOffered: Boolean,
        val voiceOffered: Boolean,
        val recording: Boolean,
        val bluetooth: BluetoothButton = BluetoothButton.HIDDEN,
        /** null: the style has no sounds (no button); otherwise whether key sounds are on. */
        val soundOn: Boolean? = null,
        val soundVolume: Float = 0.6f,
        /** The remote can be opened: sending to a connected computer. */
        val remoteOffered: Boolean = false,
        /** The keyboard's own settings are open (typing in them): the gear is dim and does nothing. */
        val inSettings: Boolean = false,
    )

    /** HIDDEN: the Bluetooth keyboard is off in settings; IDLE: on, typing goes to the phone; SENDING: to the computer. */
    /** WAITING: turned on, waiting for the computer to connect (ringed, dimmer); SENDING: connected. */
    enum class BluetoothButton { HIDDEN, IDLE, WAITING, SENDING }

    var toolbarListener: ToolbarListener? = null

    val keyboardView = KeyboardView(context)

    /** The keyboard's own stickers, shown in place of the keys. */
    val stickerPanel = StickerPanel(context).apply { visibility = View.GONE }

    val stickersShown: Boolean get() = stickerPanel.visibility == View.VISIBLE

    /** The recently copied texts, in place of the keys (like the stickers). */
    val clipboardPanel = ClipboardPanel(context).apply { visibility = View.GONE }

    val clipboardShown: Boolean get() = clipboardPanel.visibility == View.VISIBLE

    /** The computer remote: touchpad, mouse buttons and a row of keys (next to the keys in landscape). */
    val remotePanel = RemotePanel(context).apply { visibility = View.GONE }

    val remoteShown: Boolean get() = remotePanel.visibility == View.VISIBLE

    /** The keys and, beside them or in their place, the remote. */
    private val keysRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    private var theme = keyboardView.theme

    /** The whole text on one line; the strip shows the end and can be swiped back to the beginning. */
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSingleLine = true
        setHorizontallyScrolling(true)
        gravity = Gravity.CENTER_VERTICAL
        // Room at the end for the soft right edge, so the newest letter is never under it.
        setPadding(dp(8), 0, dp(24), 0)
    }

    /**
     * Touching the text in the strip: a word puts the cursor there (its suggestions then replace it), a long
     * press selects the word; anywhere else shows the buttons, or runs the message's action.
     */
    private val statusGestures = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent) = true
        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
            val action = statusAction
            when {
                action != null -> { statusAction = null; action() }
                !touchWord(e.x, select = false) -> revealButtons()
            }
            return true
        }
        override fun onLongPress(e: android.view.MotionEvent) {
            if (statusAction == null && touchWord(e.x, select = true)) {
                status.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    })

    /** The word of the shown text at [x] (in the strip's text), if the text is the user's own: tells the listener. */
    private fun touchWord(x: Float, select: Boolean): Boolean {
        if (!showingEcho) return false
        val layout = status.layout ?: return false
        val text = status.text.toString()
        val offset = layout.getOffsetForHorizontal(0, x - status.totalPaddingLeft)
        val words = ECHO_WORD.findAll(text).toList()
        val word = words.firstOrNull { offset >= it.range.first && offset <= it.range.last + 1 } ?: return false
        val cursor = if (echoCursor in 0..text.length) echoCursor else text.length
        toolbarListener?.onEchoWord(word.range.first - cursor, word.range.last + 1 - cursor, select)
        return true
    }

    /** Whether the strip shows the user's text (not a message, a translation or suggestions). */
    private var showingEcho = false
    private val statusScroll = object : HorizontalScrollView(context) {
        // The right end always melts into the paper (not only while there is more text that way), so the
        // paper that moves with the text never shows an edge there.
        override fun getRightFadingEdgeStrength(): Float = 1f
    }.apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        // Text that runs past the edge fades out softly instead of being cut off (as in the launcher).
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(18))
        addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** A finger sliding along the user's text in the strip carries the cursor with it. */
    private var draggingCursor = false
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragOffset = -1
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        @SuppressLint("ClickableViewAccessibility")
        status.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    draggingCursor = false
                    dragDownX = event.x
                    dragDownY = event.y
                    dragOffset = -1
                    // On the user's text a slide moves the cursor, not the strip (it scrolls at its ends).
                    if (showingEcho && statusAction == null) statusScroll.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!draggingCursor && showingEcho && statusAction == null &&
                        kotlin.math.abs(event.x - dragDownX) > touchSlop && kotlin.math.abs(event.x - dragDownX) > kotlin.math.abs(event.y - dragDownY)
                    ) {
                        draggingCursor = true
                        statusScroll.requestDisallowInterceptTouchEvent(true)
                        // No tap or long press after a slide.
                        statusGestures.onTouchEvent(android.view.MotionEvent.obtain(event).apply { action = android.view.MotionEvent.ACTION_CANCEL })
                    }
                    if (draggingCursor) {
                        dragCursorTo(event.x)
                        return@setOnTouchListener true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (draggingCursor) {
                    draggingCursor = false
                    return@setOnTouchListener true
                }
            }
            statusGestures.onTouchEvent(event)
        }
    }

    /**
     * Puts the cursor between the characters under [x] (in the strip's text); near either end of the strip
     * the text scrolls on, so the cursor can go anywhere in it.
     */
    private fun dragCursorTo(x: Float) {
        val layout = status.layout ?: return
        val visibleX = x - statusScroll.scrollX
        val edge = dp(28)
        val step = when {
            visibleX > statusScroll.width - edge -> dp(10)
            visibleX < edge -> -dp(10)
            else -> 0
        }
        if (step != 0) statusScroll.scrollBy(step, 0)
        val offset = layout.getOffsetForHorizontal(0, x + (if (step != 0) step else 0) - status.totalPaddingLeft)
            .coerceIn(0, status.text.length)
        if (offset == dragOffset) return
        dragOffset = offset
        val cursor = if (echoCursor in 0..status.text.length) echoCursor else status.text.length
        if (offset == cursor) return
        status.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        toolbarListener?.onEchoWord(offset - cursor, offset - cursor, select = false)
    }
    private val undoButton = button(R.string.toolbar_undo_translation_description) { toolbarListener?.onUndoTranslation() }
        .apply { icon = Icon.UNDO; visibility = View.GONE }
    private val suggestionViews = List(SUGGESTIONS) { suggestionView() }
    private val suggestionsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        suggestionViews.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }
    /**
     * Next-word predictions after a finished word: small words at the right of the strip, so the text
     * itself stays in view on the left.
     */
    private val predictionViews = List(PREDICTIONS) {
        suggestionView().apply {
            // Sized by their word (no auto-size: it needs a fixed width).
            setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
            // As large as the text, but lighter and set apart by a line: words offered, not written.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }
    private val predictionsDivider = View(context)
    private val predictionsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(predictionsDivider, LayoutParams(dp(1), dp(22)).apply { setMargins(dp(2), 0, dp(2), 0) })
        predictionViews.forEach { addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)) }
    }
    private var predictions: List<String> = emptyList()
    private val modeButton = button(R.string.toolbar_mode_description) { toolbarListener?.onToolbarMode() }
    private val bluetoothButton = button(R.string.toolbar_bluetooth_description) { toolbarListener?.onToolbarBluetooth() }
        .apply {
            icon = Icon.BLUETOOTH
            setOnLongClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                if (buttonsShown) revealButtons()
                toolbarListener?.onToolbarBluetoothSwitch()
                true
            }
        }
    private val remoteButton = button(R.string.toolbar_remote_description) { toolbarListener?.onToolbarRemote() }
        .apply { icon = Icon.TOUCHPAD }

    /** Key sounds: a tap turns them on or off; held, the strip becomes a volume bar under the finger. */
    private val soundButton = ToolbarButton(context).apply {
        contentDescription = context.getString(R.string.toolbar_sound_description)
        isClickable = true
    }
    private val volumeBar = VolumeBar(context).apply { visibility = View.GONE }
    private var volumeShown = false
    private var volumeDownX = 0f
    private val startVolume = Runnable {
        volumeShown = true
        // From the volume it is at (0 when the sounds are off at 0), moved by the finger from where it is.
        volumeBar.value = lastState?.soundVolume ?: 0.6f
        volumeBar.anchor(volumeDownX)
        soundButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        removeCallbacks(hideButtonsRunnable)
        refreshCentre()
        fadeIn(volumeBar)
    }

    init {
        @SuppressLint("ClickableViewAccessibility")
        soundButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    volumeShown = false
                    volumeDownX = event.rawX
                    postDelayed(startVolume, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                android.view.MotionEvent.ACTION_MOVE -> if (volumeShown) {
                    val step = volumeBar.setFromScreenX(event.rawX)
                    if (step) toolbarListener?.onSoundVolume(volumeBar.value, done = false)
                    return@setOnTouchListener true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(startVolume)
                    if (volumeShown) {
                        volumeShown = false
                        v.onTouchEvent(android.view.MotionEvent.obtain(event).apply { action = android.view.MotionEvent.ACTION_CANCEL })
                        toolbarListener?.onSoundVolume(volumeBar.value, done = true)
                        refreshCentre()
                        if (buttonsShown) revealButtons()
                        return@setOnTouchListener true
                    }
                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP &&
                        event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()
                    ) {
                        if (buttonsShown) revealButtons()
                        toolbarListener?.onToolbarSound()
                    }
                }
            }
            v.onTouchEvent(event)
            true
        }
    }

    private val settingsButton = button(R.string.toolbar_settings_description) {
        when {
            recording -> toolbarListener?.onToolbarVoiceCancel()
            lastState?.inSettings != true -> toolbarListener?.onToolbarSettings()
        }
    }

    /** Compact toolbar: shows or hides the row of input modes. */
    private val modesButton = button(R.string.toolbar_modes_description) { toggleModes() }
        .apply { icon = Icon.MODES; visibility = View.GONE }

    private val textChip = button(R.string.mode_text_description) { toolbarListener?.onModeText() }
    private val translateChip = button(R.string.toolbar_translate_description) { toolbarListener?.onModeTranslate() }
    private val clipboardChip = button(R.string.clipboard_chip_description) { toolbarListener?.onClipboardChip() }
    private val voiceChip = button(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }

    private var recording = false
    private lateinit var modeRow: LinearLayout

    /** One row above the keys: the input modes appear on demand (and while translating or dictating). */
    var compact = false
        set(value) {
            if (field == value) return
            field = value
            refreshModesRow()
        }
    /**
     * Mobile keyboard: the toolbar buttons (modes, Bluetooth, computer keyboard, settings) stay hidden and
     * only the text strip shows, until the strip is tapped; they hide again a few seconds later.
     */
    var hideButtons = false
        set(value) {
            if (field == value) return
            field = value
            refreshButtons()
        }
    private var buttonsShown = false
    private val hideButtonsRunnable = Runnable { buttonsShown = false; refreshButtons() }

    fun revealButtons() {
        removeCallbacks(hideButtonsRunnable)
        if (!buttonsShown) {
            buttonsShown = true
            refreshButtons()
        }
        postDelayed(hideButtonsRunnable, BUTTONS_SHOWN_MS)
    }

    private fun buttonsVisible(): Boolean {
        val state = lastState
        if (!hideButtons || state == null || state.computerMode || buttonsShown || busy()) return true
        if (compact && modesExpanded) return true
        return state.bluetooth == BluetoothButton.SENDING || state.bluetooth == BluetoothButton.WAITING
    }

    private fun refreshButtons() {
        val visible = buttonsVisible()
        val bluetoothOn = lastState?.bluetooth?.let { it != BluetoothButton.HIDDEN } ?: false
        for ((button, present) in listOf(
            modesButton to compact,
            soundButton to (lastState?.soundOn != null),
            remoteButton to (lastState?.remoteOffered == true),
            bluetoothButton to bluetoothOn,
            modeButton to true,
            settingsButton to true,
        )) {
            val target = when {
                !present -> View.GONE
                visible -> View.VISIBLE
                // Invisible, not gone: the text strip keeps its place and width.
                else -> View.INVISIBLE
            }
            if (target == View.VISIBLE && button.visibility != View.VISIBLE) fadeIn(button)
            button.visibility = target
        }
    }

    private var modesExpanded = false
    /** Folded by hand while translating or dictating (the row opens by itself for those). */
    private var foldedWhileBusy = false
    private var lastState: ToolbarState? = null
    private var message: CharSequence? = null
    private var statusAction: (() -> Unit)? = null
    private var liveText: CharSequence? = null
    private var suggestions: List<String> = emptyList()
    private var echo: CharSequence? = null
    private val clearMessage = Runnable { message = null; statusAction = null; refreshCentre() }

    /**
     * A band above the toolbar where the app shows through and fades into the keyboard's black, instead
     * of a hard line between the app and the keyboard. It is not touchable and does not shrink the app
     * (see KeyboardService.onComputeInsets).
     */
    // On a light keyboard the band is shorter: light paper fading over a dark app reads as a smear.
    // Over the blurred app there is none: the system blurs the whole keyboard window, so a band would show
    // a hard edge of blur; the glass ends in its lit top rim instead.
    val fadeHeight get() = if (theme.seeThrough) 0 else dp(if (theme.dark) FADE_DP else FADE_LIGHT_DP)
    private val fadePaint = Paint()
    private var fadeColor = 0

    override fun onDraw(canvas: Canvas) {
        val color = fadeColor
        // On the typewriter the soft edge fades into the black paper of the strip, not into the keyboard.
        val fadeTo = if (theme.style == SurfaceStyle.TYPEWRITER) PaperTexture.edgeColor(theme.milk) else color
        if (fadeHeight > 0) {
            if (fadePaint.shader == null || fadePaint.color != fadeTo) {
                fadePaint.color = fadeTo
                fadePaint.shader = LinearGradient(
                    0f, 0f, 0f, fadeHeight.toFloat(),
                    intArrayOf(fadeTo and 0x00FFFFFF, fadeTo), null, Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight.toFloat(), fadePaint)
        }
        backgroundPaint.color = color
        canvas.drawRect(0f, fadeHeight.toFloat(), width.toFloat(), height.toFloat(), backgroundPaint)
        if (theme.seeThrough) {
            // The top edge of the glass plate over the app: a line of light, and a soft light below it.
            backgroundPaint.color = if (theme.dark) 0x47FFFFFF else 0xE6FFFFFF.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), resources.displayMetrics.density, backgroundPaint)
            glowPaint.shader ?: run {
                glowPaint.shader = LinearGradient(0f, 0f, 0f, dp(GLASS_GLOW_DP).toFloat(), 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), dp(GLASS_GLOW_DP).toFloat(), glowPaint)
        }
        if (theme.style == SurfaceStyle.SHEET) drawSheetPaper(canvas)
        if (theme.style == SurfaceStyle.TYPEWRITER && ::infoRow.isInitialized && infoRow.height > 0) {
            // The paper runs the whole width, past the keys' sides (landscape: up to the camera cutout).
            paperSheet()?.let { canvas.drawBitmap(it.bitmap, 0f, infoRow.top.toFloat(), null) }
        }
    }

    private val backgroundPaint = Paint()

    private val paperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paperShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperMatrix = android.graphics.Matrix()
    private val paperRect = android.graphics.RectF()

    /**
     * The sketch: the keyboard is a sheet of paper (the owner's photograph: crumpled black paper, or stained
     * parchment) lying on a plain dark or pale ground, a little inside its edges with a soft shadow; the
     * letters are written on it. Until the photograph is loaded, the plain ground.
     */
    private fun drawSheetPaper(canvas: Canvas) {
        val paper = PaperSheets.keyboard(!theme.dark) ?: return
        val inset = dp(3).toFloat()
        paperRect.set(inset, fadeHeight + inset, width - inset, height.toFloat())
        if (paperRect.width() <= 0f || paperRect.height() <= 0f) return
        // A soft shadow under the sheet.
        for (i in 1..4) {
            paperShadow.color = if (theme.dark) 0x30000000 else 0x1E3A2A14
            canvas.drawRect(paperRect.left + i * 0.5f, paperRect.top + i, paperRect.right + i * 0.5f, paperRect.bottom, paperShadow)
        }
        // The photograph scaled to cover the sheet, centred.
        val scale = maxOf(paperRect.width() / paper.width, paperRect.height() / paper.height)
        paperMatrix.setScale(scale, scale)
        paperMatrix.postTranslate(
            paperRect.left + (paperRect.width() - paper.width * scale) / 2,
            paperRect.top + (paperRect.height() - paper.height * scale) / 2,
        )
        if (paperPaint.shader == null || paperShader?.let { it !== paper } != false) {
            paperShader = paper
            paperPaint.shader = android.graphics.BitmapShader(paper, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        }
        paperPaint.shader.setLocalMatrix(paperMatrix)
        // For the letters written on it, which bend with its creases: where it lies in the window.
        getLocationInWindow(paperSpot)
        PaperSheets.place(
            scale,
            paperSpot[0] + paperRect.left + (paperRect.width() - paper.width * scale) / 2,
            paperSpot[1] + paperRect.top + (paperRect.height() - paper.height * scale) / 2,
        )
        canvas.drawRect(paperRect, paperPaint)
    }

    private var paperShader: android.graphics.Bitmap? = null
    private val paperSpot = IntArray(2)
    private val glowPaint = Paint()

    /** The strip (text, suggestions and buttons): on the typewriter it lies on black paper. */
    private lateinit var infoRow: LinearLayout
    private var paper: PaperTexture.Sheet? = null
    /** Whether [paper] is the parchment of the milky typewriter. */
    private var paperMilk = false
    /** Set when the paper changed, so the text is inked again to follow its folds. */
    private var reInk = false

    /** The black paper for the strip at its present size (made on first use and after a size change). */
    private fun paperSheet(): PaperTexture.Sheet? {
        if (theme.style != SurfaceStyle.TYPEWRITER || !::infoRow.isInitialized || width <= 0 || infoRow.height <= 0) return null
        paper?.takeIf { it.bitmap.width == width && it.bitmap.height == infoRow.height && paperMilk == theme.milk }?.let { return it }
        val key = Triple(width, infoRow.height, theme.milk)
        val ready = papers[key]
        if (ready == null) {
            makePapers(width, infoRow.height)
            // Until it is ready (a moment), the plain colour of the paper.
            return null
        }
        paperMilk = theme.milk
        paper = ready
        reInk = true
        post { applyPaperToText(); refreshCentre() }
        return ready
    }

    /** The sheet style's whole page (graphite or parchment) for the keyboard's size, made in the background. */
    @Volatile private var sheetPage: android.graphics.Bitmap? = null
    private var sheetPageKey: Triple<Int, Int, Boolean>? = null
    private var sheetMaking: Triple<Int, Int, Boolean>? = null

    private fun sheetPaper(): android.graphics.Bitmap? {
        val h = height - fadeHeight
        if (width <= 0 || h <= 0) return null
        val key = Triple(width, h, !theme.dark)
        if (sheetPageKey == key) return sheetPage
        if (sheetMaking != key) {
            sheetMaking = key
            val density = resources.displayMetrics.density
            Thread {
                val page = PaperTexture.journal(key.first, key.second, density, light = key.third)
                post {
                    if (sheetMaking == key) {
                        sheetPage = page?.bitmap
                        sheetPageKey = key
                        invalidate()
                    }
                }
            }.apply { name = "sheet-paper"; isDaemon = true }.start()
        }
        // Until it is ready, the paper's plain colour (the background).
        return if (sheetPageKey?.let { it.first == width && it.third == key.third } == true) sheetPage else null
    }

    /** Both papers (black and parchment) for each size, made once in the background. */
    private val papers = java.util.concurrent.ConcurrentHashMap<Triple<Int, Int, Boolean>, PaperTexture.Sheet>()
    private val making = java.util.Collections.synchronizedSet(HashSet<Pair<Int, Int>>())

    /**
     * Makes the paper for a strip [width] × [height] off the main thread, the present style's first and the
     * other typewriter's right after, so switching between them does not wait for a new sheet.
     */
    private fun makePapers(width: Int, height: Int) {
        if (!making.add(width to height)) return
        val density = resources.displayMetrics.density
        val first = theme.milk
        Thread {
            for (milk in listOf(first, !first)) {
                val sheet = PaperTexture.sheet(width, height, density, parchment = milk) ?: continue
                papers[Triple(width, height, milk)] = sheet
                post { invalidate() }
            }
        }.apply { name = "paper"; isDaemon = true }.start()
    }

    // ---------------------------------------------------------------- over the strip (typewriter)

    private val holderPainter = SurfacePainter(resources.displayMetrics.density)
    /** The typebar of the letter key being pressed: when it went down and came up (uptime ms; 0: not). */
    private var strikeDownAt = 0L
    private var strikeUpAt = 0L
    /** How far up it was when the key came up (0..1). */
    private var strikeHeldAt = 0f
    /** Where its key is (x in this view) and where it strikes the paper. */
    private var strikeFromX = 0f
    private var strikeX = 0f
    /** The letter of the key, pressed into the paper as the typebar strikes. */
    private var strikeLetter = ""
    private val impressionPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)

    /**
     * A letter key went down ([down]) or came up. As on the machine, the typebar of that key swings up from
     * the key's side, out from under the paper holder, and strikes the paper where the letter goes the moment
     * the key is down; it rests there while the key is held and falls back as it comes up.
     */
    fun typebar(keyX: Float, down: Boolean, letter: String = "") {
        if (theme.style != SurfaceStyle.TYPEWRITER || !::infoRow.isInitialized) return
        val now = android.os.SystemClock.uptimeMillis()
        if (down) {
            strikeFromX = keysRow.left + keyboardView.left + keyX
            // It strikes where the letter goes in the text: on the strip's text after the cursor; while the strip
            // shows suggestions instead (the first letters of a word) where the letter will stand once the text
            // is back, not over its key (the letter landed at the far right, away from the text). Over a
            // message, which is not the user's text, straight up over its key.
            val onText = showingEcho && statusScroll.visibility == View.VISIBLE && status.layout != null
            strikeX = when {
                onText -> printX()
                message == null && liveText == null -> textEndX()
                else -> strikeFromX
            }
            strikeLetter = letter
            strikeDownAt = now
            strikeUpAt = 0L
        } else if (strikeDownAt > 0 && strikeUpAt == 0L) {
            // A quick tap lets go before the typebar reaches the paper: it still strikes home and leaves the
            // letter for a moment, then falls (it fell halfway before, and the letter was never pressed).
            val home = strikeDownAt + STRIKE_RISE_MS + STRIKE_PRESS_MS
            strikeHeldAt = if (now < home) 1f else strikeRise(now)
            strikeUpAt = maxOf(now, home)
        }
        postInvalidateOnAnimation()
    }

    /** Where the next letter goes on the paper: just after the cursor in the strip (x in this view). */
    private fun printX(): Float {
        val layout = status.layout
        val text = status.text
        val cursor = if (showingEcho && echoCursor in 0..text.length) echoCursor else text.length
        val at = if (layout != null) runCatching { layout.getPrimaryHorizontal(cursor) }.getOrDefault(0f) else 0f
        val advance = status.paint.measureText("0")
        val x = infoRow.left + statusScroll.left + status.left + status.totalPaddingLeft + at + advance / 2 - statusScroll.scrollX
        return x.coerceIn(infoRow.left + statusScroll.left.toFloat(), infoRow.left + statusScroll.right.toFloat())
    }

    /**
     * Where the next letter goes while the strip shows suggestions: after the text before the cursor, as the
     * strip will show it (its last line; at the right edge when it is longer than the strip).
     */
    private fun textEndX(): Float {
        val text = echo?.toString() ?: ""
        val cursor = echoCursor.coerceIn(0, text.length)
        val before = text.substring(0, cursor).substringAfterLast('\n')
        val advance = status.paint.measureText("0")
        val x = infoRow.left + statusScroll.left + status.left + status.totalPaddingLeft + status.paint.measureText(before) + advance / 2
        return x.coerceIn(infoRow.left + statusScroll.left.toFloat(), infoRow.left + statusScroll.right.toFloat() - advance / 2)
    }

    private fun strikeRise(now: Long): Float {
        val t = ((now - strikeDownAt) / STRIKE_RISE_MS.toFloat()).coerceIn(0f, 1f)
        // Fast at first and slowing into the paper.
        return 1f - (1f - t) * (1f - t)
    }

    /** How far up the typebar is now (0 down in the machine, 1 on the paper). */
    private fun strikeLift(now: Long): Float = when {
        strikeDownAt == 0L -> 0f
        strikeUpAt == 0L || now < strikeUpAt -> strikeRise(now)
        else -> (strikeHeldAt * (1f - (now - strikeUpAt) / STRIKE_FALL_MS.toFloat())).coerceAtLeast(0f)
    }

    private val strikeHalo = android.graphics.BlurMaskFilter(2.2f * resources.displayMetrics.density, android.graphics.BlurMaskFilter.Blur.NORMAL)

    /**
     * The paper's still ends, the typebar and the paper holder go over the strip but under the keys: drawn
     * just before the keys, so an enlarged key or a row of choices rising over the strip covers them (they
     * showed through those before).
     */
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child === keysRow) {
            // The machine's dark depth under the holder runs the whole width (beside the keys too, over the
            // remote in landscape), not only over the keys, so the holder's shadow does not stop short.
            if (theme.style == SurfaceStyle.TYPEWRITER) {
                canvas.save()
                canvas.translate(0f, keysRow.top.toFloat())
                holderPainter.drawTypewriterDepth(canvas, width.toFloat(), dp(18).toFloat())
                canvas.restore()
            }
            drawOverStrip(canvas)
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun drawOverStrip(canvas: Canvas) {
        if (theme.style != SurfaceStyle.TYPEWRITER || !::infoRow.isInitialized || infoRow.height <= 0) return
        val top = infoRow.top.toFloat()
        val bottom = infoRow.bottom.toFloat()
        // The browned ends of the parchment lie over the text as well.
        paper?.overlay?.let { canvas.drawBitmap(it, 0f, top, null) }
        val holder = dp(HOLDER_DP).toFloat()
        // On the paper's lower edge; below it a strip of the machine, then the chips or the keys.
        val holderTop = bottom - holder
        // The typebar of the pressed key. Below the holder, in the strip of the machine, its arm swings up
        // from the key's side; the bend is under the holder; above it only the straight end shows, rising
        // right under the letter, and the letter is pressed into the paper whole.
        val now = android.os.SystemClock.uptimeMillis()
        val lift = strikeLift(now)
        if (lift > 0f) {
            val gapBottom = bottom + dp(HOLDER_GAP_DP)
            val bend = holderTop + holder * 0.5f
            val arm = dp(3).toFloat()
            // The arm and its bend stay hidden in the machine, under the solid holder: nothing of the typebar
            // shows below the paper (the light machine of the milky typewriter showed it, as if through).
            // The letter's place in the strip: its baseline and size as the strip writes it.
            impressionPaint.set(status.paint)
            val layout = status.layout
            val baseline = if (layout != null && statusScroll.visibility == View.VISIBLE) {
                infoRow.top + statusScroll.top + status.top + status.totalPaddingTop + layout.getLineBaseline(0).toFloat() + status.translationY
            } else {
                top + infoRow.height * 0.62f
            }
            val metrics = impressionPaint.fontMetrics
            // The straight end: up from under the holder to just below the letter, only when it has swung
            // over to the letter (while the arm is still on its way it stays under the holder).
            val reach = baseline + metrics.descent * 0.6f
            val up = ((lift - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (up > 0f) {
                val tip = bend + (reach - bend) * up
                holderPainter.drawTypebarEnd(canvas, strikeX, bend, tip, arm, impressionPaint.measureText("0") * 0.9f)
            }
            // The letter pressed into the paper: the whole letter, darker than the ink and soft at its edges,
            // as the type hits; it goes as the bar falls and the typed letter takes its place.
            if (lift > 0.7f && strikeLetter.isNotEmpty()) {
                val ink = ((lift - 0.7f) / 0.3f).coerceIn(0f, 1f)
                val letterWidth = impressionPaint.measureText(strikeLetter)
                // Whole on the paper, also over the keys at the very edges.
                val x = (strikeX - letterWidth / 2f).coerceIn(dp(4).toFloat(), maxOf(dp(4).toFloat(), width - letterWidth - dp(4)))
                haloPaint.set(impressionPaint)
                haloPaint.maskFilter = strikeHalo
                haloPaint.color = (theme.text and 0x00FFFFFF) or ((0x70 * ink).toInt() shl 24)
                canvas.drawText(strikeLetter, x, baseline, haloPaint)
                impressionPaint.color = (theme.text and 0x00FFFFFF) or ((0xE0 * ink).toInt() shl 24)
                canvas.drawText(strikeLetter, x, baseline, impressionPaint)
            }
            // Moving (going up or coming down): the next frame. Resting on the paper: nothing to redraw.
            if (strikeUpAt != 0L || now - strikeDownAt < STRIKE_RISE_MS) postInvalidateOnAnimation()
            // Never left up: a key held very long (or let go unseen) brings its typebar down by itself.
            if (strikeUpAt == 0L && now - strikeDownAt > STRIKE_HOLD_MAX_MS) typebar(0f, false)
        } else if (strikeUpAt != 0L) {
            strikeDownAt = 0L
            strikeUpAt = 0L
        }
        canvas.save()
        canvas.translate(0f, holderTop)
        holderPainter.drawPaperHolder(canvas, width.toFloat(), holder)
        canvas.restore()
    }

    /**
     * Under the text the paper moves with it, like a sheet in the carriage: a typed letter always stays on
     * the same spot of the paper, and the letters on a fold follow it. The rest of the strip keeps the paper
     * still; the strip's soft edges blend the two.
     */
    private fun applyPaperToText() {
        val sheet = paper
        status.background = if (theme.style == SurfaceStyle.TYPEWRITER && sheet != null) {
            PaperDrawable(sheet.bitmap, (infoRow.left + statusScroll.left).toFloat())
        } else {
            null
        }
    }

    /** How much the character at [index] of the strip's text sits on a fold of the paper (typewriter). */
    private fun foldOf(index: Int): Float {
        val sheet = paper ?: return 0f
        val advance = status.paint.measureText("0")
        val x = infoRow.left + statusScroll.left + status.totalPaddingLeft + (index + 0.5f) * advance
        return sheet.foldAt(x, infoRow.height / 2f, status.textSize * 0.6f)
    }

    init {
        setWillNotDraw(false)
        orientation = VERTICAL
        // The key preview of the top row is drawn over the toolbar above it.
        clipChildren = false
        clipToPadding = false
        // Android 15 draws the keyboard window behind the navigation bar (back, home, the keyboard switch
        // globe): the keys move up by the bar's height, and the keyboard's background shows behind it.
        // Older Android places the window above the bar and reports no inset here.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            // In landscape the camera cutout is at one side: the keyboard's black reaches under it (the
            // window is laid out into the cutout), only the keys keep clear of it.
            navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            updateBottomSpace()
            insets
        }

        infoRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // A tap where the hidden buttons are (or anywhere on the strip) shows them.
            setOnClickListener { revealButtons() }
            // A smaller plate, so it sits clear of the paper's edge and the holder; still a full-size target
            // with the strip around it (a tap there shows the buttons too).
            addView(modesButton, LayoutParams(dp(MODES_BUTTON_DP), dp(MODES_BUTTON_DP)).apply { setMargins(dp(5), 0, dp(5), 0) })
            addView(statusScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(suggestionsRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(predictionsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(volumeBar, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(undoButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(remoteButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(soundButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(bluetoothButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(modeButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        }
        modeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(3))
            for ((c, weight) in listOf(textChip to 1f, translateChip to 1.35f, clipboardChip to 0.65f, voiceChip to 1f)) {
                addView(c, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        addView(infoRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(modeRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        keysRow.addView(keyboardView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        keysRow.addView(remotePanel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(keysRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        keyboardView.typebarListener = { x, down, letter -> typebar(x, down, letter) }
        remotePanel.tapFeedback = { keyboardView.panelTap() }
        clipboardPanel.tapFeedback = { keyboardView.panelTap() }
        addView(stickerPanel, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        addView(clipboardPanel, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        applyTheme(theme)
        refreshCentre()
    }

    private var navBars = Insets.NONE
    internal var bottomSpaceChangesForTest = 0
    private val layoutWatcher = ViewTreeObserver.OnGlobalLayoutListener { updateBottomSpace() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutWatcher)
    }

    /**
     * Room below the keys: the navigation bar, or the row of keyboard buttons Android draws inside the
     * keyboard window with gesture navigation (hide ∨ and the keyboard switch globe), whichever is taller.
     * That row is taller than the gesture bar's inset, so the inset alone left it over the bottom keys.
     */
    private fun updateBottomSpace() {
        val bottom = maxOf(navBars.bottom, imeButtonsHeight())
        if (paddingTop != fadeHeight || paddingLeft != navBars.left || paddingRight != navBars.right || paddingBottom != bottom) {
            bottomSpaceChangesForTest++
            setPadding(navBars.left, fadeHeight, navBars.right, bottom)
        }
    }

    /** Height of the system's button row in the keyboard window (Android 13+), 0 when there is none. */
    private fun imeButtonsHeight(): Int {
        fun find(view: View): View? {
            if (view === this) return null
            if (view.javaClass.name.endsWith("NavigationBarFrame")) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val frame = find(rootView) ?: return 0
        return if (frame.visibility == View.VISIBLE) frame.height else 0
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        keyboardView.theme = value
        stickerPanel.applyTheme(value)
        clipboardPanel.applyTheme(value)
        remotePanel.applyTheme(value)
        volumeBar.applyTheme(value)
        holderPainter.milk = value.milk
        holderPainter.brightness = value.brightness
        if (::infoRow.isInitialized) {
            val gap = if (value.style == SurfaceStyle.TYPEWRITER) dp(HOLDER_GAP_DP) else 0
            (infoRow.layoutParams as? LayoutParams)?.let { if (it.bottomMargin != gap) { it.bottomMargin = gap; infoRow.layoutParams = it } }
        }
        fadeColor = value.background
        fadePaint.shader = null
        // The sketch's paper pieces get their grain once it is made (in the background, the first time).
        if (value.style == SurfaceStyle.SHEET) {
            PaperSheets.prepare(context, !value.dark) { post { invalidateTree(this) } }
        }
        // The band's height follows the tone (not while the view is still being built: insets come later).
        if (isAttachedToWindow) updateBottomSpace()
        applyPaperToText()
        reInk = true
        invalidate()
        invalidate()
        Icons.style = value.style
        val face = value.typeface(context)
        (suggestionViews + predictionViews).forEach {
            it.setTextColor(value.text)
            it.typeface = face
        }
        status.typeface = face
        for (b in listOf(textChip, translateChip, clipboardChip, voiceChip)) b.applyStyle(value)
        // A lit button is a smaller plate inside its place, so it sits clear of the paper's edge (on the
        // typewriter smaller still: its rim reached the paper holder's line).
        val litInset = dp(LIT_PLATE_INSET_DP + if (value.style == SurfaceStyle.TYPEWRITER) 3 else 0).toFloat()
        bluetoothButton.plateInset = litInset
        remoteButton.plateInset = litInset
        for (b in listOf(modeButton, settingsButton, bluetoothButton, undoButton, modesButton, soundButton, remoteButton)) {
            b.foreground = value.text
            b.pressedFill = value.pressed
            b.applyStyle(value)
        }
        lastState?.let { updateToolbar(it) }
        refreshCentre()
    }

    fun updateToolbar(state: ToolbarState) {
        lastState = state
        recording = state.recording
        modeButton.icon = if (state.computerMode) Icon.PHONE else Icon.KEYBOARD
        // Ringed at once when turned on (it used to wait for the computer to connect, so a tap seemed to do
        // nothing); dimmer until it is connected.
        val sending = state.bluetooth == BluetoothButton.SENDING || state.bluetooth == BluetoothButton.WAITING
        if (theme.styled) {
            bluetoothButton.fill = if (sending) theme.function else 0
            bluetoothButton.chosen = sending
            bluetoothButton.foreground = theme.text
        } else {
            bluetoothButton.fill = if (sending) theme.accent else 0
            bluetoothButton.foreground = if (sending) theme.onAccent else theme.text
        }
        bluetoothButton.alpha = if (state.bluetooth == BluetoothButton.WAITING) 0.6f else 1f
        soundButton.icon = if (state.soundOn == false) Icon.SPEAKER_OFF else Icon.SPEAKER
        soundButton.alpha = restAlpha(soundButton)
        val remoteOpen = remoteShown
        if (theme.styled) {
            remoteButton.fill = if (remoteOpen) theme.function else 0
            remoteButton.chosen = remoteOpen
            remoteButton.foreground = theme.text
        } else {
            remoteButton.fill = if (remoteOpen) theme.accent else 0
            remoteButton.foreground = if (remoteOpen) theme.onAccent else theme.text
        }
        if (!state.remoteOffered && remoteOpen && !remoteForTest) showRemote(false)
        settingsButton.icon = if (state.recording) Icon.CLOSE else Icon.GEAR
        settingsButton.alpha = restAlpha(settingsButton)
        settingsButton.contentDescription = context.getString(
            if (state.recording) R.string.toolbar_voice_cancel_description else R.string.toolbar_settings_description,
        )

        textChip.text = context.getString(R.string.mode_text)
        styleChip(textChip, active = !state.translateActive && !state.recording, enabled = true)
        translateChip.icon = Icon.TRANSLATE
        translateChip.text = state.translateLabel
        styleChip(translateChip, active = state.translateActive, enabled = state.translateOffered)
        clipboardChip.icon = Icon.CLIPBOARD
        styleChip(clipboardChip, active = false, enabled = state.translateOffered)
        voiceChip.icon = if (state.recording) Icon.STOP else Icon.MIC
        voiceChip.text = context.getString(if (state.recording) R.string.mode_voice_stop else R.string.mode_voice)
        styleChip(voiceChip, active = state.recording, enabled = state.voiceOffered)
        // Glass and sheet: the listening microphone is cherry, so it is not mistaken for a red "delete".
        if (theme.styled && state.recording) voiceChip.foreground = theme.listening
        refreshModesRow()
        refreshCentre()
    }

    /** Folds the modes row away again (the keyboard was hidden). */
    fun collapseModes() {
        modesExpanded = false
        removeCallbacks(hideButtonsRunnable)
        buttonsShown = false
        refreshModesRow()
    }

    private fun busy() = lastState?.let { it.translateActive || it.recording } ?: false

    private fun toggleModes() {
        if (buttonsShown) revealButtons()
        if (busy()) foldedWhileBusy = !foldedWhileBusy else modesExpanded = !modesExpanded
        refreshModesRow()
    }

    private fun refreshModesRow() {
        val busy = busy()
        if (!busy) foldedWhileBusy = false
        val open = if (busy) !foldedWhileBusy else modesExpanded
        // Lit while translating or dictating even when folded, so the mode is not forgotten.
        modesButton.fill = if (compact && (open || busy)) theme.function else 0
        val showRow = !compact || open
        if (showRow && modeRow.visibility != View.VISIBLE) fadeIn(modeRow)
        modeRow.visibility = if (showRow) View.VISIBLE else View.GONE
        refreshButtons()
    }

    /** Swaps the keys for the sticker panel (same height, so nothing jumps) or back. */
    fun showStickers(show: Boolean) {
        if (show == stickersShown) return
        if (show) {
            if (remoteShown) showRemote(false)
            stickerPanel.layoutParams = stickerPanel.layoutParams.apply { height = keyboardView.height.coerceAtLeast(dp(120)) }
            keyboardView.visibility = View.GONE
            stickerPanel.visibility = View.VISIBLE
            fadeIn(stickerPanel)
        } else {
            stickerPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            fadeIn(keyboardView)
        }
    }

    /** Swaps the keys for the clipboard panel (same height) or back. */
    fun showClipboard(show: Boolean) {
        if (show == clipboardShown) return
        if (show) {
            if (remoteShown) showRemote(false)
            if (stickersShown) stickerPanel.visibility = View.GONE
            clipboardPanel.layoutParams = clipboardPanel.layoutParams.apply { height = keyboardView.height.coerceAtLeast(dp(120)) }
            keyboardView.visibility = View.GONE
            clipboardPanel.visibility = View.VISIBLE
            fadeIn(clipboardPanel)
        } else {
            clipboardPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            fadeIn(keyboardView)
        }
    }

    /**
     * Opens the remote or closes it. Upright it takes the place of the keys (same height); in landscape the
     * keys keep about 60% of the width and the remote sits beside them.
     */
    /** Keeps the remote open without a connected computer (tests). */
    internal var remoteForTest = false

    fun showRemote(show: Boolean) {
        if (show == remoteShown) return
        val keysParams = keyboardView.layoutParams as LayoutParams
        val remoteParams = remotePanel.layoutParams as LayoutParams
        if (show) {
            if (stickersShown) showStickers(false)
            if (clipboardShown) showClipboard(false)
            val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            remotePanel.besideKeyboard = landscape
            if (landscape) {
                keysParams.weight = KEYS_BESIDE_REMOTE
                remoteParams.weight = 1f - KEYS_BESIDE_REMOTE
                remoteParams.height = LayoutParams.MATCH_PARENT
            } else {
                remoteParams.weight = 1f
                remoteParams.height = keyboardView.height.coerceAtLeast(dp(160))
                keyboardView.visibility = View.GONE
            }
            remotePanel.visibility = View.VISIBLE
            fadeIn(remotePanel)
        } else {
            remotePanel.visibility = View.GONE
            keysParams.weight = 1f
            if (keyboardView.visibility != View.VISIBLE) {
                keyboardView.visibility = View.VISIBLE
                fadeIn(keyboardView)
            }
        }
        keyboardView.layoutParams = keysParams
        remotePanel.layoutParams = remoteParams
        lastState?.let { updateToolbar(it) }
    }

    /** How see-through a view is at rest: the gear is dim inside the settings, the speaker while muted. */
    private fun restAlpha(view: View): Float {
        val state = lastState
        return when {
            view === settingsButton && state?.inSettings == true && !state.recording -> 0.35f
            view === soundButton && state?.soundOn == false -> 0.6f
            else -> 1f
        }
    }

    /** Lets a panel that just took the place of another come in softly instead of popping up. */
    private fun invalidateTree(view: View) {
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
    }

    private fun fadeIn(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(restAlpha(view)).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    /** A message of the keyboard as shown in the strip: ⓘ, slanted, dimmer, underlined when tappable. */
    private fun note(text: CharSequence): CharSequence {
        val out = android.text.SpannableString("ⓘ $text")
        val all = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 0, out.length, all)
        out.setSpan(android.text.style.ForegroundColorSpan((theme.text and 0x00FFFFFF) or 0xA6000000.toInt()), 0, out.length, all)
        if (statusAction != null) out.setSpan(android.text.style.UnderlineSpan(), 2, out.length, all)
        return out
    }

    /** Text not yet confirmed (dictation, translation): brass or bronze on the typewriters, else blue. */
    private fun liveColor(): Int = when {
        theme.style == SurfaceStyle.TYPEWRITER -> theme.accent
        theme.confirm != 0 -> theme.confirm
        else -> theme.accent
    }

    /** Short, non-blocking message. [onTap] makes it tappable (e.g. "restore original"). */
    fun showStatus(text: CharSequence, durationMs: Long = STATUS_MILLIS, onTap: (() -> Unit)? = null) {
        removeCallbacks(clearMessage)
        message = text
        statusAction = onTap
        refreshCentre()
        postDelayed(clearMessage, durationMs)
    }

    fun clearStatus() {
        removeCallbacks(clearMessage)
        clearMessage.run()
    }

    /** Text shown while dictating or translating (source text in auto mode); null to clear. */
    fun showLive(text: CharSequence?) {
        liveText = text
        refreshCentre()
    }

    /**
     * The end of the text the user is writing (or what was sent over Bluetooth), shown when there is
     * nothing else to show, so the strip always says where the text is.
     */
    fun showEcho(text: CharSequence?, cursor: Int = text?.length ?: 0) {
        if (text?.toString() == echo?.toString() && cursor == echoCursor) return
        echo = text
        echoCursor = cursor
        refreshCentre()
    }

    /** Where the cursor is in [echo]: the text after it is shown dimmer. */
    private var echoCursor = 0
    /** The cursor position the strip shows now (-1: not the user's text). */
    private var shownCursor = -1

    /** Shows the "restore original" button while the last translation can still be undone. */
    fun showUndo(visible: Boolean) {
        undoButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Next-word predictions shown beside the text (empty hides them). */
    fun showPredictions(words: List<String>) {
        if (words == predictions) return
        predictions = words
        refreshCentre()
    }

    fun showSuggestions(words: List<String>) {
        if (words == suggestions) return
        suggestions = words
        refreshCentre()
    }

    private fun refreshCentre() {
        val centreText = message ?: liveText
        val showSuggestions = centreText == null && suggestions.isNotEmpty()
        val isEcho = centreText == null && !showSuggestions
        val text = if (isEcho) echo ?: "" else centreText ?: ""
        showingEcho = isEcho && echo != null
        val cursorAt = if (isEcho) echoCursor else -1
        if (text.toString() != status.text.toString() || cursorAt != shownCursor || reInk) {
            shownCursor = cursorAt
            reInk = false
            val typewriter = theme.style == SurfaceStyle.TYPEWRITER
            val grew = text.length > status.text.length && text.toString().startsWith(status.text.toString())
            status.text = when {
                // The keyboard speaking (a hint, a question, a missing model): not the user's text, so it
                // reads differently, a note in a dimmer slanted hand with ⓘ; underlined when it can be tapped.
                message != null -> note(text)
                // Dictation or a translation still on its way: the accent colour until it is confirmed.
                centreText != null -> android.text.SpannableString(text).apply {
                    setSpan(android.text.style.ForegroundColorSpan(liveColor()), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    val inked = if (typewriter) TypewriterInk.apply(text, status.textSize, if (paper != null) ::foldOf else null) else text
                    if (isEcho && echoCursor in 0 until text.length) dimAfter(inked, echoCursor, theme.text) else inked
                }
            }
            // A new letter on the typewriter: the strip gives the small jolt of a typebar hitting the paper.
            if (typewriter && grew) {
                status.animate().cancel()
                status.translationY = -1.2f * resources.displayMetrics.density
                status.animate().translationY(0f).setDuration(90).start()
            }
            // The cursor in view (the newest text when it is at the end); the rest is a swipe away.
            if (!draggingCursor) statusScroll.post {
                // Runs a moment later: by then dictation or a translation may have changed the text, so the
                // cursor is checked against the text shown now.
                val layout = status.layout
                val cursor = echoCursor
                val x = if (showingEcho && layout != null && cursor in 0 until layout.text.length) {
                    runCatching { status.totalPaddingLeft + layout.getPrimaryHorizontal(cursor) - statusScroll.width * 0.75f }
                        .getOrDefault(status.width.toFloat()).toInt()
                } else {
                    status.width
                }
                statusScroll.smoothScrollTo(x.coerceAtLeast(0), 0)
            }
        }
        status.setTextColor(theme.text)
        predictionsDivider.setBackgroundColor((theme.text and 0x00FFFFFF) or 0x40000000)
        val volume = volumeShown
        volumeBar.visibility = if (volume) View.VISIBLE else View.GONE
        statusScroll.visibility = if (showSuggestions || volume) View.GONE else View.VISIBLE
        suggestionsRow.visibility = if (showSuggestions && !volume) View.VISIBLE else View.GONE
        val showPredictions = isEcho && predictions.isNotEmpty() && !volume
        predictionsRow.visibility = if (showPredictions) View.VISIBLE else View.GONE
        predictionViews.forEachIndexed { i, v ->
            val word = predictions.getOrNull(i)
            v.text = word ?: ""
            v.visibility = if (word != null) View.VISIBLE else View.GONE
            v.alpha = 0.6f
        }
        suggestionViews.forEachIndexed { i, v ->
            val word = suggestions.getOrNull(i)
            v.text = word ?: ""
            v.isEnabled = word != null
        }
    }

    private fun styleChip(view: ToolbarButton, active: Boolean, enabled: Boolean) {
        // Glass and sheet mark the active mode with a brighter rim, not a colour.
        view.chosen = active && theme.styled
        view.fill = if (active && !theme.styled) theme.accent else theme.function
        view.pressedFill = theme.pressed
        // The others are written dimmer, so the chosen mode stands out at a glance.
        view.foreground = when {
            active && !theme.styled -> theme.onAccent
            active -> theme.text
            else -> androidx.core.graphics.ColorUtils.setAlphaComponent(theme.text, INACTIVE_CHIP_ALPHA)
        }
        view.bold = active
        view.alpha = if (enabled) 1f else 0.4f
    }

    private fun button(descriptionRes: Int, onClick: () -> Unit) = ToolbarButton(context).apply {
        contentDescription = context.getString(descriptionRes)
        setOnClickListener {
            if (buttonsShown) revealButtons()
            onClick()
        }
    }

    private fun suggestionView() = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        // Long words get a smaller font instead of being cut.
        setAutoSizeTextTypeUniformWithConfiguration(11, 16, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(dp(2), 0, dp(2), 0)
        isFocusable = false
        setOnClickListener { (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionPicked(w) } }
        setOnLongClickListener {
            (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionLongPress(w) }
            true
        }
        selectableBackground()
    }

    private fun View.selectableBackground() {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutWatcher)
        super.onDetachedFromWindow()
        removeCallbacks(clearMessage)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val ECHO_WORD = Regex(ECHO_WORD_PATTERN)
        const val FADE_DP = 14
        const val FADE_LIGHT_DP = 6
        const val GLASS_GLOW_DP = 24
        /** The paper holder under the strip's paper. */
        const val HOLDER_DP = 5
        /** The modes button's plate in the strip. */
        const val MODES_BUTTON_DP = 34
        /** How far in a lit button's plate sits (Bluetooth, remote). */
        const val LIT_PLATE_INSET_DP = 6
        /** The machine showing between the paper holder and what is below it. */
        const val HOLDER_GAP_DP = 8
        /** A typebar's way up to the paper, and back down. */
        const val STRIKE_RISE_MS = 70L
        const val STRIKE_FALL_MS = 110L
        /** How long a quick tap's letter rests pressed into the paper before the typebar falls. */
        const val STRIKE_PRESS_MS = 45L
        const val STRIKE_HOLD_MAX_MS = 3000L
        /** The keys' share of the width while the remote is beside them (landscape). */
        const val KEYS_BESIDE_REMOTE = 0.6f
        const val BUTTONS_SHOWN_MS = 6000L
        const val PREDICTIONS = 3
        const val INACTIVE_CHIP_ALPHA = 0x70
        const val STATUS_MILLIS = 3500L
        const val SUGGESTIONS = 3
    }
}

/** [text] with what follows [cursor] dimmer: the part of the message after the cursor. */
private fun dimAfter(text: CharSequence, cursor: Int, color: Int): CharSequence {
    val out = android.text.SpannableString(text)
    out.setSpan(android.text.style.ForegroundColorSpan((color and 0x00FFFFFF) or 0x80000000.toInt()), cursor, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return out
}

/** A word in the strip's text, the same for the strip and the service: letters, digits, apostrophes, hyphens. */
internal const val ECHO_WORD_PATTERN = "[\\p{L}\\p{N}'ʼ’-]+"

/** The strip's paper under the text: [bitmap] repeated sideways, shifted by [offsetX] to line up with the paper around it. */
private class PaperDrawable(bitmap: android.graphics.Bitmap, offsetX: Float) : android.graphics.drawable.Drawable() {
    private val paint = Paint().apply {
        shader = android.graphics.BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-offsetX, 0f) })
        }
    }

    override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/**
 * The key sound volume while the sound button is held: a row of ten bars, lit up to the chosen one, with the
 * percentage. The finger moves along the strip (it stays on the button; the bar follows its position).
 */
private class VolumeBar(context: Context) : View(context) {
    /** 0–1 in steps of 0.1 (0: the sounds off). */
    var value = 0.6f
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * density; textAlign = Paint.Align.RIGHT }
    private var lit = 0xFFFFFFFF.toInt()
    private var dim = 0x44FFFFFF
    private val location = IntArray(2)
    private val bar = android.graphics.RectF()

    fun applyTheme(theme: KeyboardTheme) {
        lit = theme.text
        dim = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.text, 0x40)
        text.color = theme.text
        invalidate()
    }

    private val barsWidth get() = width - LABEL_DP * density - 2 * PAD_DP * density

    private var anchorX = 0f
    private var anchorValue = 0f

    /** The finger is at [rawX] now: moving it from here changes the volume from what it is (no jump). */
    fun anchor(rawX: Float) {
        anchorX = rawX
        anchorValue = value
    }

    /** Moves the volume by the finger's way from where it was put down; true when the step changed. */
    fun setFromScreenX(rawX: Float): Boolean {
        val stepWidth = (barsWidth / STEPS).coerceAtLeast(1f)
        val step = (kotlin.math.round(anchorValue * STEPS).toInt() + ((rawX - anchorX) / stepWidth).toInt()).coerceIn(0, STEPS)
        val v = step / STEPS.toFloat()
        if (kotlin.math.abs(v - value) < 0.01f) return false
        value = v
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val pad = PAD_DP * density
        val gap = 3 * density
        val each = (barsWidth - gap * (STEPS - 1)) / STEPS
        val chosen = kotlin.math.round(value * STEPS).toInt()
        for (i in 0 until STEPS) {
            val left = pad + i * (each + gap)
            // Rising bars, like a volume scale.
            val h = height * (0.25f + 0.45f * (i + 1) / STEPS)
            bar.set(left, (height - h) / 2f, left + each, (height + h) / 2f)
            paint.color = if (i < chosen) lit else dim
            canvas.drawRoundRect(bar, 2 * density, 2 * density, paint)
        }
        canvas.drawText(if (chosen == 0) "0" else "${chosen * 10}%", width - pad, height / 2f + text.textSize / 3f, text)
    }

    private companion object {
        const val STEPS = 10
        const val PAD_DP = 10
        const val LABEL_DP = 48
    }
}
