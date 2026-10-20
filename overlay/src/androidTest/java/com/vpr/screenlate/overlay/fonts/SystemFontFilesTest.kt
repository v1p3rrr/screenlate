package com.vpr.screenlate.overlay.fonts

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.ArmenianSupport
import com.vpr.screenlate.core.common.language.GeorgianSupport
import com.vpr.screenlate.core.common.language.HindiSupport
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.core.common.language.KhmerSupport
import com.vpr.screenlate.core.common.language.ThaiSupport
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

    /** Android declares its Devanagari fonts for the script (`und-Deva`), not for Hindi. */
    @Test
    fun findsTheDevanagariSerifThroughItsScript() {
        val font = SystemFontFiles.find(HindiSupport, serif = true)
        assumeTrue("The phone has no Devanagari serif font", font != null)
        val names = FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }
        assertThat(names.any { it in HindiSupport.systemFonts.serif.map(String::lowercase) }).isTrue()
    }

    /** The Georgian ones are declared for `und-Geor`. */
    @Test
    fun findsTheGeorgianSerifThroughItsScript() {
        val font = SystemFontFiles.find(GeorgianSupport, serif = true)
        assumeTrue("The phone has no Georgian serif font", font != null)
        val names = FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }
        assertThat(names.any { it in GeorgianSupport.systemFonts.serif.map(String::lowercase) }).isTrue()
    }

    /** The Armenian ones are declared for `und-Armn`. */
    @Test
    fun findsTheArmenianSerifThroughItsScript() {
        val font = SystemFontFiles.find(ArmenianSupport, serif = true)
        assumeTrue("The phone has no Armenian serif font", font != null)
        val names = FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }
        assertThat(names.any { it in ArmenianSupport.systemFonts.serif.map(String::lowercase) }).isTrue()
    }

    /** The Thai ones are declared for `und-Thai`; the sans-serif is the default font, which the page needs no name for. */
    @Test
    fun findsTheThaiSerifThroughItsScript() {
        val font = SystemFontFiles.find(ThaiSupport, serif = true)
        assumeTrue("The phone has no Thai serif font", font != null)
        val names = FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }
        assertThat(names.any { it in ThaiSupport.systemFonts.serif.map(String::lowercase) }).isTrue()
    }

    /** The Khmer ones are declared for `und-Khmr`; the sans-serif is reached by fallback as the Thai one. */
    @Test
    fun findsTheKhmerSerifThroughItsScript() {
        val font = SystemFontFiles.find(KhmerSupport, serif = true)
        assumeTrue("The phone has no Khmer serif font", font != null)
        val names = FontFiles.localNames(font!!.buffer, font.ttcIndex).map { it.lowercase() }
        assertThat(names.any { it in KhmerSupport.systemFonts.serif.map(String::lowercase) }).isTrue()
    }
}
