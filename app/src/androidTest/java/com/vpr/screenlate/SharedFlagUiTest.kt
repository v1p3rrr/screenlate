package com.vpr.screenlate

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.camera.PhotoTestAccess
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in visual acceptance for the remaining shared-flag screens; no Compose test rule on the newest Android. */
@RunWith(AndroidJUnit4::class)
class SharedFlagUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun firstRunAndSetupTitlesShowSharedFlagCodes() = runBlocking {
        assumeTrue(context.packageName == "com.vpr.screenlate.verification" &&
            InstrumentationRegistry.getArguments().getString("sharedFlagUi") == "true")
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val preferences = access.commonPreferences()
        val original = access.profiles().current()
        val originalFirstRun = preferences.data.first()[LanguageProfiles.FIRST_RUN]
        try {
            preferences.edit { it[LanguageProfiles.FIRST_RUN] = true }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText(context.getString(R.string.first_run_question))
                scenario.onActivity { activity ->
                    val model = androidx.lifecycle.ViewModelProvider(activity)[com.vpr.screenlate.languages.FirstRunViewModel::class.java]
                    check(model.languages.containsAll(INDIA_LANGUAGES)) { "The first-run model misses an Indian language" }
                }
                INDIA_LANGUAGES.forEach { language ->
                    scrollToText(language.displayName())
                    screenshot("lng47-first-run-${language.code}.png")
                }
            }

            preferences.edit { it.remove(LanguageProfiles.FIRST_RUN) }
            INDIA_LANGUAGES.forEach { language ->
                val intent = Intent(context, MainActivity::class.java).putExtra("code", language.code)
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            com.vpr.screenlate.ui.theme.ScreenlateTheme {
                                com.vpr.screenlate.languages.LanguageSetupScreen({}, {})
                            }
                        }
                    }
                    awaitText(language.displayName())
                    screenshot("lng47-setup-${language.code}.png")
                }
            }
        } finally {
            access.profiles().restore(original)
            preferences.edit {
                if (originalFirstRun == null) it.remove(LanguageProfiles.FIRST_RUN)
                else it[LanguageProfiles.FIRST_RUN] = originalFirstRun
            }
        }
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == text } == null) {
            check(SystemClock.elapsedRealtime() < deadline) { "Text is not visible: $text" }
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun scrollToText(text: String) {
        val seen = mutableSetOf<String>()
        for (forward in listOf(true, false)) {
            repeat(40) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                seen += texts(root)
                if (find(root) { it.text?.toString() == text } != null) return
                val scrollable = find(root) { it.isScrollable }
                check(scrollable != null) { "No scrollable content while looking for $text" }
                swipe(scrollable, forward)
                SystemClock.sleep(200)
            }
        }
        screenshot("lng47-missing-text.png")
        error("Text is not visible: $text; seen=${seen.sorted()}")
    }

    private fun texts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        return listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
    }

    private fun swipe(scrollable: AccessibilityNodeInfo, forward: Boolean) {
        val bounds = Rect().also(scrollable::getBoundsInScreen)
        val x = bounds.centerX().toFloat()
        val near = if (forward) bounds.bottom - 300 else bounds.top + 300
        val far = near + if (forward) -800 else 800
        val down = SystemClock.uptimeMillis()
        inject(down, down, MotionEvent.ACTION_DOWN, x, near.toFloat())
        repeat(8) { step ->
            SystemClock.sleep(16)
            val fraction = (step + 1) / 8f
            inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, near + (far - near) * fraction)
        }
        // Stop the pointer before releasing: a fast release flings past the rows the test needs to inspect.
        SystemClock.sleep(350)
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, far.toFloat())
    }

    private fun inject(down: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        try {
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(250)
        val output = File(context.getExternalFilesDir(null), "ui-acceptance").apply { mkdirs() }
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
            File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }

    private companion object {
        val INDIA_LANGUAGES = listOf(
            Language.HINDI,
            Language.MARATHI,
            Language.TAMIL,
            Language.TELUGU,
            Language.KANNADA,
        )
        const val TIMEOUT_MS = 15_000L
        const val POLL_MS = 50L
    }
}
