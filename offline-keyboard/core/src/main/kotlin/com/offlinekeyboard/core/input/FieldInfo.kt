package com.offlinekeyboard.core.input

/**
 * The parts of Android's EditorInfo the keyboard reacts to. Constant values mirror
 * android.text.InputType and android.view.inputmethod.EditorInfo (checked by a test in :app).
 */
data class FieldInfo(val inputType: Int, val imeOptions: Int) {

    private val inputClass get() = inputType and TYPE_MASK_CLASS
    private val variation get() = inputType and TYPE_MASK_VARIATION

    /** TYPE_NULL fields (terminals, games) understand key events only. */
    val isRawKeyField: Boolean get() = inputType == TYPE_NULL

    val isMultiLine: Boolean
        get() = inputClass == TYPE_CLASS_TEXT && inputType and (TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0

    val isPassword: Boolean
        get() = when (inputClass) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }

    /** The app asked the keyboard not to learn from this field (incognito tabs etc.). */
    val isIncognito: Boolean get() = imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    /** Voice input and (later) translation are never offered here. */
    val isSensitive: Boolean get() = isPassword || isIncognito

    /**
     * Whether typed words may go into the user dictionary and get suggestions: ordinary text only,
     * never passwords, incognito, addresses or fields that ask for no suggestions.
     */
    val allowsLearning: Boolean
        get() = inputClass == TYPE_CLASS_TEXT && !isSensitive &&
            inputType and TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0 &&
            variation != TYPE_TEXT_VARIATION_URI &&
            variation != TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            variation != TYPE_TEXT_VARIATION_FILTER

    /**
     * Whether autocorrect may run: any ordinary text field, also incognito ones and those that ask for no
     * suggestions (many chat apps set that flag only to hide other keyboards' suggestion bars); never
     * passwords, addresses or search filters.
     */
    val allowsCorrection: Boolean
        get() = inputClass == TYPE_CLASS_TEXT && !isPassword &&
            variation != TYPE_TEXT_VARIATION_URI &&
            variation != TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            variation != TYPE_TEXT_VARIATION_FILTER

    val isNumeric: Boolean get() = inputClass == TYPE_CLASS_NUMBER || inputClass == TYPE_CLASS_PHONE

    val imeAction: Int get() = imeOptions and IME_MASK_ACTION

    private val noEnterAction: Boolean get() = imeOptions and IME_FLAG_NO_ENTER_ACTION != 0

    private val hasRealAction: Boolean
        get() = !noEnterAction && imeAction != IME_ACTION_UNSPECIFIED && imeAction != IME_ACTION_NONE

    fun resolveEnter(forceNewline: Boolean): EnterResolution = when {
        isRawKeyField -> EnterResolution.KeyEvent
        forceNewline && isMultiLine -> EnterResolution.Newline
        hasRealAction -> EnterResolution.EditorAction(imeAction)
        isMultiLine -> EnterResolution.Newline
        else -> EnterResolution.KeyEvent
    }

    val enterLabel: EnterLabel
        get() = if (!hasRealAction) {
            EnterLabel.NEWLINE
        } else {
            when (imeAction) {
                IME_ACTION_GO -> EnterLabel.GO
                IME_ACTION_SEARCH -> EnterLabel.SEARCH
                IME_ACTION_SEND -> EnterLabel.SEND
                IME_ACTION_NEXT -> EnterLabel.NEXT
                IME_ACTION_PREVIOUS -> EnterLabel.PREVIOUS
                IME_ACTION_DONE -> EnterLabel.DONE
                else -> EnterLabel.NEWLINE
            }
        }

    /** Multi-line text gets a real tab character; elsewhere Tab moves focus like on a PC. */
    fun resolveTab(reverse: Boolean): TabResolution =
        if (!reverse && isMultiLine && !isRawKeyField) TabResolution.InsertTab else TabResolution.KeyEvent

    companion object {
        const val TYPE_NULL = 0
        const val TYPE_MASK_CLASS = 0x0000000f
        const val TYPE_MASK_VARIATION = 0x00000ff0
        const val TYPE_CLASS_TEXT = 0x00000001
        const val TYPE_CLASS_NUMBER = 0x00000002
        const val TYPE_CLASS_PHONE = 0x00000003
        const val TYPE_TEXT_FLAG_CAP_SENTENCES = 0x00004000
        const val TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000
        const val TYPE_TEXT_FLAG_IME_MULTI_LINE = 0x00040000
        const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x00080000
        const val TYPE_TEXT_VARIATION_URI = 0x00000010
        const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
        const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
        const val TYPE_TEXT_VARIATION_FILTER = 0x000000b0
        const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
        const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
        const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
        const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010

        const val IME_MASK_ACTION = 0x000000ff
        const val IME_ACTION_UNSPECIFIED = 0
        const val IME_ACTION_NONE = 1
        const val IME_ACTION_GO = 2
        const val IME_ACTION_SEARCH = 3
        const val IME_ACTION_SEND = 4
        const val IME_ACTION_NEXT = 5
        const val IME_ACTION_DONE = 6
        const val IME_ACTION_PREVIOUS = 7
        const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
        const val IME_FLAG_NO_ENTER_ACTION = 0x40000000
    }
}

sealed interface EnterResolution {
    data object Newline : EnterResolution
    data object KeyEvent : EnterResolution
    data class EditorAction(val actionId: Int) : EnterResolution
}

enum class TabResolution { InsertTab, KeyEvent }

enum class EnterLabel { NEWLINE, GO, SEARCH, SEND, NEXT, PREVIOUS, DONE, TRANSLATE }
