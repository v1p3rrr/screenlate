package com.vpr.screenlate.search

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.languages.LanguageTestFixture
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchLanguageTest {
    @Test
    fun installingLanguageShowsStatusAndSearchStartsWhenItsDictionaryIsReady() {
        LanguageTestFixture().use { fixture ->
            runBlocking { fixture.profiles.turnOn(Language.ENGLISH) }
            val model = fixture.search()
            ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
                activity.onActivity {
                    it.setContent { ScreenlateTheme { SearchScreen({}, {}, {}, initialQuery = "walk", viewModel = model) } }
                }
                waitForPage("document.querySelector('.message') !== null && document.querySelector('.entry') === null")
                assertTrue(model.results.value?.installation != null)
                assertEquals(0, fixture.lookupCalls)
                runBlocking { fixture.dictionary("English Dictionary") }
                waitForPage("document.querySelector('.entry') !== null && document.querySelector('.message') === null")
                assertTrue(fixture.lookupCalls > 0)
            }
        }
    }
    @Test
    fun switchingProfilesRefreshesAnkiButtonsEvenWhenTheWordResultsAreIdentical() {
        LanguageTestFixture().use { fixture ->
            runBlocking {
                fixture.dictionary("Shared Dictionary", language = null)
                LookupSettingsRepository(fixture.preferences).setRomaji(true)
                fixture.ankiSettings.update(Language.ENGLISH) { it.copy(deckId = 1, modelId = 1, fields = mapOf("Front" to "{expression}")) }
                fixture.profiles.turnOn(Language.ENGLISH, activate = false)
            }
            val model = fixture.search()
            ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
                activity.onActivity {
                    it.setContent {
                        ScreenlateTheme { SearchScreen({}, {}, {}, initialQuery = "walk", viewModel = model) }
                    }
                }
                waitForPage("document.querySelector('.entry') !== null && document.querySelector('.action-add') === null")
                runBlocking { fixture.profiles.setActive(Language.ENGLISH) }
                waitForPage("document.querySelector('.action-add:not(.unavailable)') !== null")
                runBlocking { fixture.profiles.setActive(Language.JAPANESE) }
                waitForPage("document.querySelector('.entry') !== null && document.querySelector('.action-add') === null")
            }
        }
    }

    private fun waitForPage(condition: String) {
        val answer = AtomicReference("false")
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (answer.get() != "true" && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                val view = activity?.window?.decorView?.let(::webView)
                view?.evaluateJavascript(condition) { answer.set(it) }
            }
            SystemClock.sleep(50)
        }
        assertTrue("The result page did not reach the expected state: $condition", answer.get() == "true")
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }
}
