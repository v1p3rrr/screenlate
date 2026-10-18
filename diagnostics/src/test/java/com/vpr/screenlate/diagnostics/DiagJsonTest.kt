package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagJsonTest {
    /** `~` stands for a backslash, which is easier to read in expected JSON. */
    private fun json(text: String) = text.replace('~', 92.toChar())

    @Test
    fun `writes the fields after time and event and skips nulls`() {
        val line = DiagJson.line(
            1,
            2,
            "scan",
            arrayOf(
                "a" to 1,
                "b" to null,
                "c" to true,
                "d" to 1.234,
                "g" to listOf(1, null, "x"),
                "f" to mapOf("k" to 2L, "n" to null),
                "s" to Thread.State.NEW,
            ),
        )
        assertThat(line).isEqualTo("""{"t":1,"up":2,"e":"scan","a":1,"c":true,"d":1.23,"g":[1,null,"x"],"f":{"k":2},"s":"NEW"}""")
    }

    @Test
    fun `writes whole doubles without decimals and NaN as null`() {
        assertThat(DiagJson.encode(2.0)).isEqualTo("2")
        assertThat(DiagJson.encode(0.5f)).isEqualTo("0.5")
        assertThat(DiagJson.encode(Double.NaN)).isEqualTo("null")
        assertThat(DiagJson.encode(intArrayOf(1, 2))).isEqualTo("[1,2]")
    }

    @Test
    fun `escapes quotes, backslashes and control characters`() {
        val text = "a\"b" + 92.toChar() + "c" + 10.toChar() + "d" + 1.toChar()
        assertThat(DiagJson.encode(text)).isEqualTo(json(""""a~"b~~c~nd~u0001""""))
    }

    @Test
    fun `cuts long strings`() {
        val encoded = DiagJson.encode("x".repeat(DiagJson.MAX_STRING + 40))
        assertThat(encoded).isEqualTo("\"" + "x".repeat(DiagJson.MAX_STRING) + "…\"")
    }
}
