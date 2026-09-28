package com.vpr.screenlate.dictionary.api.imports

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class JsonScanException(message: String) : IOException(message)

/**
 * A minimal streaming JSON reader over UTF-8 bytes that hands out values as raw JSON without building a tree,
 * decoding or re-serializing them. Yomitan collection exports are gigabytes of rows whose values go into
 * dictionary banks unchanged, so copying their bytes is all that is needed. Structural characters, quotes and
 * backslashes are ASCII and never occur inside multi-byte UTF-8 sequences, so the scan needs no decoding.
 *
 * Only well-formed JSON is supported; errors are reported, not recovered from.
 */
internal class RawJsonScanner(private val input: InputStream) {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var pos = 0
    private var limit = 0

    /** Bytes of the input before the current buffer. */
    private var consumed = 0L
    private val scratch = ByteBuilder(64)

    /** Where [fill] saves the buffer's bytes of a value being copied, and where in the buffer that value starts. */
    private var capturing: ByteBuilder? = null
    private var captureStart = 0

    fun beginObject() = expect(OBJECT_START)

    fun endObject() = expect(OBJECT_END)

    fun beginArray() = expect(ARRAY_START)

    fun endArray() = expect(ARRAY_END)

    /** Consumes the comma before the next element or member; false at the end of the object or array. */
    fun hasNext(): Boolean {
        var c = peek()
        if (c == COMMA) {
            pos++
            c = peek()
        }
        return c != OBJECT_END && c != ARRAY_END
    }

    fun nextName(): String {
        val name = nextString()
        expect(COLON)
        return name
    }

    /**
     * The next member name as raw bytes (without quotes, escapes left as written) in a buffer that the next call
     * reuses. For matching names without allocating a string per member.
     */
    fun nextRawName(): ByteBuilder {
        if (peek() != QUOTE) throw JsonScanException("Expected a name")
        scratch.clear()
        capture(scratch, pos + 1) {
            skipString()
            pos - 1
        }
        expect(COLON)
        return scratch
    }

    fun nextString(): String {
        if (peek() != QUOTE) throw JsonScanException("Expected a string")
        val raw = ByteBuilder(32)
        copyAny(raw)
        return unquote(raw.array, 0, raw.size) ?: throw JsonScanException("Bad string")
    }

    /** The next value as JSON text, exactly as written. */
    fun rawValue(): String {
        val out = ByteBuilder(64)
        copyAny(out)
        return out.decode(0, out.size)
    }

    /** Appends the next value's bytes, exactly as written, to [out]. */
    fun copyValue(out: ByteBuilder) = copyAny(out)

    fun skipValue() = copyAny(null)

    /** Bytes of the input read up to the current position. */
    val offset: Long get() = consumed + pos

