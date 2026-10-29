package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.util.Locale
import org.junit.Test

class CantoneseSupportTest {

    private val support = Language.CANTONESE.support

    @Test
    fun `cantonese is its own language with chinese text rules`() {
        assertThat(support).isSameInstanceAs(CantoneseSupport)
        assertThat(Language.CANTONESE.code).isEqualTo("yue")
        assertThat(support.glyph).isEqualTo("粵")
        assertThat(support.codeLabel).isEqualTo("YUE")
        assertThat(support.ocrScript).isEqualTo(OcrScript.CHINESE)
        assertThat(support.flagCountries).containsExactly("hk")
        assertThat(support.lookupStart("我哋", latinAsNative = false)).isEqualTo(LookupStart.Any)
        assertThat(support.lookupStart("OK 啦", latinAsNative = false)).isEqualTo(ChineseSupport.lookupStart("OK 啦", false))
        assertThat(support.endsSentence("佢走咗。", 3)).isTrue()
    }

    @Test
    fun `traditional characters in their hong kong forms come first whatever the interface language`() {
        assertThat(support.scriptTags).containsExactly("yue-Hans", "yue-HK")
        for (locale in listOf(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.ENGLISH)) {
            assertThat(support.defaultScriptTag(locale)).isEqualTo("yue-HK")
        }
        assertThat(support.languageTag).isEqualTo("yue-HK")
        assertThat(support.scriptOf("广东话")).isEqualTo("yue-Hans")
        assertThat(support.scriptOf("廣東話")).isEqualTo("yue-HK")
        assertThat(support.scriptOf("我哋")).isNull()
        assertThat(support.inScript(MappedText.identity("广东话"), "yue-HK").map { it.text }).contains("廣東話")
        assertThat(support.inScript(MappedText.identity("廣東話"), "yue-Hans").map { it.text }).contains("广东话")
    }

    @Test
    fun `recordings are titled in yale, then by the characters under chinese`() {
        assertThat(support.recordings("我哋", "ngo5 dei6"))
            .containsExactly(RecordingTitle("yue", "ngo5dei6"), RecordingTitle("zh", "yue-我哋")).inOrder()
        assertThat(support.recordings("我哋", "")).containsExactly(RecordingTitle("zh", "yue-我哋"))
        assertThat(support.recordingTitle("我哋", "ngo5 dei6")).isEqualTo("ngo5dei6")
        assertThat(support.recordingTitle("我哋", "")).isEqualTo("我哋")
        assertThat(support.defaultAudioSources).containsExactly("LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH").inOrder()
    }

    @Test
    fun `a voice reads the characters, hong kong cantonese first`() {
        assertThat(support.speechText("我哋", "ngo5 dei6")).isEqualTo("我哋")
        assertThat(support.speechTag).isEqualTo("yue-HK")
        assertThat(support.otherSpeechTags).containsExactly("zh-HK")
        assertThat(ChineseSupport.otherSpeechTags).isEmpty()
    }

    @Test
    fun `typed jyutping finds words with or without tones and spaces`() {
        assertThat(support.readingSearch).isTrue()
        assertThat(support.normalizeReading("Nei5 Hou2")).isEqualTo("nei5hou2")
        assertThat(support.readingKey("nei5 hou2")).isEqualTo(support.readingKey("neihou"))
        assertThat(support.readingMatch("nei5hou2", "nei5 hou2")).isGreaterThan(support.readingMatch("neihou", "nei5 hou2"))
    }

    @Test
    fun `a frequency word shares its rank with its other forms and scripts`() {
        assertThat(support.frequencySpellings("爲")).containsAtLeast("為", "为")
        assertThat(support.frequencySpellings("衛生")).containsAtLeast("衞生", "卫生")
        assertThat(support.frequencySpellings("爲")).doesNotContain("爲")
        assertThat(support.frequencySpellings("我")).isEmpty()
    }

    @Test
    fun `anki fields of cantonese note types are recognised`() {
        assertThat(support.ankiFieldNames["reading"]).containsAtLeast("jyutping", "粵拼")
        assertThat(support.scriptMarkers).containsExactly("expression-simplified", "yue-Hans", "expression-traditional", "yue-HK")
    }
}
