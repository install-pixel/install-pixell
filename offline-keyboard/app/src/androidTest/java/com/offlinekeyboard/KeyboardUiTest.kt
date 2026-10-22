package com.offlinekeyboard

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
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
        shell("ime enable $ime")
        shell("ime set $ime")
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
        "дяк".forEach { tapText(it.toString()) }
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
        tapText("а")
        assertEquals("а", text(activity.plain))
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

    private fun focus(field: EditText) {
        instrumentation.runOnMainSync {
            field.requestFocus()
            field.context.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
        service()
        clear(field)
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
        shell("screencap -p $SCREENS/$name.png")
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }

    private companion object {
        const val SCREENS = "/data/local/tmp/screens"
    }
}
