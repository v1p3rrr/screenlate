package com.vpr.screenlate.diagnostics

import kotlin.math.roundToLong

/**
 * One diagnostics record as a JSON object on one line: `t` (wall clock, ms), `up` (elapsed since boot, ms), `e` (the
 * event) and the fields. Values are numbers, booleans, enums, identifiers, lists and maps of them; strings are cut to
 * [MAX_STRING] characters, as nothing longer than an identifier belongs here (never recognized text or words).
 */
internal object DiagJson {
    const val MAX_STRING = 160

    fun line(wallMs: Long, upMs: Long, event: String, fields: Array<out Pair<String, Any?>>): String =
        buildString(64 + fields.size * 16) {
            append("{\"t\":").append(wallMs).append(",\"up\":").append(upMs).append(",\"e\":")
            string(event)
            for ((key, value) in fields) {
                if (value == null) continue
                append(',')
                string(key)
                append(':')
                value(value)
            }
            append('}')
        }

    /** [value] as JSON, for fields that hold lists or maps built elsewhere. */
    fun encode(value: Any?): String = buildString { if (value == null) append("null") else value(value) }

    private fun StringBuilder.value(value: Any) {
        when (value) {
            is Boolean, is Int, is Long, is Short, is Byte -> append(value)
            is Float -> number(value.toDouble())
            is Double -> number(value)
            is Enum<*> -> string(value.name)
            is Map<*, *> -> {
                append('{')
                var first = true
                for ((key, item) in value) {
                    if (item == null) continue
                    if (!first) append(',')
                    first = false
                    string(key.toString())
                    append(':')
                    value(item)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    if (item == null) append("null") else value(item)
                }
                append(']')
            }
            is IntArray -> value(value.toList())
            is LongArray -> value(value.toList())
            else -> string(value.toString())
        }
    }

    /** Two decimals at most; NaN and infinities, which JSON lacks, become null. */
    private fun StringBuilder.number(value: Double) {
        when {
            !value.isFinite() -> append("null")
            value == value.roundToLong().toDouble() -> append(value.roundToLong())
            else -> append((value * 100).roundToLong() / 100.0)
        }
    }

    private fun StringBuilder.string(text: String) {
        append('"')
        val end = minOf(text.length, MAX_STRING)
        for (i in 0 until end) {
            when (val c = text[i]) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        if (text.length > MAX_STRING) append('…')
        append('"')
    }
}
