package com.vpr.screenlate.audio

import com.vpr.screenlate.core.common.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class TestWordAfterTest {
    @Test
    fun `the previous language's sample becomes the new language's`() {
        assertEquals("Wasser", testWordAfter("読む", Language.JAPANESE, Language.GERMAN))
    }

    @Test
    fun `a blank word becomes the new language's sample`() {
        assertEquals("read", testWordAfter(" ", Language.JAPANESE, Language.ENGLISH))
    }

    @Test
    fun `a word typed by the user stays`() {
        assertEquals("食べる", testWordAfter("食べる", Language.JAPANESE, Language.GERMAN))
    }

    @Test
    fun `another language's sample is kept as typed`() {
        assertEquals("read", testWordAfter("read", Language.JAPANESE, Language.GERMAN))
    }
}
