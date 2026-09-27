package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.EOFException
import org.junit.Assert.assertThrows
import org.junit.Test

class RawJsonScannerTest {

    private fun scanner(json: String) = RawJsonScanner(json.byteInputStream())

    @Test
    fun `copies values as written`() {
        val scanner = scanner("""{"a": [1, {"b": "x\"}y"}, true], "c": -1.5e3, "d": null, "e": "日本"}""")
        scanner.beginObject()
        assertThat(scanner.nextName()).isEqualTo("a")
        assertThat(scanner.rawValue()).isEqualTo("""[1, {"b": "x\"}y"}, true]""")
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.nextName()).isEqualTo("c")
        assertThat(scanner.rawValue()).isEqualTo("-1.5e3")
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.nextName()).isEqualTo("d")
        assertThat(scanner.rawValue()).isEqualTo("null")
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.nextName()).isEqualTo("e")
        assertThat(scanner.rawValue()).isEqualTo("\"日本\"")
        assertThat(scanner.hasNext()).isFalse()
        scanner.endObject()
    }

    @Test
    fun `decodes strings`() {
        val scanner = scanner("""["a\nbあ\\\"/"]""")
        scanner.beginArray()
        assertThat(scanner.nextString()).isEqualTo("a\nbあ\\\"/")
        assertThat(scanner.hasNext()).isFalse()
    }

    @Test
    fun `skips nested values`() {
        val scanner = scanner("""[{"x": [[], {}, "]"]}, 2]""")
        scanner.beginArray()
        scanner.skipValue()
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.rawValue()).isEqualTo("2")
        assertThat(scanner.hasNext()).isFalse()
        scanner.endArray()
    }

    @Test
    fun `values longer than the buffer`() {
        val long = "あ".repeat(200_000)
        val scanner = scanner("""{"v": ["$long", "\"$long"]}""")
        scanner.beginObject()
        scanner.nextName()
        assertThat(scanner.rawValue()).isEqualTo("""["$long", "\"$long"]""")
    }

    @Test
    fun `unquotes strings`() {
        assertThat(RawJsonScanner.unquote("\"plain\"")).isEqualTo("plain")
        assertThat(RawJsonScanner.unquote("\"a\\\"b\"")).isEqualTo("a\"b")
        assertThat(RawJsonScanner.unquote("12")).isNull()
    }

    @Test
    fun `unquotes UTF-8 bytes with escapes`() {
        val raw = "\"日本\\u8a9e\\ud83d\\ude00\\/\\t\"".encodeToByteArray()
        assertThat(RawJsonScanner.unquote(raw, 0, raw.size)).isEqualTo("日本語\uD83D\uDE00/\t")
        val framed = "x\"語\"y".encodeToByteArray()
        assertThat(RawJsonScanner.unquote(framed, 1, framed.size - 1)).isEqualTo("語")
        assertThat(RawJsonScanner.unquote(framed, 0, framed.size)).isNull()
    }

    @Test
    fun `hands out raw names and copies values into a buffer`() {
        val scanner = scanner("""{"expression" : "食べる", "${'$'}types": {"a": 1}, "glossary": ["x", {"y": "}"}]}""")
        val out = ByteBuilder(4)
        scanner.beginObject()
        assertThat(scanner.nextRawName().let { it.decode(0, it.size) }).isEqualTo("expression")
        scanner.copyValue(out)
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.nextRawName().let { it.decode(0, it.size) }).isEqualTo("${'$'}types")
        scanner.skipValue()
        assertThat(scanner.hasNext()).isTrue()
        assertThat(scanner.nextRawName().let { it.decode(0, it.size) }).isEqualTo("glossary")
        scanner.copyValue(out)
        assertThat(scanner.hasNext()).isFalse()
        scanner.endObject()
        assertThat(out.decode(0, out.size)).isEqualTo("\"食べる\"[\"x\", {\"y\": \"}\"}]")
    }

    /** The read buffer is 64 KiB; escapes and multi-byte characters that straddle its end must survive. */
    @Test
    fun `escapes and characters across the read buffer boundary`() {
        for (shift in 0..3) {
            val prefix = "a".repeat((1 shl 16) - 3 - shift)
            val value = "\"$prefix\\\"語\\u00e9\""
            val scanner = scanner("[$value, 1]")
            scanner.beginArray()
            assertThat(scanner.rawValue()).isEqualTo(value)
            assertThat(scanner.hasNext()).isTrue()
            assertThat(scanner.rawValue()).isEqualTo("1")
            assertThat(scanner.hasNext()).isFalse()
        }
    }

    @Test
    fun `reports malformed input`() {
        val scanner = scanner("""{"a": [1, 2""")
        scanner.beginObject()
        scanner.nextName()
        assertThrows(EOFException::class.java) { scanner.skipValue() }
        assertThrows(JsonScanException::class.java) { scanner("[1]").beginObject() }
    }

    @Test
    fun `byte builder grows and compares ranges`() {
        val builder = ByteBuilder(1)
        repeat(1000) { builder.append('x'.code.toByte()) }
        builder.append("日本".encodeToByteArray())
        assertThat(builder.size).isEqualTo(1006)
        assertThat(builder.contentEquals(1000, 1006, "日本".encodeToByteArray())).isTrue()
        assertThat(builder.contentEquals(999, 1006, "日本".encodeToByteArray())).isFalse()
        assertThat(builder.contentEquals(1000, 1006, "日曜".encodeToByteArray())).isFalse()
        builder.clear()
        assertThat(builder.size).isEqualTo(0)
    }
}
