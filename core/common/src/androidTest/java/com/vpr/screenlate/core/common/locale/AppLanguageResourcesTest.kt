package com.vpr.screenlate.core.common.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageResourcesTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fallback = context.resources

    private fun stringIn(tag: String): String {
        val config = Configuration(fallback.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return context.createConfigurationContext(config).getString(android.R.string.cancel)
    }

    @Test
    fun followsTheSystemWithoutAnAppLanguage() {
        val resources = AppLanguageResources(context) { null }
        assertThat(resources.resources(fallback)).isSameInstanceAs(fallback)
    }

    @Test
    fun usesTheAppLanguage() {
        val system = fallback.configuration.locales[0].language
        val tag = if (system == "de") "fr" else "de"
        val resources = AppLanguageResources(context) { LocaleList.forLanguageTags(tag) }.resources(fallback)
        assertThat(resources.configuration.locales[0]).isEqualTo(Locale.forLanguageTag(tag))
        assertThat(resources.getString(android.R.string.cancel)).isEqualTo(stringIn(tag))
    }

    @Test
    fun reusesResourcesUntilTheLanguageChanges() {
        var tag = "de"
        val language = AppLanguageResources(context) { LocaleList.forLanguageTags(tag) }
        val first = language.resources(fallback)
        assertThat(language.resources(fallback)).isSameInstanceAs(first)
        tag = "ja"
        val second = language.resources(fallback)
        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.configuration.locales[0]).isEqualTo(Locale.JAPANESE)
    }
}
