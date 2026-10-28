package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class FoldedMarksTest {
    private val marks = Json.parseToJsonElement(HoshidictsEngine.HEADWORD_MARKS.decodeToString()).jsonObject

    private fun foldsMarks(code: String) = marks[code]?.jsonObject?.get("fold_marks")?.jsonPrimitive?.boolean

    @Test
    fun `the bundles of the languages that fold marks look text from ocr up folded, the others do not`() {
        for (language in Language.entries.distinctBy { it.dictionaryCode }) {
            val bundle = File("src/main/assets/${HoshidictsEngine.SCRIPTS}/${language.dictionaryCode}.js")
            if (!bundle.exists()) {
                assertWithMessage(language.name).that(language.support.foldsMarks).isFalse()
                continue
            }
            assertWithMessage(language.name).that(bundle.readText().contains("name:\"Folded marks\""))
                .isEqualTo(language.support.foldsMarks)
        }
    }

    @Test
    fun `the import folds the marks of every such language, under each code its dictionaries name`() {
        for (language in Language.entries) {
            val expected = if (language.support.foldsMarks) true else marks[language.dictionaryCode]?.let { false }
            assertWithMessage(language.name).that(foldsMarks(language.dictionaryCode)).isEqualTo(expected)
        }
        // Hungarian has no marks of its own to drop, Norwegian dictionaries name Bokmål and Nynorsk.
        assertThat(foldsMarks("hu")).isTrue()
        assertThat(foldsMarks("nb")).isTrue()
        assertThat(foldsMarks("nn")).isTrue()
        assertThat(foldsMarks("vi")).isNull()
        assertThat(foldsMarks("el")).isFalse()
    }
}
