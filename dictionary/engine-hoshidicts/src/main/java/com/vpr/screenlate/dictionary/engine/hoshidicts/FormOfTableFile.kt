package com.vpr.screenlate.dictionary.engine.hoshidicts

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Checks of the form-of table file (layout in `src/main/cpp/language/form_of_table.hpp`) without loading it. */
internal object FormOfTableFile {
    private val MAGIC = "SLFO".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val HEADER_SIZE = 4 + 4 + 4 * 4 + 6 * 8

    /**
     * Whether [file] is whole: absent (a dictionary without forms) or with a valid header whose sections fit the file and
     * whose last index entries match the data sizes, which a file cut short fails.
     */
    fun isComplete(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            RandomAccessFile(file, "r").use { input ->
                val size = input.length()
                if (size < HEADER_SIZE) return false
                val header = read(input, 0, HEADER_SIZE)
                val magic = ByteArray(4).also { header.get(it) }
                if (!magic.contentEquals(MAGIC) || header.getInt() != VERSION) return false
                header.getInt() // forms
                header.getInt() // blocks
                val strings = header.getInt().toLong() and 0xFFFFFFFFL
                val tagSets = header.getInt().toLong() and 0xFFFFFFFFL
                val offsets = LongArray(6) { header.getLong() } + size
                for (i in 0 until 6) {
                    if (offsets[i] < HEADER_SIZE || offsets[i] > offsets[i + 1]) return false
                }
                if (offsets[3] - offsets[2] != 4 * (strings + 1) || offsets[5] - offsets[4] != 4 * (tagSets + 1)) return false
                val stringDataSize = offsets[4] - offsets[3]
                val tagSetDataSize = offsets[6] - offsets[5]
                val lastString = read(input, offsets[2] + 4 * strings, 4).getInt().toLong() and 0xFFFFFFFFL
                val lastTagSet = read(input, offsets[4] + 4 * tagSets, 4).getInt().toLong() and 0xFFFFFFFFL
                lastString == stringDataSize && lastTagSet == tagSetDataSize
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun read(input: RandomAccessFile, at: Long, count: Int): ByteBuffer {
        val bytes = ByteArray(count)
        input.seek(at)
        input.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }
}
