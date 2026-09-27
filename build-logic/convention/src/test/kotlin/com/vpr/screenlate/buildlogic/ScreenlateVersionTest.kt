package com.vpr.screenlate.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScreenlateVersionTest {
    @Test
    fun codeFromTag() {
        assertEquals(ScreenlateVersion("1.2.3", 10203, "abc1234"), ScreenlateVersion.parse("v1.2.3\n", "abc1234\n"))
        assertEquals(ScreenlateVersion("0.12.0", 1200, null), ScreenlateVersion.parse("0.12.0", ""))
    }

    @Test
    fun defaultWithoutTag() {
        assertEquals(ScreenlateVersion("0.1.0", 100, null), ScreenlateVersion.parse(null, null))
        assertEquals(ScreenlateVersion("0.1.0", 100, null), ScreenlateVersion.parse("nightly", null))
    }

    @Test
    fun partsAbove99AreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScreenlateVersion.parse("v1.100.0", null) }
    }
}
