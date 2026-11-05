package com.vpr.screenlate

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.camera.PhotoTestAccess
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.languages.LanguageSetupScreen
import com.vpr.screenlate.languages.LanguageSetupViewModel
import com.vpr.screenlate.search.SearchScreen
import com.vpr.screenlate.search.SearchViewModel
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in UI acceptance against the verification app's real catalog, imports and renderer. */
class RealCatalogUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun requireFixture() {
        assumeTrue(context.packageName == "com.vpr.screenlate.verification" &&
            Build.HARDWARE in listOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("realCatalogUi") == "true")
    }

    @Test
    fun downloadedCantoneseRanksRenderInTheResultPage() {
        requireFixture()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: SearchViewModel
            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[SearchViewModel::class.java]
                it.setContent { ScreenlateTheme { SearchScreen(null, {}, {}, viewModel = model) } }
            }
            for ((index, pair) in listOf("我哋" to 279, "佢哋" to 391, "你哋" to 1110, "喺度" to 870, "為咗" to 1172).withIndex()) {
                val (word, rank) = pair
                instrumentation.runOnMainSync { model.query.value = word }
                await { model.results.value?.let { r -> r.text == word && r.results.any { result ->
                    result.term.expression == word && result.term.frequencies.any { group -> group.values.any { it.value == rank } }
                } } == true }
                await {
                    val answer = AtomicReference("false")
                    instrumentation.runOnMainSync {
                        webView(activity.window.decorView)?.evaluateJavascript(
                            "Array.from(document.querySelectorAll('.frequency-value')).some(n => n.textContent.trim() === '$rank')",
                        ) { answer.set(it) }
                    }
                    SystemClock.sleep(100)
                    answer.get() == "true"
                }
                screenshot("cantonese-rank-$index.png")
            }
        }
    }

    @Test
    fun tamilAndTeluguFormsAreMandatoryUntilTheFullEnglishDictionaryIsSelected() {
        requireFixture()
        for (code in listOf("ta", "te")) {
            val intent = Intent(context, MainActivity::class.java).putExtra("code", code)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var model: LanguageSetupViewModel
                scenario.onActivity {
                    model = ViewModelProvider(it)[LanguageSetupViewModel::class.java]
                    it.setContent { ScreenlateTheme { LanguageSetupScreen({}, {}, viewModel = model) } }
                }
                await { model.state.value.loaded }
                val initial = model.state.value
                check(initial.language.dictionaryCode == code) { "Expected $code setup, got ${initial.language.dictionaryCode}" }
                val items = initial.categories.flatMap { it.items }
                val english = checkNotNull(items.singleOrNull { it.entry.id == "wiktionary-$code-en" }) {
                    "No full English dictionary for $code; ids=${items.map { it.entry.id }}"
                }
                instrumentation.runOnMainSync { model.tick(english, false) }
                await { model.state.value.categories.any { it.category == CatalogCategory.FORMS } }
                val withoutEnglish = model.state.value
                val forms = withoutEnglish.categories.single { it.category == CatalogCategory.FORMS }
                assertThat(forms.mandatory).isTrue()
                assertThat(forms.fixed).isTrue()
                assertThat(forms.ticked(forms.items.single(), withoutEnglish.choices)).isTrue()
                val title = context.getString(R.string.catalog_category_forms)
                repeat(8) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    if (find(root) { it.text?.toString() == title } != null) return@repeat
                    find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    SystemClock.sleep(150)
                }
                check(find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == title } != null)
                screenshot("$code-required-forms.png")
                instrumentation.runOnMainSync { model.tick(english, true) }
                await { model.state.value.categories.none { it.category == CatalogCategory.FORMS } }
                screenshot("$code-full-dictionary.png")
            }
        }
    }

    @Test
    fun tamilHomeAndLeftoverCardTrackTheMandatoryForms() = runBlocking {
        requireFixture()
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val repository = access.dictionaries()
        val profiles = access.profiles()
        val installations = access.installation()
        val catalog = access.catalog().localCatalog()
        val fixtures = File(context.getExternalFilesDir(null), "real-catalog")
        val original = profiles.current()
        repository.deleteAll()
        try {
            repository.import(File(fixtures, "wty-ta-en.zip"), catalog = catalog.entries)
            repository.import(File(fixtures, "wty-ta-ru.zip"), catalog = catalog.entries)
            profiles.restore(LanguageProfilesState(listOf(Language.TAMIL), Language.TAMIL))
            assertThat(installations.current(Language.TAMIL).missing).isEmpty()
            ActivityScenario.launch(MainActivity::class.java).use {
                await { find(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.text?.toString() == context.getString(R.string.app_title)
                } != null }
                await { find(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.text?.toString() == context.getString(R.string.problems_title)
                } == null }
                screenshot("ta-home-complete.png")
            }

            repository.getAll().single { it.targetLanguage == "en" }.let { repository.delete(it.id) }
            assertThat(installations.current(Language.TAMIL).missing).containsExactly(CatalogCategory.FORMS)
            ActivityScenario.launch(MainActivity::class.java).use {
                val missing = context.getString(
                    R.string.problems_missing_categories,
                    Language.TAMIL.displayName(),
                    context.getString(R.string.catalog_category_forms),
                )
                scrollToText(missing)
                screenshot("ta-home-missing-forms.png")
            }

            profiles.restore(LanguageProfilesState(listOf(Language.JAPANESE), Language.JAPANESE))
            ActivityScenario.launch(MainActivity::class.java).use {
                clickText(context.getString(R.string.home_languages_all))
                scrollToText(Language.TAMIL.displayName())
                clickText(context.getString(R.string.languages_turn_on))
                // The full English dictionary is selected by default and already contains the forms.
                clickText(catalog.entries.single { it.id == "wiktionary-ta-en" }.displayTitle())
                scrollToText(context.getString(R.string.catalog_category_forms))
                screenshot("ta-leftover-setup.png")
            }
        } finally {
            repository.deleteAll()
            profiles.restore(original)
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

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        check(condition()) { "Real catalog UI did not reach its expected state" }
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun scrollToText(text: String) {
        for (action in listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            repeat(12) {
                if (find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == text } != null) return
                find(instrumentation.uiAutomation.rootInActiveWindow) { it.isScrollable }?.performAction(action)
                SystemClock.sleep(150)
            }
        }
        if (find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == text } == null) {
            screenshot("ui-missing-text.png")
            error("Text is not visible: $text")
        }
    }

    private fun clickText(text: String) {
        scrollToText(text)
        val node = checkNotNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == text })
        val clickable = generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
        check(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Text is not clickable: $text" }
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }
}
