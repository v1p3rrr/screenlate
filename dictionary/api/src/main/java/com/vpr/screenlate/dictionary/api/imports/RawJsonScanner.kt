package com.vpr.screenlate.dictionary.api.imports

import java.io.EOFException
import java.io.IOException
import java.io.Reader

class JsonScanException(message: String) : IOException(message)

/**
 * A minimal streaming JSON reader that hands out values as raw JSON text without building a tree or
 * re-serializing them. Yomitan collection exports are gigabytes of rows whose values go into dictionary banks
 * unchanged, so copying their text is all that is needed.
 *
 * Only well-formed JSON is supported; errors are reported, not recovered from.
 */
internal class RawJsonScanner(private val reader: Reader) {
    private val buffer = CharArray(BUFFER_SIZE)
    private var pos = 0
    private var limit = 0

    fun beginObject() = expect('{')

    fun endObject() = expect('}')

    fun beginArray() = expect('[')

    fun endArray() = expect(']')

    /** Consumes the comma before the next element or member; false at the end of the object or array. */
    fun hasNext(): Boolean {
        var c = peek()
        if (c == ',') {
            pos++
            c = peek()
        }
        return c != '}' && c != ']'
    }

    fun nextName(): String {
        val name = nextString()
        expect(':')
        return name
    }

    fun nextString(): String {
        if (peek() != '"') throw JsonScanException("Expected a string")
        pos++
        val out = StringBuilder()
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            var i = pos
            while (i < limit && buffer[i] != '"' && buffer[i] != '\\') i++
            out.appendRange(buffer, pos, i)
            pos = i
            if (i == limit) continue
            val c = buffer[pos++]
            if (c == '"') return out.toString()
            out.append(unescape())
        }
    }

    /** The next value as JSON text, exactly as written. */
    fun rawValue(): String {
        val out = StringBuilder()
        copyValue(out)
        return out.toString()
    }

    fun skipValue() = copyValue(null)

    /** The next non-whitespace character without consuming it. */
    fun peek(): Char {
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            val c = buffer[pos]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++ else return c
        }
    }

    private fun expect(expected: Char) {
        val c = peek()
        if (c != expected) throw JsonScanException("Expected '$expected' but found '$c'")
        pos++
    }

    private fun fill(): Boolean {
        val read = reader.read(buffer, 0, buffer.size)
        pos = 0
        limit = read.coerceAtLeast(0)
        return read > 0
    }

    private fun copyValue(out: StringBuilder?) {
        when (peek()) {
            '"' -> copyString(out)
            '{', '[' -> copyContainer(out)
            else -> copyLiteral(out)
        }
    }

    /** A string at [pos] including its quotes and escapes. */
    private fun copyString(out: StringBuilder?) {
        pos++
        out?.append('"')
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            var i = pos
            while (i < limit && buffer[i] != '"' && buffer[i] != '\\') i++
            out?.appendRange(buffer, pos, i)
            pos = i
            if (i == limit) continue
            val c = buffer[pos++]
            out?.append(c)
            if (c == '"') return
            // An escape: its next character is copied as it is, so \" does not end the string.
            if (pos >= limit && !fill()) throw EOFException()
            val escaped = buffer[pos++]
            out?.append(escaped)
        }
    }

    private fun copyContainer(out: StringBuilder?) {
        var depth = 0
        while (true) {
            if (pos >= limit && !fill()) throw EOFException()
            var i = pos
            while (i < limit) {
                val c = buffer[i]
                if (c == '"' || c == '{' || c == '[' || c == '}' || c == ']') break
                i++
            }
            out?.appendRange(buffer, pos, i)
            pos = i
            if (i == limit) continue
            when (buffer[pos]) {
                '"' -> copyString(out)
                '{', '[' -> {
                    depth++
                    out?.append(buffer[pos])
                    pos++
                }
                else -> {
                    depth--
                    out?.append(buffer[pos])
                    pos++
                    if (depth == 0) return
                }
            }
        }
    }

    /** A number, `true`, `false` or `null`. */
    private fun copyLiteral(out: StringBuilder?) {
        while (true) {
            if (pos >= limit && !fill()) return
            val c = buffer[pos]
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t') return
            out?.append(c)
            pos++
        }
    }

    private fun unescape(): Char {
        if (pos >= limit && !fill()) throw EOFException()
        return when (val c = buffer[pos++]) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            'b' -> '\b'
            'f' -> '\u000C'
            'u' -> {
                var code = 0
                repeat(4) {
                    if (pos >= limit && !fill()) throw EOFException()
                    code = code * 16 + Character.digit(buffer[pos++], 16).also {
                        if (it < 0) throw JsonScanException("Bad unicode escape")
                    }
                }
                code.toChar()
            }
            else -> c
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1 shl 16

        /** The text of a JSON string value; fast when it has no escapes. */
        fun unquote(raw: String): String? {
            if (raw.length < 2 || raw[0] != '"') return null
            val inner = raw.substring(1, raw.length - 1)
            if ('\\' !in inner) return inner
            return RawJsonScanner(raw.reader()).nextString()
        }
    }
}
