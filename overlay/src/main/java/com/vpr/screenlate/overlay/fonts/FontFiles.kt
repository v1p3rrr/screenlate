package com.vpr.screenlate.overlay.fonts

import java.nio.ByteBuffer
import java.nio.charset.Charset

/** Recognizes font files and reads their family name. */
object FontFiles {
    enum class Format(val extension: String, val mimeType: String) {
        TRUETYPE("ttf", "font/ttf"),
        OPENTYPE("otf", "font/otf"),
        COLLECTION("ttc", "font/collection"),
        WOFF("woff", "font/woff"),
        WOFF2("woff2", "font/woff2"),
    }

    /** The format from the file's first bytes; null if it is not a font. */
    fun format(bytes: ByteArray): Format? {
        if (bytes.size < 12) return null
        return when (tag(bytes, 0)) {
            "\u0000\u0001\u0000\u0000", "true" -> Format.TRUETYPE
            "OTTO" -> Format.OPENTYPE
            "ttcf" -> Format.COLLECTION
            "wOFF" -> Format.WOFF
            "wOF2" -> Format.WOFF2
            else -> null
        }
    }

    fun formatOf(fileName: String): Format? = Format.entries.firstOrNull { fileName.endsWith(".${it.extension}") }

    /** Whether [name] is a plain file name as the app gives font files: no path, one extension. */
    fun isFileName(name: String): Boolean = FILE_NAME.matches(name)

    /**
     * The family name from the `name` table (the typographic family if present), in English when the font has it.
     * For a collection, the first font's. Null for compressed web fonts or a damaged table.
     */
    fun family(bytes: ByteArray): String? = family(ByteBuffer.wrap(bytes))

    /** [family] of a font in [buffer], such as a mapped file, reading only the tables it needs. */
    fun family(buffer: ByteBuffer): String? = runCatching {
        val font = if (tag(buffer, 0) == "ttcf") buffer.getInt(12) else 0
        val tables = buffer.getShort(font + 4).toInt() and 0xFFFF
        val name = (0 until tables)
            .map { font + 12 + 16 * it }
            .firstOrNull { tag(buffer, it) == "name" }
            ?.let { buffer.getInt(it + 8) }
            ?: return null
        familyFromNameTable(buffer, name)
    }.getOrNull()

    private fun familyFromNameTable(buffer: ByteBuffer, table: Int): String? {
        val count = buffer.getShort(table + 2).toInt() and 0xFFFF
        val strings = table + (buffer.getShort(table + 4).toInt() and 0xFFFF)
        data class Record(val platform: Int, val encoding: Int, val language: Int, val nameId: Int, val length: Int, val offset: Int)
        val records = (0 until count).map { index ->
            val at = table + 6 + 12 * index
            fun u16(offset: Int) = buffer.getShort(at + offset).toInt() and 0xFFFF
            Record(u16(0), u16(2), u16(4), u16(6), u16(8), u16(10))
        }
        for (nameId in listOf(TYPOGRAPHIC_FAMILY, FAMILY)) {
            val candidates = records.filter { it.nameId == nameId }
            val best = candidates.firstOrNull { it.platform == WINDOWS && it.language == ENGLISH_US }
                ?: candidates.firstOrNull { it.platform == WINDOWS }
                ?: candidates.firstOrNull { it.platform == MACINTOSH && it.encoding == 0 }
                ?: continue
            val start = strings + best.offset
            val bytes = ByteArray(best.length) { buffer.get(start + it) }
            val charset = if (best.platform == WINDOWS) Charsets.UTF_16BE else Charset.forName("ISO-8859-1")
            // Control characters would end the CSS string the name goes into.
            val family = String(bytes, charset).filterNot { it.isISOControl() }.trim()
            if (family.isNotEmpty()) return family
        }
        return null
    }

    private fun tag(bytes: ByteArray, offset: Int): String =
        String(CharArray(4) { (bytes[offset + it].toInt() and 0xFF).toChar() })

    private fun tag(buffer: ByteBuffer, offset: Int): String =
        String(CharArray(4) { (buffer.get(offset + it).toInt() and 0xFF).toChar() })

    private val FILE_NAME = Regex("[A-Za-z0-9_-]+\\.[a-z0-9]+")
    private const val FAMILY = 1
    private const val TYPOGRAPHIC_FAMILY = 16
    private const val MACINTOSH = 1
    private const val WINDOWS = 3
    private const val ENGLISH_US = 0x409
}
