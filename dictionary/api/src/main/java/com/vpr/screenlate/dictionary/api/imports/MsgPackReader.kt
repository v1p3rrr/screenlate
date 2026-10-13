package com.vpr.screenlate.dictionary.api.imports

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * A streaming reader of the MessagePack types that wordfreq's word lists use: arrays, maps, strings, integers,
 * booleans and nil. Other types are skipped by [skip] and refused by the readers.
 */
internal class MsgPackReader(input: InputStream) {
    private val data = DataInputStream(input as? BufferedInputStream ?: BufferedInputStream(input))

    fun readArrayHeader(): Int {
        val type = data.readUnsignedByte()
        return when {
            type in 0x90..0x9f -> type and 0x0f
            type == 0xdc -> data.readUnsignedShort()
            type == 0xdd -> length(data.readInt())
            else -> throw unexpected(type, "an array")
        }
    }

    fun readString(): String {
        val type = data.readUnsignedByte()
        return string(type) ?: throw unexpected(type, "a string")
    }

    /** The next value as a string, a [Long], a [Boolean], null, a list or a map with string keys. */
    fun readValue(): Any? {
        val type = data.readUnsignedByte()
        string(type)?.let { return it }
        integer(type)?.let { return it }
        return when {
            type == NIL -> null
            type == FALSE -> false
            type == TRUE -> true
            type in 0x90..0x9f -> List(type and 0x0f) { readValue() }
            type == 0xdc -> List(data.readUnsignedShort()) { readValue() }
            type == 0xdd -> List(length(data.readInt())) { readValue() }
            type in 0x80..0x8f -> map(type and 0x0f)
            type == 0xde -> map(data.readUnsignedShort())
            type == 0xdf -> map(length(data.readInt()))
            else -> throw unexpected(type, "a value")
        }
    }

    fun skip() {
        val type = data.readUnsignedByte()
        when {
            type <= 0x7f || type >= 0xe0 || type == NIL || type == FALSE || type == TRUE -> Unit
            type in 0xa0..0xbf -> skipBytes(type and 0x1f)
            type in 0x90..0x9f -> repeat(type and 0x0f) { skip() }
            type in 0x80..0x8f -> repeat(2 * (type and 0x0f)) { skip() }
            else -> when (type) {
                0xcc, 0xd0 -> skipBytes(1)
                0xcd, 0xd1 -> skipBytes(2)
                0xce, 0xd2, 0xca -> skipBytes(4)
                0xcf, 0xd3, 0xcb -> skipBytes(8)
                0xd9, 0xc4 -> skipBytes(data.readUnsignedByte())
                0xda, 0xc5 -> skipBytes(data.readUnsignedShort())
                0xdb, 0xc6 -> skipBytes(length(data.readInt()))
                0xdc -> repeat(data.readUnsignedShort()) { skip() }
                0xdd -> repeat(length(data.readInt())) { skip() }
                0xde -> repeat(2 * data.readUnsignedShort()) { skip() }
                0xdf -> repeat(2 * length(data.readInt())) { skip() }
                else -> throw unexpected(type, "a known type")
            }
        }
    }

    private fun string(type: Int): String? {
        val length = when (type) {
            in 0xa0..0xbf -> type and 0x1f
            0xd9 -> data.readUnsignedByte()
            0xda -> data.readUnsignedShort()
            0xdb -> length(data.readInt())
            else -> return null
        }
        return ByteArray(length).also(data::readFully).decodeToString()
    }

    private fun integer(type: Int): Long? = when {
        type <= 0x7f -> type.toLong()
        type >= 0xe0 -> (type - 0x100).toLong()
        type == 0xcc -> data.readUnsignedByte().toLong()
        type == 0xcd -> data.readUnsignedShort().toLong()
        type == 0xce -> data.readInt().toLong() and 0xffffffffL
        type == 0xcf -> data.readLong()
        type == 0xd0 -> data.readByte().toLong()
        type == 0xd1 -> data.readShort().toLong()
        type == 0xd2 -> data.readInt().toLong()
        type == 0xd3 -> data.readLong()
        else -> null
    }

    private fun map(size: Int): Map<String?, Any?> = buildMap {
        repeat(size) { put(readValue() as? String, readValue()) }
    }

    private fun skipBytes(count: Int) {
        var left = count
        while (left > 0) {
            val skipped = data.skipBytes(left)
            if (skipped <= 0) throw IOException("The data ends early")
            left -= skipped
        }
    }

    /** A 32-bit length, which a list in memory cannot exceed anyway. */
    private fun length(value: Int): Int = if (value >= 0) value else throw IOException("Too long")

    private fun unexpected(type: Int, wanted: String) = IOException("Expected $wanted, found type 0x${type.toString(16)}")

    private companion object {
        const val NIL = 0xc0
        const val FALSE = 0xc2
        const val TRUE = 0xc3
    }
}
