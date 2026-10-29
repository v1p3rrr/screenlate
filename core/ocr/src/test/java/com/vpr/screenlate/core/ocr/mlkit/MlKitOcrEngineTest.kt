package com.vpr.screenlate.core.ocr.mlkit

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.OcrScript
import com.vpr.screenlate.core.common.language.support
import org.junit.Test

class MlKitOcrEngineTest {

    private val engine = MlKitOcrEngine()

    @Test
    fun `each script with a bundled model reads with its own, the others have none`() {
        assertThat(engine.model(OcrScript.LATIN)).isEqualTo(OcrScript.LATIN)
        // Japanese misreads Chinese, so the two do not share a model.
        assertThat(engine.model(OcrScript.JAPANESE)).isEqualTo(OcrScript.JAPANESE)
        assertThat(engine.model(OcrScript.CHINESE)).isEqualTo(OcrScript.CHINESE)
        assertThat(engine.model(OcrScript.KOREAN)).isEqualTo(OcrScript.KOREAN)
        assertThat(engine.model(OcrScript.DEVANAGARI)).isEqualTo(OcrScript.DEVANAGARI)
        // The Devanagari model reads Bengali as well (0.5% errors on clean screens).
        assertThat(engine.model(OcrScript.BENGALI)).isEqualTo(OcrScript.DEVANAGARI)
        for (script in listOf(OcrScript.ARABIC, OcrScript.ARMENIAN, OcrScript.CYRILLIC, OcrScript.GEORGIAN, OcrScript.GREEK, OcrScript.HEBREW, OcrScript.KANNADA, OcrScript.KHMER, OcrScript.TAMIL, OcrScript.TELUGU, OcrScript.THAI)) {
            assertThat(engine.model(script)).isNull()
        }
    }

    @Test
    fun `every language reads on the device except those whose script has no model`() {
        val unread = Language.entries.filterNot { engine.reads(it) }
        assertThat(unread).containsExactlyElementsIn(Language.entries.filter { engine.model(it.support.ocrScript) == null })
        assertThat(unread).containsAtLeast(Language.RUSSIAN, Language.GREEK)
        assertThat(engine.reads(Language.VIETNAMESE)).isTrue()
        assertThat(engine.reads(Language.HINDI)).isTrue()
        assertThat(engine.reads(Language.BENGALI)).isTrue()
        assertThat(engine.reads(Language.MARATHI)).isTrue()
    }
}
