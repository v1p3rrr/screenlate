package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import org.junit.Test

class YomitanInflectionsTest {

    @Test
    fun `languages whose rules give way to entries or rows have their rule ids`() {
        val filtered = Language.entries.filter {
            it.support.inflectionRulesOnlyWithoutEntry || it.support.ruleChainsOnlyWithoutRow
        }
        assertThat(filtered).containsAtLeast(Language.MARATHI, Language.TAMIL, Language.TELUGU, Language.KANNADA)
        for (language in filtered) {
            assertWithMessage(language.name).that(YomitanInflections.names(language.dictionaryCode)).isNotEmpty()
        }
    }

    @Test
    fun `marathi endings and particles are listed as written, without strings`() {
        assertThat(YomitanInflections.names("mr")).containsAtLeast("-त", "-ही", "-च", "-साठी")
    }

    @Test
    fun `tamil endings and particles are listed as written, the doubled consonant not at all`() {
        assertThat(YomitanInflections.names("ta")).containsAtLeast("-இல்", "-கள்", "-உம்", "-அத்து")
        assertThat(YomitanInflections.names("ta")).doesNotContain("-க்")
    }

    @Test
    fun `telugu endings and particles are listed as written, vowel signs by their letters`() {
        assertThat(YomitanInflections.names("te")).containsAtLeast("-లో", "-ఆన్ని", "-ల", "-లు", "-నే", "-ఏ")
    }

    @Test
    fun `kannada endings and particles are listed by their vowels' letters`() {
        assertThat(YomitanInflections.names("kn")).containsAtLeast("-ಅಲ್ಲಿ", "-ಇಂದ", "-ಕ್ಕೆ", "-ಗಳ", "-ಗಳು", "-ಊ", "-ಏ")
    }
}
