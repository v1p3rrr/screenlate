package com.vpr.screenlate.core.common

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test

class RedactionTest {

    @Test
    fun `keeps the type and stack trace but not the message`() {
        val original = IOException("unexpected end of stream on https://example.com/audio?term=食べる")
        val redacted = original.redacted()
        assertThat(redacted.toString()).isEqualTo("java.io.IOException")
        assertThat(redacted.stackTrace).isEqualTo(original.stackTrace)
        assertThat(redacted.stackTraceToString()).doesNotContain("食べる")
    }

    @Test
    fun `redacts causes too`() {
        val original = RuntimeException("wrapper 読む", IllegalStateException("inner 読む"))
        val text = original.redacted().stackTraceToString()
        assertThat(text).contains("java.lang.IllegalStateException")
        assertThat(text).doesNotContain("読む")
    }

    @Test
    fun `urls keep only scheme and host`() {
        assertThat(redactUrl("https://jisho.org/search/食べる")).isEqualTo("https://jisho.org/…")
        assertThat(redactUrl("not a url with spaces")).isEqualTo("<url>")
        assertThat(redactUrl("mailto:someone@example.com")).isEqualTo("mailto:…")
    }
}
