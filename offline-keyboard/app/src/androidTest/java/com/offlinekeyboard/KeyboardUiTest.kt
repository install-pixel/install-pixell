package com.offlinekeyboard

import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.SpecialKey
import com.offlinekeyboard.debug.KeyboardTestActivity
import com.offlinekeyboard.keyboard.Accent
import com.offlinekeyboard.keyboard.KeyboardService
import com.offlinekeyboard.keyboard.KeyboardTheme
import com.offlinekeyboard.keyboard.ToolbarButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Drives the real keyboard on an emulator with real touch events: the keyboard is enabled and selected
 * through the shell, a test activity is opened, and keys are tapped at their on-screen positions.
 * Screenshots go to /data/local/tmp/screens (pulled by CI for a visual check).
 */
@RunWith(AndroidJUnit4::class)
class KeyboardUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private lateinit var scenario: ActivityScenario<KeyboardTestActivity>
    private lateinit var activity: KeyboardTestActivity

    @Before
    fun setUp() {
        val ime = "${instrumentation.targetContext.packageName}/.keyboard.KeyboardService"
        shell("settings put secure show_ime_with_hard_keyboard 1")
        // Right after the emulator boots, Android can take a while to accept the keyboard: ask until it is
        // the current one, so the first test does not run before it.
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (true) {
            shell("ime enable $ime")
            shell("ime set $ime")
            // Android stores the id in full ("pkg/pkg.keyboard.KeyboardService") or short ("pkg/.keyboard...").
            val current = shellOutput("settings get secure default_input_method").trim()
            val full = ime.replace("/.", "/${instrumentation.targetContext.packageName}.")
            if (current == ime || current == full || SystemClock.uptimeMillis() > deadline) break
            Thread.sleep(1_000)
        }
        shell("mkdir -p $SCREENS")
        scenario = ActivityScenario.launch(KeyboardTestActivity::class.java)
        val ref = AtomicReference<KeyboardTestActivity>()
        scenario.onActivity { ref.set(it) }
        activity = ref.get()
        focus(activity.plain)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun typesAndDeletesUkrainianText() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        screenshot("mobile-uk")
        "привіт".forEach { tapText(it.toString()) }
        assertEquals("привіт", text(activity.plain))
        tapSpecial(SpecialKey.BACKSPACE)
        assertEquals("приві", text(activity.plain))
    }

    @Test
    fun shiftCapitalisesOneLetter() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapSpecial(SpecialKey.SHIFT)
        tapText("а")
        tapText("б")
        assertEquals("Аб", text(activity.plain))
    }

    @Test
    fun longPressGivesUkrainianAndRussianLetters() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        longPressText("е", screenshotName = "popup-e")
        longPressText("и")
        // On the mobile layout є has its own key, so е offers э first.
        assertEquals("эы", text(activity.plain))
    }

    @Test
    fun languageKeyCyclesAllLanguages() {
        useMode(KeyboardMode.MOBILE)
        for (language in listOf(Language.PL, Language.EN, Language.UK)) {
            useLanguage(language)
            assertEquals(language, service().languageForTest)
        }
        // The language is shown once, on the keys; the strip above is free for text.
        assertNull(findView { v -> v is TextView && v.isShown && v.text.toString() == "Укр" && v !is ToolbarButton })
    }

    @Test
    fun suggestionsWorkFromTheFirstStart() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        typeWord(activity.plain, "дяк")
        val suggestion = waitForView { v -> v is TextView && v.isShown && v.text.toString() == "дякую" }
        screenshot("suggestions")
        tapView(suggestion)
        assertEquals("дякую ", text(activity.plain))
    }

    @Test
    fun stripAboveTheKeysRepeatsTheText() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapText("а")
        tapText("б")
        tapSpecial(SpecialKey.SPACE)
        waitForView { v -> v is TextView && v.isShown && v.text.toString() == "аб" }
        screenshot("echo")
    }

    @Test
    fun emojiPanelTypesEmoji() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapSpecial(SpecialKey.LAYER_EMOJI)
        screenshot("emoji")
        tapText(com.offlinekeyboard.core.layout.EmojiSet.pages[0].items[0])
        tapSpecial(SpecialKey.EMOJI_NEXT)
        screenshot("emoji-page2")
        tapSpecial(SpecialKey.LAYER_LETTERS)
        tapText("а")
        assertEquals(com.offlinekeyboard.core.layout.EmojiSet.pages[0].items[0] + "а", text(activity.plain))
        // Opened again, the panel starts on the most used page.
        tapSpecial(SpecialKey.LAYER_EMOJI)
        screenshot("emoji-frequent")
        assertTrue(keyCenter { it.special == SpecialKey.EMOJI_NEXT && it.label?.startsWith("🕘") == true } != null)
        tapSpecial(SpecialKey.LAYER_LETTERS)
    }

    @Test
    fun doubleSpacePutsAPeriod() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        typeWord(activity.plain, "та")
        tapSpecial(SpecialKey.SPACE)
        tapSpecial(SpecialKey.SPACE)
        assertEquals("та. ", text(activity.plain))
    }

    @Test
    fun typoIsCorrectedAndBackspaceBringsItBack() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        typeWord(activity.plain, "пртвіт")
        tapSpecial(SpecialKey.SPACE)
        assertEquals("привіт ", text(activity.plain))
        tapSpecial(SpecialKey.BACKSPACE)
        assertEquals("пртвіт", text(activity.plain))
        // Undone once, the word is left alone from then on.
        tapSpecial(SpecialKey.SPACE)
        assertEquals("пртвіт ", text(activity.plain))
    }

    @Test
    fun draggingBackspaceLeftDeletesWords() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        instrumentation.runOnMainSync {
            // Ends with a space, so the strip shows the text rather than word suggestions.
            activity.plain.setText("раз два три ")
            activity.plain.setSelection(activity.plain.length())
        }
        // The keyboard reads the field through the app: wait until it sees the new text.
        waitForView { v -> v is TextView && v !is EditText && v.isShown && v.text.toString() == "раз два три" }
        val start = keyCenter { it.special == SpecialKey.BACKSPACE }!!
        val density = instrumentation.targetContext.resources.displayMetrics.density
        // Past the start (28 dp) and one more step (30 dp): two words.
        drag(start.x, start.y, start.x - 72 * density, start.y)
        assertEquals("раз ", text(activity.plain))
    }

    @Test
    fun periodLongPressGivesTheApostrophe() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapText("п")
        longPressText(".")
        tapText("я")
        assertEquals("п'я", text(activity.plain))
    }

    @Test
    fun stickersAreSentToAppsThatTakeImages() {
        focus(activity.chat)
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapSpecial(SpecialKey.LAYER_EMOJI)
        // The sticker panel is open once its boy/girl buttons show.
        repeat(3) {
            if (findView { v -> v is ToolbarButton && v.isShown && v.text == "👦" } != null) return@repeat
            if (service().inputRootForTest?.stickersShown != true) {
                runCatching { tapSpecial(SpecialKey.STICKERS) }.onFailure { tapSpecial(SpecialKey.LAYER_EMOJI) }
            }
            Thread.sleep(800)
        }
        tapUntilShown({ v -> v is ToolbarButton && v.isShown && v.text == "👦" }) { v -> v is ImageView && v.isShown && v.tag == "boy/02-rainbow" }
        Thread.sleep(400)
        screenshot("stickers-boy")
        // The girl button swaps Lingo for the girl version.
        val sticker = tapUntilShown({ v -> v is ToolbarButton && v.isShown && v.text == "👧" }) { v ->
            v is ImageView && v.isShown && v.tag == "girl/02-thumbs-up"
        }
        Thread.sleep(400)
        screenshot("stickers-girl")
        tapView(sticker)
        val received = waitFor("sticker") { activity.receivedSticker }
        // GIF, the animated format chat apps play.
        assertTrue(received, received.startsWith("image/gif ") && received.contains("girl-02-thumbs-up.gif"))
        assertTrue(received, received.split(' ')[1].toInt() > 1000)
        // ABC goes back to the letters.
        tapView(waitForView { v -> v is ToolbarButton && v.isShown && v.contentDescription == "АБВ" || v is ToolbarButton && v.isShown && v.contentDescription == "ABC" })
        tapText("а")
        assertEquals("а", text(activity.chat))
    }

    @Test
    fun longTextScrollsInTheStrip() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        instrumentation.runOnMainSync {
            activity.plain.setText("це дуже довге повідомлення, яке не поміщається в рядок над клавішами ")
            activity.plain.setSelection(activity.plain.length())
        }
        // While a word is being typed the strip shows suggestions; after a space it shows the message.
        tapText("а")
        tapSpecial(SpecialKey.SPACE)
        waitForView { v -> v is TextView && v.isShown && v.text.toString().startsWith("це дуже довге") }
        Thread.sleep(300)
        screenshot("long-echo")
    }

    @Test
    fun passwordFieldShowsNoSuggestions() {
        focus(activity.password)
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        "дяк".forEach { tapText(it.toString()) }
        Thread.sleep(300)
        assertNull(findView { v -> v is TextView && v.isShown && v.text.toString() == "дякую" })
        // Passwords are never repeated above the keys either.
        assertNull(findView { v -> v is TextView && v.isShown && v.text.toString().contains("дяк") })
    }

    @Test
    fun enterStartsANewLineInMultiLineFields() {
        focus(activity.multiLine)
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        tapText("а")
        tapSpecial(SpecialKey.ENTER)
        tapText("б")
        assertEquals("а\nб", text(activity.multiLine))
    }

    @Test
    fun computerKeyboardHasNoLanguageOrModeKeyAndWinSpaceSwitches() {
        useMode(KeyboardMode.COMPUTER)
        useLanguage(Language.UK)
        screenshot("computer-uk")
        assertNull(keyCenter { it.special == SpecialKey.LANGUAGE })
        assertNull(keyCenter { it.special == SpecialKey.MODE })

        tapSpecial(SpecialKey.META)
        tapSpecial(SpecialKey.SPACE)
        assertEquals(Language.PL, service().languageForTest)

        "abc".forEach { tapText(it.toString()) }
        assertEquals("abc", text(activity.plain))
        tapSpecial(SpecialKey.CTRL)
        tapText("a")
        tapSpecial(SpecialKey.BACKSPACE)
        assertEquals("", text(activity.plain))
    }

    /**
     * With three-button navigation (back, home, recent apps, and on Android 15 the keyboard switch globe),
     * the bar must not cover the bottom row: Android 15 draws the keyboard window behind it.
     */
    @Test
    fun keysStayAboveTheNavigationBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        useMode(KeyboardMode.MOBILE)
        val mode = shellOutput("settings get secure navigation_mode").trim()
        // A second keyboard makes Android show its switch globe and hide button below the keys (as on the
        // phone where they covered Enter and ?123); the test keyboard reopens to pick that up.
        val ours = shellOutput("settings get secure default_input_method").trim()
        val others = shellOutput("ime list -a -s").lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(instrumentation.targetContext.packageName) }
        try {
            others.forEach { shell("ime enable $it") }
            shell("ime set $ours")
            relaunchTestScreen()
            screenshot("nav-default")
            assertKeysClearOfImeButtons()
            shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton")
            waitFor("three-button navigation", timeoutMs = 10_000) {
                shellOutput("settings get secure navigation_mode").trim().takeIf { it == "0" }
            }
            // The new bar is a configuration change: the test screen is recreated and the keyboard closes.
            Thread.sleep(1_500)
            relaunchTestScreen()
            screenshot("nav-three-button")
            val keyboard = service()
            val bottom = AtomicReference<Int>()
            instrumentation.runOnMainSync {
                val view = keyboard.inputRootForTest!!.keyboardView
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                bottom.set(location[1] + view.height)
            }
            val context = instrumentation.targetContext
            val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.height()
            // The bar as the app sees it (some emulator images have hardware keys and show no bar at all).
            val barRef = AtomicReference<Int>()
            instrumentation.runOnMainSync {
                val insets = WindowInsetsCompat.toWindowInsetsCompat(activity.window.decorView.rootWindowInsets)
                barRef.set(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            }
            val bar = barRef.get()
            assertTrue("keys end at ${bottom.get()}, the bar starts at ${screen - bar}", bottom.get() <= screen - bar)
            assertKeysClearOfImeButtons()
        } finally {
            val restore = when (mode) {
                "1" -> "twobutton"
                "2" -> "gestural"
                else -> "threebutton"
            }
            shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$restore")
            others.forEach { shell("ime disable $it") }
            shell("ime set $ours")
        }
    }

    private fun relaunchTestScreen() {
        scenario.close()
        scenario = ActivityScenario.launch(KeyboardTestActivity::class.java)
        val ref = AtomicReference<KeyboardTestActivity>()
        scenario.onActivity { ref.set(it) }
        activity = ref.get()
        focus(activity.plain)
    }

    /**
     * With gesture navigation Android 13+ draws its own row inside the keyboard window (hide ∨ and the
     * keyboard switch globe); it must sit below the keys, not on Enter and ?123.
     */
    private fun assertKeysClearOfImeButtons() {
        val keyboard = service()
        val result = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            val root = keyboard.inputRootForTest!!
            fun find(view: View): View? {
                if (view.javaClass.name.endsWith("NavigationBarFrame")) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            val frame = find(root.rootView)?.takeIf { it.isShown && it.height > 0 }
            if (frame != null) {
                val keys = IntArray(2).also { root.keyboardView.getLocationOnScreen(it) }
                val buttons = IntArray(2).also { frame.getLocationOnScreen(it) }
                val keysBottom = keys[1] + root.keyboardView.height
                if (keysBottom > buttons[1]) result.set("keys end at $keysBottom, the keyboard buttons start at ${buttons[1]}")
            }
        }
        assertNull(result.get())
    }

    @Test
    fun everyThemeRenders() {
        useMode(KeyboardMode.MOBILE)
        useLanguage(Language.UK)
        for (dark in listOf(false, true)) {
            for (accent in Accent.entries) {
                val keyboard = service()
                instrumentation.runOnMainSync { keyboard.applyThemeForTest(KeyboardTheme.create(dark, accent)) }
                instrumentation.waitForIdleSync()
                screenshot("theme-${if (dark) "dark" else "light"}-${accent.name.lowercase()}")
            }
        }
        for (accent in listOf(Accent.GREY, Accent.SKY)) {
            val keyboard = service()
            instrumentation.runOnMainSync { keyboard.applyThemeForTest(KeyboardTheme.oled(accent)) }
            instrumentation.waitForIdleSync()
            screenshot("theme-oled-${accent.name.lowercase()}")
        }
        // Fourteen redraws later the Android 15 emulator sometimes ignores the next taps: settle as at the start.
        focus(activity.plain)
        typeWord(activity.plain, "а")
    }

    @Test
    fun settingsPagesOpen() {
        scenario.close()
        // Phones run the app in Ukrainian: open the settings in every interface language (Android 13+).
        val pkg = instrumentation.targetContext.packageName
        val locales = if (android.os.Build.VERSION.SDK_INT >= 33) listOf("uk-UA", "pl-PL", "en-US") else listOf("")
        for (locale in locales) {
            if (locale.isNotEmpty()) shell("cmd locale set-app-locales $pkg --locales $locale")
            ActivityScenario.launch(com.offlinekeyboard.settings.SettingsActivity::class.java).use { settings ->
                Thread.sleep(1500)
                assertEquals(
                    "settings closed in locale $locale",
                    androidx.lifecycle.Lifecycle.State.RESUMED,
                    settings.state,
                )
                val tag = locale.ifEmpty { "default" }
                screenshot("settings-$tag-1")
                for (page in 2..5) {
                    shell("input swipe 160 560 160 140 400")
                    Thread.sleep(600)
                    screenshot("settings-$tag-$page")
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("cmd locale set-app-locales $pkg --locales en-US")
        scenario = ActivityScenario.launch(KeyboardTestActivity::class.java)
    }

    // ------------------------------------------------------------------ helpers

    private val Key.special: SpecialKey? get() = (action as? KeyAction.Special)?.key

    private fun service(): KeyboardService = waitFor("keyboard service", timeoutMs = 45_000) {
        val ref = AtomicReference<KeyboardService?>()
        instrumentation.runOnMainSync {
            ref.set(KeyboardService.instance?.takeIf { it.inputRootForTest?.isShown == true })
        }
        ref.get()
    }

    /** Focuses [field] and shows the keyboard; asks again if Android ignores the first request. */
    private fun waitForKeysToSettle() {
        var last: Int? = null
        var still = 0
        val end = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < end && still < 3) {
            Thread.sleep(200)
            val y = AtomicReference<Int?>()
            instrumentation.runOnMainSync {
                KeyboardService.instance?.inputRootForTest?.keyboardView?.let { view ->
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    y.set(if (view.isShown) location[1] else null)
                }
            }
            val now = y.get()
            still = if (now != null && now == last) still + 1 else 0
            last = now
        }
        Thread.sleep(200)
    }

    private fun focus(field: EditText) {
        repeat(6) {
            instrumentation.runOnMainSync {
                field.requestFocus()
                field.context.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
            }
            val shown = runCatching { waitFor("keyboard service", timeoutMs = 8_000) { visibleService() } }.isSuccess
            if (shown) {
                clear(field)
                // Android 14 and 15 slide the keyboard in (slowly on the emulator); a touch during the
                // slide is lost. Wait until the keys stop moving.
                waitForKeysToSettle()
                return
            }
            // A tap on the field is what a person would do.
            tapView(field)
        }
        service()
        clear(field)
    }

    private fun visibleService(): KeyboardService? {
        val ref = AtomicReference<KeyboardService?>()
        instrumentation.runOnMainSync {
            ref.set(KeyboardService.instance?.takeIf { it.inputRootForTest?.isShown == true })
        }
        return ref.get()
    }

    private fun clear(field: EditText) {
        instrumentation.runOnMainSync { field.setText("") }
        instrumentation.waitForIdleSync()
    }

    private fun text(field: EditText): String {
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
        val ref = AtomicReference<String>()
        instrumentation.runOnMainSync { ref.set(field.text.toString()) }
        return ref.get()
    }

    private fun useMode(mode: KeyboardMode) {
        repeat(2) {
            if (service().modeForTest == mode) return
            tapView(toolbarButton(R.string.toolbar_mode_description))
        }
        assertEquals(mode, service().modeForTest)
    }

    /** Mobile: the language key; computer: Win+Space (the computer layout has no language key). */
    private fun useLanguage(language: Language) {
        repeat(Language.entries.size) {
            if (service().languageForTest == language) return
            if (service().modeForTest == KeyboardMode.MOBILE) {
                tapSpecial(SpecialKey.LANGUAGE)
            } else {
                tapSpecial(SpecialKey.META)
                tapSpecial(SpecialKey.SPACE)
            }
        }
        assertEquals(language, service().languageForTest)
    }

    private fun toolbarButton(descriptionRes: Int): ToolbarButton {
        val description = instrumentation.targetContext.getString(descriptionRes)
        return waitForView { it is ToolbarButton && it.contentDescription == description } as ToolbarButton
    }

    private fun keyCenter(match: (Key) -> Boolean): android.graphics.PointF? {
        val ref = AtomicReference<android.graphics.PointF?>()
        val keyboard = service()
        instrumentation.runOnMainSync { ref.set(keyboard.inputRootForTest?.keyboardView?.keyCenterOnScreen(match)) }
        return ref.get()
    }

    private fun tapText(text: String) {
        val point = keyCenter { (it.action as? KeyAction.Text)?.text == text } ?: throw AssertionError("no key «$text»")
        touch(point.x, point.y, holdMs = 40)
    }

    /**
     * Types [word] letter by letter, checking the field after each tap: the API 34 emulator sometimes
     * drops a tap right after a test starts, and a lost letter is tapped again.
     */
    private fun typeWord(field: EditText, word: String) {
        val start = text(field)
        for (i in word.indices) {
            val expected = start + word.substring(0, i + 1)
            for (attempt in 1..3) {
                tapText(word[i].toString())
                val typed = runCatching {
                    waitFor("«$expected»", timeoutMs = 1_500) { text(field).takeIf { it == expected } }
                }.isSuccess
                if (typed) break
            }
            assertEquals(expected, text(field))
        }
    }

    private fun tapSpecial(key: SpecialKey) {
        val point = keyCenter { it.special == key } ?: throw AssertionError("no key $key")
        touch(point.x, point.y, holdMs = 40)
    }

    private fun longPressText(text: String, screenshotName: String? = null) {
        val point = keyCenter { (it.action as? KeyAction.Text)?.text == text } ?: throw AssertionError("no key «$text»")
        touch(point.x, point.y, holdMs = 700, whileHeld = { screenshotName?.let(::screenshot) })
    }

    private fun tapView(view: View) {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        touch(location[0] + view.width / 2f, location[1] + view.height / 2f, holdMs = 40)
    }

    private fun touch(x: Float, y: Float, holdMs: Long, whileHeld: () -> Unit = {}) {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        Thread.sleep(holdMs)
        whileHeld()
        inject(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
        Thread.sleep(80)
    }

    /** Taps [button] until [result] appears; the emulator now and then drops a tap. */
    private fun tapUntilShown(button: (View) -> Boolean, result: (View) -> Boolean): View {
        repeat(3) {
            tapView(waitForView(button))
            runCatching { waitFor("view", timeoutMs = 6_000) { findView(result) } }.getOrNull()?.let { return it }
        }
        return waitForView(result)
    }

    private fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY, 0))
        val steps = 12
        for (i in 1..steps) {
            Thread.sleep(15)
            val x = fromX + (toX - fromX) * i / steps
            val y = fromY + (toY - fromY) * i / steps
            inject(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0))
        }
        inject(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, toX, toY, 0))
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
    }

    private fun inject(event: MotionEvent) {
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue("touch was not injected", automation.injectInputEvent(event, true))
        event.recycle()
    }

    private fun findView(match: (View) -> Boolean): View? {
        val ref = AtomicReference<View?>()
        instrumentation.runOnMainSync {
            fun search(v: View): View? {
                if (match(v)) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) search(v.getChildAt(i))?.let { return it }
                return null
            }
            ref.set(KeyboardService.instance?.inputRootForTest?.let(::search))
        }
        return ref.get()
    }

    private fun waitForView(match: (View) -> Boolean): View = waitFor("view", timeoutMs = 20_000) { findView(match) }

    private fun <T : Any> waitFor(what: String, timeoutMs: Long = 10_000, probe: () -> T?): T {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            probe()?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        // The keyboard window may still show the previous frame (a theme switch looked unchanged).
        Thread.sleep(300)
        shell("screencap -p $SCREENS/$name.png")
    }

    private fun shell(command: String) {
        shellOutput(command)
    }

    private fun shellOutput(command: String): String =
        automation.executeShellCommand(command).let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private companion object {
        const val SCREENS = "/data/local/tmp/screens"
    }
}
