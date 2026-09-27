package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.model.Transform
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class JapaneseInflectionsLocaleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun resources(locale: Locale) =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
            .resources

    private val english = resources(Locale.ENGLISH)
    private val russian = resources(Locale.forLanguageTag("ru"))

    @Test
    fun namesInLatinLettersAndDescriptionsFollowTheLanguage() {
        val inEnglish = JapaneseInflections.localize(english, Transform("passive", "engine text"))
        assertThat(inEnglish.name).isEqualTo("passive")
        assertThat(inEnglish.label).isEqualTo("passive")
        assertThat(inEnglish.description).startsWith("1. Indicates an action received")

        val inRussian = JapaneseInflections.localize(russian, Transform("passive", "engine text"))
        assertThat(inRussian.name).isEqualTo("passive")
        assertThat(inRussian.label).isEqualTo("страдательная")
        assertThat(inRussian.description).startsWith("1. Обозначает действие")
    }

    @Test
    fun kanaNamesStayAndUnknownRulesKeepTheEngineText() {
        val te = JapaneseInflections.localize(russian, Transform("-て", "engine text"))
        assertThat(te.label).isEmpty()
        assertThat(te.description).startsWith("て-форма.")

        val unknown = Transform("new rule", "engine text")
        assertThat(JapaneseInflections.localize(russian, unknown)).isEqualTo(unknown)
    }
}
