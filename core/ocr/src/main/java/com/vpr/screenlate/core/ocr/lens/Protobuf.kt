package com.vpr.screenlate.core.ocr.lens

import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf wire-format writer. Covers only what the Lens request needs: varints and length-delimited fields.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter = apply {
        writeTag(field, WIRE_VARINT)
        writeRawVarint(value)
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeRawVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.encodeToByteArray())

    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeTag(field: Int, wireType: Int) = writeRawVarint(((field shl 3) or wireType).toLong())

    private fun writeRawVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
}

/**
 * Minimal protobuf wire-format reader. Unknown fields are skipped, so it tolerates schema additions on the server.
 */
internal class ProtoReader(private val buffer: ByteArray, private var position: Int = 0, private val limit: Int = buffer.size) {

    /** Calls [onField] for every field; the callback must consume the value through [Field]. */
    fun forEachField(onField: (Field) -> Unit) {
        while (position < limit) {
            val tag = readRawVarint()
            val field = Field(number = (tag ushr 3).toInt(), wireType = (tag and 7).toInt())
            onField(field)
            if (!field.consumed) skip(field.wireType)
        }
    }

    inner class Field(val number: Int, val wireType: Int) {
        internal var consumed = false
            private set

        fun varint(): Long {
            check(wireType == WIRE_VARINT) { "Field $number is not a varint" }
            consumed = true
            return readRawVarint()
        }

        fun float(): Float {
            check(wireType == WIRE_FIXED32) { "Field $number is not fixed32" }
            consumed = true
            val bits = (buffer[position].toInt() and 0xFF) or
                ((buffer[position + 1].toInt() and 0xFF) shl 8) or
                ((buffer[position + 2].toInt() and 0xFF) shl 16) or
                ((buffer[position + 3].toInt() and 0xFF) shl 24)
            position += 4
            return Float.fromBits(bits)
        }

        fun string(): String {
            val (start, end) = lengthDelimited()
            return buffer.decodeToString(start, end)
        }

        fun bytes(): ByteArray {
            val (start, end) = lengthDelimited()
            return buffer.copyOfRange(start, end)
        }

        fun message(): ProtoReader {
            val (start, end) = lengthDelimited()
            return ProtoReader(buffer, start, end)
        }

        private fun lengthDelimited(): Pair<Int, Int> {
            check(wireType == WIRE_LENGTH_DELIMITED) { "Field $number is not length-delimited" }
            consumed = true
            val length = readRawVarint().toInt()
            val start = position
            position += length
            check(position <= limit) { "Truncated field $number" }
            return start to position
        }
    }

    private fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            check(position < limit) { "Truncated varint" }
            val b = buffer[position++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        error("Malformed varint")
    }

    private fun skip(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readRawVarint()
            WIRE_FIXED64 -> position += 8
            WIRE_LENGTH_DELIMITED -> {
                // Read the length first: `position += readRawVarint()` would use the position before the varint.
                val length = readRawVarint().toInt()
                position += length
            }
            WIRE_FIXED32 -> position += 4
            else -> error("Unsupported wire type $wireType")
        }
        check(position <= limit) { "Truncated message" }
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5
