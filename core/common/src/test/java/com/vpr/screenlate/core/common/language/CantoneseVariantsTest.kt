package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class CantoneseVariantsTest {

    @Test
    fun `a word is spelled in the hong kong, taiwan and other forms of its characters`() {
        assertThat(CantoneseVariants.spellings("衛生")).containsExactly("衞生")
        assertThat(CantoneseVariants.spellings("衞生")).containsExactly("衛生")
        assertThat(CantoneseVariants.spellings("爲咗")).containsExactly("為咗")
        assertThat(CantoneseVariants.spellings("電綫")).containsExactly("電線")
        // Every grouped character takes the same form.
        assertThat(CantoneseVariants.spellings("衛生溫度")).containsExactly("衞生温度")
    }

    @Test
    fun `words without variant characters, and the pairs that are different words, have no other spelling`() {
        for (word in listOf("我", "着", "著", "只", "才", "台", "臺")) {
            assertThat(CantoneseVariants.spellings(word)).isEmpty()
        }
    }

    @Test
    fun `the groups are those of the lookup page's processor`() {
        val processors = File("../../scripts/yomitan-language/own-processors.js").readText()
        val groups = Regex("const CANTONESE_VARIANT_GROUPS = '([^']*)';").find(processors)!!.groupValues[1]
        assertThat(groups).isEqualTo(CantoneseVariants.GROUPS)
    }

    @Test
    fun `no character is in two groups`() {
        val characters = CantoneseVariants.GROUPS.split(' ').flatMap { group -> group.codePoints().toArray().distinct() }
        assertThat(characters).containsNoDuplicates()
    }
}