    /** The next non-whitespace byte without consuming it. */
    fun peek(): Byte {
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            val c = buffer[pos]
            if (c == SPACE || c == NEWLINE || c == RETURN || c == TAB) pos++ else return c
        }
    }

    private fun expect(expected: Byte) {
        val c = peek()
        if (c != expected) throw JsonScanException("Expected '${expected.toInt().toChar()}' but found '${c.toInt().toChar()}'")
        pos++
    }

    /** Refills the buffer; bytes of a value being captured are appended to the capture first. */
    private fun fill(): Boolean {
        capturing?.let {
            it.append(buffer, captureStart, limit - captureStart)
            captureStart = 0
        }
        consumed += limit
        val read = input.read(buffer, 0, buffer.size)
        pos = 0
        limit = read.coerceAtLeast(0)
        return read > 0
    }

    /**
     * Runs [skip] and appends to [out] the bytes from [from] up to the position [skip] returns, including those
     * that [fill] moved out of the buffer meanwhile. Values are copied in whole buffer runs, not piece by piece.
     */
    private inline fun capture(out: ByteBuilder?, from: Int, skip: () -> Int) {
        if (out == null) {
            skip()
            return
        }
        capturing = out
        captureStart = from
        try {
            val end = skip()
            out.append(buffer, captureStart, end - captureStart)
        } finally {
            capturing = null
        }
    }

    private fun copyAny(out: ByteBuilder?) {
        val first = peek()
        capture(out, pos) {
            when (first) {
                QUOTE -> skipString()
                OBJECT_START, ARRAY_START -> skipContainer()
                else -> skipLiteral()
            }
            pos
        }
    }

    /** Moves past a string at [pos], escapes included. */
    private fun skipString() {
        pos++
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            var i = pos
            while (i < limit) {
                val c = buffer[i]
                if (c == QUOTE || c == BACKSLASH) break
                i++
            }
            pos = i
            if (i == limit) continue
            if (buffer[pos++] == QUOTE) return
            // An escape: its next byte is skipped as well, so \" does not end the string.
            if (pos >= limit && !fill()) throw EOFException()
            pos++
        }
    }

    private fun skipContainer() {
        var depth = 0
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            var i = pos
            while (i < limit) {
                val c = buffer[i]
                if (c == QUOTE || c == OBJECT_START || c == ARRAY_START || c == OBJECT_END || c == ARRAY_END) break
                i++
            }
            pos = i
            if (i == limit) continue
            when (buffer[pos]) {
                QUOTE -> skipString()
                OBJECT_START, ARRAY_START -> {
                    depth++
                    pos++
                }
                else -> {
                    depth--
                    pos++
                    if (depth == 0) return
                }
            }
        }
    }

    /** A number, `true`, `false` or `null`. */
    private fun skipLiteral() {
        while (true) {
            if (pos >= limit && !fill()) return
            val c = buffer[pos]
            if (c == COMMA || c == OBJECT_END || c == ARRAY_END || c == SPACE || c == NEWLINE || c == RETURN || c == TAB) return
            pos++
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1 shl 16
        private const val QUOTE = '"'.code.toByte()
        private const val BACKSLASH = '\\'.code.toByte()
        private const val OBJECT_START = '{'.code.toByte()
        private const val OBJECT_END = '}'.code.toByte()
        private const val ARRAY_START = '['.code.toByte()
        private const val ARRAY_END = ']'.code.toByte()
        private const val COMMA = ','.code.toByte()
        private const val COLON = ':'.code.toByte()
        private const val SPACE = ' '.code.toByte()
        private const val NEWLINE = '\n'.code.toByte()
        private const val RETURN = '\r'.code.toByte()
        private const val TAB = '\t'.code.toByte()

        /** The text of a JSON string value written as UTF-8 in `bytes[from until to]`, quotes included. */
        fun unquote(bytes: ByteArray, from: Int, to: Int): String? {
            if (to - from < 2 || bytes[from] != QUOTE || bytes[to - 1] != QUOTE) return null
            val inner = bytes.decodeToString(from + 1, to - 1)
            return if ('\\' in inner) unescape(inner) else inner
        }

        /** The text of a JSON string value; fast when it has no escapes. */
        fun unquote(raw: String): String? {
            if (raw.length < 2 || raw[0] != '"' || raw[raw.length - 1] != '"') return null
            val inner = raw.substring(1, raw.length - 1)
            return if ('\\' in inner) unescape(inner) else inner
        }

        private fun unescape(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i++]
                if (c != '\\') {
                    out.append(c)
                    continue
                }
                if (i >= text.length) throw JsonScanException("Bad escape")
                when (val e = text[i++]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        if (i + 4 > text.length) throw JsonScanException("Bad unicode escape")
                        var code = 0
                        repeat(4) {
                            val digit = Character.digit(text[i++], 16)
                            if (digit < 0) throw JsonScanException("Bad unicode escape")
                            code = code * 16 + digit
                        }
                        out.append(code.toChar())
                    }
                    else -> out.append(e)
                }
            }
            return out.toString()
        }
    }
}

/** A growable byte array; cheaper than [java.io.ByteArrayOutputStream] for many small appends. */
internal class ByteBuilder(capacity: Int) {
    var array = ByteArray(capacity)
        private set
    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun append(byte: Byte) {
        if (size == array.size) grow(size + 1)
        array[size++] = byte
    }

    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (size + length > array.size) grow(size + length)
        System.arraycopy(bytes, offset, array, size, length)
        size += length
    }

    fun append(bytes: ByteArray) = append(bytes, 0, bytes.size)

    fun decode(from: Int, to: Int): String = array.decodeToString(from, to)

    /** Whether `array[from until to]` equals [other]. */
    fun contentEquals(from: Int, to: Int, other: ByteArray): Boolean {
        if (to - from != other.size) return false
        for (i in other.indices) if (array[from + i] != other[i]) return false
        return true
    }

    private fun grow(needed: Int) {
        var capacity = array.size.coerceAtLeast(16)
        while (capacity < needed) capacity = if (capacity > Int.MAX_VALUE / 2) Int.MAX_VALUE - 8 else capacity * 2
        array = array.copyOf(capacity)
    }
}
