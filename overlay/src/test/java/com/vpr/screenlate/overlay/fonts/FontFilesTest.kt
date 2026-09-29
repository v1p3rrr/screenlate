package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import org.junit.Test

class FontFilesTest {

    /** A name table entry: platform, language, name id, text. */
    private data class Name(val platform: Int, val language: Int, val nameId: Int, val text: String)

    /** A font of [tables] (tag and bytes, in order); [base] is where the font starts in the file (for collections). */
    private fun sfnt(tables: List<Pair<String, ByteArray>>, base: Int = 0, version: Int = 0x00010000): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(version)
            writeShort(tables.size)
            writeShort(0)
            writeShort(0)
            writeShort(0)
            var offset = base + 12 + 16 * tables.size
            tables.forEach { (tag, data) ->
                writeBytes(tag)
                writeInt(0)
                writeInt(offset)
                writeInt(data.size)
                offset += data.size
            }
            tables.forEach { write(it.second) }
        }
        return bytes.toByteArray()
    }

    private fun nameTable(names: List<Name>): ByteArray {
        val strings = names.map { if (it.platform == 3) it.text.toByteArray(Charsets.UTF_16BE) else it.text.toByteArray(Charsets.ISO_8859_1) }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeShort(0)
            writeShort(names.size)
            writeShort(6 + 12 * names.size)
            var offset = 0
            names.forEachIndexed { index, name ->
                writeShort(name.platform)
                writeShort(if (name.platform == 3) 1 else 0)
                writeShort(name.language)
                writeShort(name.nameId)
                writeShort(strings[index].size)
                writeShort(offset)
                offset += strings[index].size
            }
            strings.forEach { write(it) }
        }
        return bytes.toByteArray()
    }

    /** An `OS/2` table up to its weight class. */
    private fun os2(weight: Int): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeShort(4)
            writeShort(500)
            writeShort(weight)
        }
    }.toByteArray()

    /** An `fvar` table with [axes]: tag, minimum and maximum. */
    private fun fvar(vararg axes: Triple<String, Double, Double>): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeShort(1)
            writeShort(0)
            writeShort(16)
            writeShort(2)
            writeShort(axes.size)
            writeShort(20)
            writeShort(0)
            writeShort(0)
            axes.forEach { (tag, min, max) ->
                writeBytes(tag)
                writeInt((min * 65536).toInt())
                writeInt((min * 65536).toInt())
                writeInt((max * 65536).toInt())
                writeShort(0)
                writeShort(256)
            }
        }
    }.toByteArray()

    /** A font with only a `name` table. */
    private fun font(names: List<Name>, base: Int = 0, version: Int = 0x00010000): ByteArray =
        sfnt(listOf("name" to nameTable(names)), base, version)

    private fun describe(bytes: ByteArray, index: Int = 0) = FontFiles.describe(ByteBuffer.wrap(bytes), index)

    @Test
    fun `recognizes font formats`() {
        assertThat(FontFiles.format(font(emptyList()))).isEqualTo(FontFiles.Format.TRUETYPE)
        assertThat(FontFiles.format(font(emptyList(), version = 0x4F54544F))).isEqualTo(FontFiles.Format.OPENTYPE)
        assertThat(FontFiles.format("wOF2xxxxxxxxxxxx".toByteArray())).isEqualTo(FontFiles.Format.WOFF2)
        assertThat(FontFiles.format("<html>not a font".toByteArray())).isNull()
        assertThat(FontFiles.format(ByteArray(4))).isNull()
        assertThat(FontFiles.formatOf("a-0.ttc")).isEqualTo(FontFiles.Format.COLLECTION)
    }

    @Test
    fun `prefers the english typographic family`() {
        val bytes = font(
            listOf(
                Name(3, 0x411, 1, "メイリオ"),
                Name(3, 0x409, 1, "Meiryo"),
                Name(3, 0x409, 16, "Meiryo Family"),
                Name(1, 0, 1, "Mac Name"),
            ),
        )
        assertThat(FontFiles.family(bytes)).isEqualTo("Meiryo Family")
    }

    @Test
    fun `falls back to other languages and platforms`() {
        assertThat(FontFiles.family(font(listOf(Name(3, 0x411, 1, "メイリオ"))))).isEqualTo("メイリオ")
        assertThat(FontFiles.family(font(listOf(Name(1, 0, 1, "Mac Name"))))).isEqualTo("Mac Name")
        assertThat(FontFiles.family(font(emptyList()))).isNull()
    }

    @Test
    fun `reads the first font of a collection`() {
        val header = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeBytes("ttcf")
                writeInt(0x00010000)
                writeInt(1)
                writeInt(16)
            }
        }.toByteArray()
        val bytes = header + font(listOf(Name(3, 0x409, 1, "Collection")), base = 16)
        assertThat(FontFiles.format(bytes)).isEqualTo(FontFiles.Format.COLLECTION)
        assertThat(FontFiles.family(bytes)).isEqualTo("Collection")
    }

    @Test
    fun `damaged tables give no name`() {
        assertThat(FontFiles.family(font(listOf(Name(3, 0x409, 1, "Cut"))).copyOf(40))).isNull()
    }

    @Test
    fun `reads the family from a buffer and drops control characters`() {
        val bytes = font(listOf(Name(3, 0x409, 1, "Line\nBreak Sans")))
        assertThat(describe(bytes)?.family).isEqualTo("LineBreak Sans")
        assertThat(FontFiles.family(bytes.copyOf(bytes.size - 4))).isNull()
    }

    @Test
    fun `names a file by its typographic family and style`() {
        val bold = sfnt(
            listOf(
                "OS/2" to os2(700),
                "name" to nameTable(
                    listOf(
                        Name(3, 0x409, 1, "Noto Serif JP Bold"),
                        Name(3, 0x409, 2, "Regular"),
                        Name(3, 0x409, 16, "Noto Serif JP"),
                        Name(3, 0x409, 17, "Bold"),
                    ),
                ),
            ),
        )
        val description = describe(bold)
        assertThat(description?.family).isEqualTo("Noto Serif JP")
        assertThat(description?.displayName).isEqualTo("Noto Serif JP Bold")
        assertThat(description?.weight).isEqualTo("700")
    }

    @Test
    fun `the legacy names go together and regular styles are left out`() {
        val light = font(listOf(Name(3, 0x409, 1, "Klee One Light"), Name(3, 0x409, 2, "Regular")))
        assertThat(describe(light)?.displayName).isEqualTo("Klee One Light")
        assertThat(describe(light)?.weight).isEqualTo("400")
        val book = font(listOf(Name(3, 0x409, 1, "Serif"), Name(3, 0x409, 2, "Book")))
        assertThat(describe(book)?.displayName).isEqualTo("Serif")
        val italic = font(listOf(Name(3, 0x409, 1, "Serif"), Name(3, 0x409, 2, "Italic")))
        assertThat(describe(italic)?.displayName).isEqualTo("Serif Italic")
    }

    @Test
    fun `a variable font gives its weight range`() {
        val names = "name" to nameTable(listOf(Name(3, 0x409, 1, "Noto Sans JP")))
        val variable = sfnt(listOf("OS/2" to os2(400), "fvar" to fvar(Triple("wdth", 75.0, 100.0), Triple("wght", 100.0, 900.0)), names))
        assertThat(describe(variable)?.weightRange).isEqualTo("100 900")
        assertThat(describe(variable)?.weight).isEqualTo("100 900")
        val widthOnly = sfnt(listOf("fvar" to fvar(Triple("wdth", 75.0, 100.0)), names))
        assertThat(describe(widthOnly)?.weightRange).isNull()
        val single = sfnt(listOf("fvar" to fvar(Triple("wght", 400.0, 400.0)), names))
        assertThat(describe(single)?.weightRange).isNull()
    }

    @Test
    fun `reads a font of a collection by index`() {
        val first = font(listOf(Name(3, 0x409, 1, "First")), base = 20)
        val second = font(listOf(Name(3, 0x409, 1, "Second")), base = 20 + first.size)
        val header = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeBytes("ttcf")
                writeInt(0x00010000)
                writeInt(2)
                writeInt(20)
                writeInt(20 + first.size)
            }
        }.toByteArray()
        val bytes = header + first + second
        assertThat(describe(bytes)?.family).isEqualTo("First")
        assertThat(describe(bytes, 1)?.family).isEqualTo("Second")
        assertThat(describe(bytes, 2)).isNull()
    }

    @Test
    fun `web formats are marked`() {
        assertThat(FontFiles.Format.entries.filter { it.web }).containsExactly(FontFiles.Format.WOFF, FontFiles.Format.WOFF2)
    }

    @Test
    fun `only plain file names are font files`() {
        assertThat(FontFiles.isFileName("file-1a2b3c4d-5e6f7a8b.ttf")).isTrue()
        assertThat(FontFiles.isFileName("noto-sans-jp-0.ttf")).isTrue()
        assertThat(FontFiles.isFileName("../databases/x.db")).isFalse()
        assertThat(FontFiles.isFileName("fonts.json.tmp")).isFalse()
        assertThat(FontFiles.isFileName("a b.ttf")).isFalse()
    }
}
