package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RawJsonScannerTest {

    private fun scanner(json: String) = RawJsonScanner(json.reader())

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
}
