package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.ChineseScripts.SIMPLIFIED
import com.vpr.screenlate.core.common.language.ChineseScripts.TRADITIONAL
import java.util.Locale
import org.junit.Test

class ChineseScriptsTest {

    private fun convert(text: String, tag: String) = ChineseScripts.convert(MappedText.identity(text), tag).map { it.text }

    @Test
    fun `text belongs to the script most of its characters are only in`() {
        assertThat(ChineseScripts.scriptOf("图书馆")).isEqualTo(SIMPLIFIED)
        assertThat(ChineseScripts.scriptOf("圖書館")).isEqualTo(TRADITIONAL)
        assertThat(ChineseScripts.scriptOf("我们在图书馆")).isEqualTo(SIMPLIFIED)
    }

    @Test
    fun `characters both scripts write alike belong to neither`() {
        assertThat(ChineseScripts.scriptOf("中文")).isNull()
        // 后 and 里 are Traditional characters too; 着 is the Taiwan form of 著.
        assertThat(ChineseScripts.scriptOf("后")).isNull()
        assertThat(ChineseScripts.scriptOf("里")).isNull()
        assertThat(ChineseScripts.scriptOf("着")).isNull()
        assertThat(ChineseScripts.scriptOf("OK")).isNull()
    }

    @Test
    fun `a character with several traditional forms gives a spelling for each`() {
        assertThat(convert("头发", TRADITIONAL)).containsExactly("頭發", "頭髮")
        // 舘 is a rarer form of 館.
        assertThat(convert("图书馆", TRADITIONAL)).containsExactly("圖書館", "圖書舘").inOrder()
    }

    @Test
    fun `traditional text becomes simplified`() {
        assertThat(convert("頭髮", SIMPLIFIED)).containsExactly("头发")
        assertThat(convert("圖書館", SIMPLIFIED)).containsExactly("图书馆")
    }

    @Test
    fun `text already in the script gives nothing`() {
        assertThat(convert("圖書館", TRADITIONAL)).isEmpty()
        assertThat(convert("图书馆", SIMPLIFIED)).isEmpty()
        assertThat(convert("中文", SIMPLIFIED)).isEmpty()
    }

    @Test
    fun `spellings are capped`() {
        // 发 has two likely forms, so five of them would give 32 spellings.
        assertThat(convert("发发发发发", TRADITIONAL).size).isAtMost(ChineseScripts.MAX_SPELLINGS)
    }

    @Test
    fun `a spelling maps back to the source text`() {
        val converted = ChineseScripts.convert(MappedText.identity("头发长"), TRADITIONAL).first()
        assertThat(converted.sourceLength(2)).isEqualTo(2)
        assertThat(converted.sourceLength(3)).isEqualTo(3)
    }

    @Test
    fun `the default script follows the interface language`() {
        assertThat(ChineseSupport.defaultScriptTag(Locale.SIMPLIFIED_CHINESE)).isEqualTo(SIMPLIFIED)
        assertThat(ChineseSupport.defaultScriptTag(Locale.TRADITIONAL_CHINESE)).isEqualTo(TRADITIONAL)
        assertThat(ChineseSupport.defaultScriptTag(Locale.forLanguageTag("zh-Hant"))).isEqualTo(TRADITIONAL)
        assertThat(ChineseSupport.defaultScriptTag(Locale.forLanguageTag("zh-HK"))).isEqualTo(TRADITIONAL)
        assertThat(ChineseSupport.defaultScriptTag(Locale.US)).isEqualTo(SIMPLIFIED)
        assertThat(ChineseSupport.defaultScriptTag(Locale.forLanguageTag("ru-RU"))).isEqualTo(SIMPLIFIED)
    }

    @Test
    fun `readings lose the numbered pinyin a dictionary adds`() {
        assertThat(ChineseSupport.displayReading("xíng (xing²)")).isEqualTo("xíng")
        assertThat(ChineseSupport.displayReading("nǚ'ér (nü:3 er2)")).isEqualTo("nǚ'ér")
        assertThat(ChineseSupport.displayReading("tou (tou⁵／tou⁰)")).isEqualTo("tou")
        assertThat(ChineseSupport.displayReading("Xiàng (Xiang⁴)")).isEqualTo("Xiàng")
        assertThat(ChineseSupport.displayReading("ế (ê²)")).isEqualTo("ế")
        assertThat(ChineseSupport.displayReading("túshūguǎn")).isEqualTo("túshūguǎn")
        assertThat(ChineseSupport.displayReading("Běijīng (capital)")).isEqualTo("Běijīng (capital)")
    }
}
