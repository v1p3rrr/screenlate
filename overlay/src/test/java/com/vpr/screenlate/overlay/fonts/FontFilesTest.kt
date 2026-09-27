package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Test

class FontFilesTest {

    /** A name table entry: platform, language, name id, text. */
    private data class Name(val platform: Int, val language: Int, val nameId: Int, val text: String)

    /** A font with only a `name` table; [base] is where the font starts in the file (for collections). */
    private fun font(names: List<Name>, base: Int = 0, version: Int = 0x00010000): ByteArray {
        val strings = names.map { if (it.platform == 3) it.text.toByteArray(Charsets.UTF_16BE) else it.text.toByteArray(Charsets.ISO_8859_1) }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(version)
            writeShort(1)
            writeShort(0)
            writeShort(0)
            writeShort(0)
            writeBytes("name")
            writeInt(0)
            writeInt(base + 28)
            writeInt(0)
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
}
