package com.vpr.screenlate.overlay.fonts

import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.roundToInt

/** Recognizes font files and reads their names and weights. */
object FontFiles {
    /**
     * Formats the page can load. [web] formats (compressed for websites) are no longer added from files, since Android
     * cannot preview them; fonts added before still load.
     */
    enum class Format(val extension: String, val mimeType: String, val web: Boolean = false) {
        TRUETYPE("ttf", "font/ttf"),
        OPENTYPE("otf", "font/otf"),
        COLLECTION("ttc", "font/collection"),
        WOFF("woff", "font/woff", web = true),
        WOFF2("woff2", "font/woff2", web = true),
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
     * What a font's tables say about it.
     *
     * @param style the typographic subfamily, such as "Bold" or "Regular"; null if the font names none.
     * @param weightRange the range of a variable font's weight axis as CSS `font-weight`, e.g. "100 900"; null for a
     *     font with one weight.
     * @param weightClass the weight the font declares for itself (`OS/2`), 100 to 1000; null if it declares none.
     */
    data class Description(val family: String, val style: String?, val weightRange: String?, val weightClass: Int? = null) {
        /** The family with the style unless it is the regular one: "Noto Serif JP", "Noto Serif JP Bold". */
        val displayName: String
            get() = if (style == null || style.lowercase() in REGULAR_STYLES) family else "$family $style"

        /** CSS `font-weight` of the file: the variable range, or its own weight. */
        val weight: String
            get() = weightRange ?: (weightClass ?: NORMAL_WEIGHT).toString()
    }

    /** The family name from the `name` table (the typographic family if present), in English when the font has it. */
    fun family(bytes: ByteArray): String? = describe(ByteBuffer.wrap(bytes))?.family

    /**
     * [Description] of font [index] of a collection, or of the font in [buffer] (such as a mapped file), reading only
     * the tables it needs. Null for compressed web fonts or damaged tables.
     */
    fun describe(buffer: ByteBuffer, index: Int = 0): Description? = runCatching {
        val font = if (tag(buffer, 0) == "ttcf") {
            if (index >= buffer.getInt(8)) return null
            buffer.getInt(12 + 4 * index)
        } else {
            0
        }
        val count = buffer.getShort(font + 4).toInt() and 0xFFFF
        val tables = (0 until count).associate { tag(buffer, font + 12 + 16 * it) to buffer.getInt(font + 12 + 16 * it + 8) }
        val name = tables["name"] ?: return null
        val records = nameRecords(buffer, name)
        // The typographic names go together; the legacy ones fold weights beyond bold into the family.
        val typographic = nameString(buffer, name, records, TYPOGRAPHIC_FAMILY)
        val family = typographic ?: nameString(buffer, name, records, FAMILY) ?: return null
        val style = if (typographic != null) {
            nameString(buffer, name, records, TYPOGRAPHIC_SUBFAMILY) ?: nameString(buffer, name, records, SUBFAMILY)
        } else {
            nameString(buffer, name, records, SUBFAMILY)
        }
        val weightClass = tables["OS/2"]?.let { u16(buffer, it + 4) }?.takeIf { it in 100..1000 }
        Description(family, style, tables["fvar"]?.let { weightAxis(buffer, it) }, weightClass)
    }.getOrNull()

    private data class NameRecord(val platform: Int, val encoding: Int, val language: Int, val nameId: Int, val length: Int, val offset: Int)

    private fun nameRecords(buffer: ByteBuffer, table: Int): List<NameRecord> {
        val count = u16(buffer, table + 2)
        return (0 until count).map { index ->
            val at = table + 6 + 12 * index
            NameRecord(u16(buffer, at), u16(buffer, at + 2), u16(buffer, at + 4), u16(buffer, at + 6), u16(buffer, at + 8), u16(buffer, at + 10))
        }
    }

    private fun nameString(buffer: ByteBuffer, table: Int, records: List<NameRecord>, nameId: Int): String? {
        val candidates = records.filter { it.nameId == nameId }
        val best = candidates.firstOrNull { it.platform == WINDOWS && it.language == ENGLISH_US }
            ?: candidates.firstOrNull { it.platform == WINDOWS }
            ?: candidates.firstOrNull { it.platform == MACINTOSH && it.encoding == 0 }
            ?: return null
        val start = table + u16(buffer, table + 4) + best.offset
        val bytes = ByteArray(best.length) { buffer.get(start + it) }
        val charset = if (best.platform == WINDOWS) Charsets.UTF_16BE else Charset.forName("ISO-8859-1")
        // Control characters would end the CSS string the name goes into.
        return String(bytes, charset).filterNot { it.isISOControl() }.trim().takeIf { it.isNotEmpty() }
    }

    /** The `wght` axis of an `fvar` table as "min max"; null without one or when it has a single value. */
    private fun weightAxis(buffer: ByteBuffer, table: Int): String? {
        val axes = table + u16(buffer, table + 4)
        val count = u16(buffer, table + 8)
        val size = u16(buffer, table + 10)
        for (index in 0 until count) {
            val at = axes + size * index
            if (tag(buffer, at) != "wght") continue
            val min = (buffer.getInt(at + 4) / FIXED_ONE).roundToInt().coerceIn(1, 1000)
            val max = (buffer.getInt(at + 12) / FIXED_ONE).roundToInt().coerceIn(1, 1000)
            return if (min < max) "$min $max" else null
        }
        return null
    }

    private fun u16(buffer: ByteBuffer, at: Int): Int = buffer.getShort(at).toInt() and 0xFFFF

    private fun tag(bytes: ByteArray, offset: Int): String =
        String(CharArray(4) { (bytes[offset + it].toInt() and 0xFF).toChar() })

    private fun tag(buffer: ByteBuffer, offset: Int): String =
        String(CharArray(4) { (buffer.get(offset + it).toInt() and 0xFF).toChar() })

    private val REGULAR_STYLES = setOf("regular", "normal", "roman", "book")
    private val FILE_NAME = Regex("[A-Za-z0-9_-]+\\.[a-z0-9]+")
    private const val FAMILY = 1
    private const val SUBFAMILY = 2
    private const val TYPOGRAPHIC_FAMILY = 16
    private const val TYPOGRAPHIC_SUBFAMILY = 17
    private const val FIXED_ONE = 65536.0
    private const val NORMAL_WEIGHT = 400
    private const val MACINTOSH = 1
    private const val WINDOWS = 3
    private const val ENGLISH_US = 0x409
}
