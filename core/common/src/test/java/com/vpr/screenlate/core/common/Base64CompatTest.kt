package com.vpr.screenlate.core.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Base64CompatTest {

    @Test
    fun roundTripSimple() {
        val original = "Hello, Screenlate on Android 7!".toByteArray(Charsets.UTF_8)
        val encoded = Base64Compat.encodeToString(original)
        val decoded = Base64Compat.decode(encoded)
        assertArrayEquals(original, decoded)
        assertEquals("Hello, Screenlate on Android 7!", String(decoded, Charsets.UTF_8))
    }

    @Test
    fun roundTripBinary() {
        val original = byteArrayOf(0, 1, 2, 127, -128, -1, 42, 99)
        val encoded = Base64Compat.encodeToString(original)
        val decoded = Base64Compat.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun decodeMime() {
        val original = "MIME encoded data\r\nwith line breaks".toByteArray(Charsets.UTF_8)
        val encoded = java.util.Base64.getMimeEncoder().encodeToString(original)
        val decodedString = Base64Compat.decodeMime(encoded)
        val decodedBytes = Base64Compat.decodeMime(encoded.toByteArray(Charsets.UTF_8))
        assertArrayEquals(original, decodedString)
        assertArrayEquals(original, decodedBytes)
    }
}
