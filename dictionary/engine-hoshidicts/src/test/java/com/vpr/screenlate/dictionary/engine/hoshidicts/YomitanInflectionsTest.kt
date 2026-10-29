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
        assertThat(filtered).contains(Language.MARATHI)
        for (language in filtered) {
            assertWithMessage(language.name).that(YomitanInflections.names(language.dictionaryCode)).isNotEmpty()
        }
    }

    @Test
    fun `marathi endings and particles are listed as written, without strings`() {
        assertThat(YomitanInflections.names("mr")).containsAtLeast("-त", "-ही", "-च", "-साठी")
    }
}
