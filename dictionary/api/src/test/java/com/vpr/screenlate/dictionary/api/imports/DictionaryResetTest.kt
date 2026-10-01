package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset.Resume
import org.junit.Test

class DictionaryResetTest {
    @Test
    fun `a start without an unfinished reset does nothing`() {
        assertThat(DictionaryReset.resumeAfter(null)).isEqualTo(Resume.NONE)
    }

    @Test
    fun `a reset the app died in is run once more, then given up`() {
        assertThat(DictionaryReset.resumeAfter(1)).isEqualTo(Resume.RETRY)
        assertThat(DictionaryReset.resumeAfter(DictionaryReset.MAX_ATTEMPTS)).isEqualTo(Resume.GIVE_UP)
        assertThat(DictionaryReset.resumeAfter(DictionaryReset.MAX_ATTEMPTS + 1)).isEqualTo(Resume.GIVE_UP)
    }
}
