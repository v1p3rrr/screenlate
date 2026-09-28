package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexTextTest {

    @Test
    fun `raw json escapes become text`() {
        val raw = listOf("監修", "n編集", "u00a9 Shogakukan", "nhttps:", "/", "/daijisen.jp").joinToString("\\")
        assertThat(decodeIndexText(raw)).isEqualTo("監修\n編集© Shogakukan\nhttps://daijisen.jp")
        assertThat(decodeIndexText("say " + "\\" + "\"hi" + "\\" + "\"")).isEqualTo("say \"hi\"")
    }

    @Test
    fun `plain text and broken escapes stay as they are`() {
        assertThat(decodeIndexText("Jitendex.org")).isEqualTo("Jitendex.org")
        assertThat(decodeIndexText(null)).isNull()
        val broken = "C:" + "\\" + "q"
        assertThat(decodeIndexText(broken)).isEqualTo(broken)
    }
}
