package com.vpr.screenlate.overlay.fonts

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The phone's fonts the lookup page names: other fonts for the language (such as Hentaigana) must not stand in. */
@RunWith(AndroidJUnit4::class)
class SystemFontFilesTest {

    private fun names(serif: Boolean): Set<String> {
        val font = SystemFontFiles.find(JapaneseSupport, serif)
        assumeTrue("The phone has no Japanese font", font != null)
        return FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }.toSet()
    }

    @Test
    fun findsTheFontsThePageNames() {
        val sans = JapaneseSupport.systemFonts.sans.map { it.lowercase() }
        val serif = JapaneseSupport.systemFonts.serif.map { it.lowercase() }
        assertThat(names(serif = false).any { it in sans }).isTrue()
        assertThat(names(serif = true).any { it in serif }).isTrue()
    }

    @Test
    fun weightsAreTheRangesOfThoseFonts() {
        val weights = SystemFontFiles.weights(JapaneseSupport)
        assertThat(weights.sans).isEqualTo(SystemFontFiles.find(JapaneseSupport)?.let(SystemFontFiles::weightRange))
        assertThat(weights.serif).isEqualTo(SystemFontFiles.find(JapaneseSupport, serif = true)?.let(SystemFontFiles::weightRange))
    }
}
