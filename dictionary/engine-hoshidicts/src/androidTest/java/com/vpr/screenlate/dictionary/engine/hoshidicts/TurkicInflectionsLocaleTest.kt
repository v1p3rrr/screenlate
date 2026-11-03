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
class TurkicInflectionsLocaleTest {
    @Test
    fun withoutHasItsOwnNameInEveryInterfaceLocaleAndKeepsTheEndingsDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = mapOf(
            "en" to "without", "ru" to "без", "de" to "ohne", "es" to "sin", "fr" to "sans",
            "it" to "senza", "ja" to "～なし", "ko" to "없이", "pl" to "bez", "pt" to "sem",
            "tr" to "-siz (yokluk)", "vi" to "không có", "zh-Hans" to "无", "zh-Hant" to "無",
            "ar" to "بدون", "fa" to "بدون", "he" to "ללא",
        )
        for ((tag, label) in labels) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            for ((language, description) in mapOf(
                "kk" to "-сыз -сіз (заңсыз, белгісіз)",
                "ky" to "-сыз -сиз -суз -сүз (мыйзамсыз, белгисиз)",
            )) {
                val localized = YomitanInflections.localize(resources, language, Transform("without"))!!
                assertThat(localized.name).isEqualTo("without")
                assertThat(localized.label).isEqualTo(label)
                assertThat(localized.description).isEqualTo(description)
            }
        }
    }
}
