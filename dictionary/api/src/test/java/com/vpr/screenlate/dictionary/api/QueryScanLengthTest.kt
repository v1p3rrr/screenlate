package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import org.junit.Test

class QueryScanLengthTest {
    @Test
    fun coversTheWholeQueryInCharacters() {
        assertThat(queryScanLength("取り扱い説明書")).isEqualTo(7)
        // A character outside the BMP is one character, not two UTF-16 units.
        assertThat(queryScanLength("𠮷野家")).isEqualTo(3)
    }

    @Test
    fun staysWithinTheSettingsRange() {
        assertThat(queryScanLength("")).isEqualTo(LookupSettings.MIN_SCAN_LENGTH)
        assertThat(queryScanLength("あ".repeat(5000))).isEqualTo(LookupSettings.MAX_SCAN_LENGTH)
    }
}
