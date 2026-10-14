package com.vpr.screenlate.dictionary.api.registry

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The words of a term dictionary by the key of their reading (`LanguageSupport.readingKey`), which the engine cannot
 * search: pinyin typed in Latin letters finds the Chinese words. Keys are kept as 64-bit hashes, and the words of
 * each key are listed the likely common ones first: a character that more of the dictionary's words contain is taken
 * as more common, and a word as common as its rarest character.
 *
 * The file is mapped, not read: a dictionary's index takes a few megabytes.
 */
class ReadingIndex private constructor(private val buffer: ByteBuffer) {
    private val keyCount = buffer.getInt(Int.SIZE_BYTES)
    private val offsets = HEADER_BYTES + keyCount.toLong() * Long.SIZE_BYTES
    private val words = offsets + (keyCount + 1L) * Int.SIZE_BYTES

    init {
        if (buffer.getInt(0) != MAGIC || keyCount < 0 || words > buffer.limit()) throw IOException("Not a reading index")
    }

    val size: Int get() = keyCount

    /** The words whose reading has [key], the likely common ones first. */
    fun find(key: String): List<String> {
        val hash = hash(key)
        var low = 0
        var high = keyCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val value = buffer.getLong(HEADER_BYTES + middle * Long.SIZE_BYTES)
            when {
                value < hash -> low = middle + 1
                value > hash -> high = middle - 1
                else -> return wordsAt(middle)
            }
        }
        return emptyList()
    }

    private fun wordsAt(index: Int): List<String> {
        val start = words + buffer.getInt((offsets + index * Int.SIZE_BYTES).toInt())
        val end = words + buffer.getInt((offsets + (index + 1) * Int.SIZE_BYTES).toInt())
        if (start > end || end > buffer.limit()) throw IOException("Bad reading index")
        val bytes = ByteArray((end - start).toInt())
        buffer.duplicate().apply { position(start.toInt()) }.get(bytes)
        return bytes.decodeToString().split(SEPARATOR)
    }

    /** Collects the words of an archive's term rows, one row at a time. */
    class Builder {
        private val characterCounts = HashMap<String, Int>()
        private val entries = ArrayList<Entry>()
        private var previous: String? = null

        val isEmpty: Boolean get() = entries.isEmpty()

        /** A term row: [expression] counts for the characters it contains; [key] is null for a reading without one. */
        fun add(expression: String, key: String?) {
            if (expression.isEmpty() || SEPARATOR in expression) return
            // Rows of one word usually follow each other; each counts once.
            if (expression != previous) {
                forEachCharacter(expression) { character, index ->
                    if (expression.indexOf(character) == index) characterCounts.merge(character, 1, Int::plus)
                }
                previous = expression
            }
            if (key != null) entries.add(Entry(hash(key), expression))
        }

        /** Adds what [other] collected, from other rows of the same archive. */
        fun addAll(other: Builder) {
            other.characterCounts.forEach { (character, count) -> characterCounts.merge(character, count, Int::plus) }
            entries.addAll(other.entries)
        }

        fun write(file: File) {
            val weights = HashMap<String, Int>()
            for (entry in entries) {
                entry.weight = weights.getOrPut(entry.expression) {
                    var rarest = Int.MAX_VALUE
                    forEachCharacter(entry.expression) { character, _ -> rarest = minOf(rarest, characterCounts[character] ?: 0) }
                    rarest
                }
            }
            val sorted = entries.sortedWith { a, b ->
                when {
                    a.hash != b.hash -> a.hash.compareTo(b.hash)
                    a.weight != b.weight -> b.weight.compareTo(a.weight)
                    else -> a.expression.compareTo(b.expression)
                }
            }
            val hashes = ArrayList<Long>()
            val ends = ArrayList<Int>()
            val blob = ByteArrayOutputStream()
            var index = 0
            while (index < sorted.size) {
                val hash = sorted[index].hash
                val words = LinkedHashSet<String>()
                while (index < sorted.size && sorted[index].hash == hash) words.add(sorted[index++].expression)
                blob.write(words.joinToString(SEPARATOR).encodeToByteArray())
                hashes.add(hash)
                ends.add(blob.size())
            }
            DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(hashes.size)
                hashes.forEach(out::writeLong)
                out.writeInt(0)
                ends.forEach(out::writeInt)
                blob.writeTo(out)
            }
        }

        private class Entry(val hash: Long, val expression: String) {
            var weight = 0
        }

        /** Each code point of [text] as a string, with its index in [text]. */
        private inline fun forEachCharacter(text: String, action: (String, Int) -> Unit) {
            var index = 0
            while (index < text.length) {
                val end = index + Character.charCount(text.codePointAt(index))
                action(text.substring(index, end), index)
                index = end
            }
        }
    }

    companion object {
        private const val MAGIC = 0x53524931 // "SRI1"
        private const val HEADER_BYTES = 2 * Int.SIZE_BYTES
        private const val SEPARATOR = "\n"

        fun read(file: File): ReadingIndex = RandomAccessFile(file, "r").use { input ->
            val buffer = input.channel.map(FileChannel.MapMode.READ_ONLY, 0, input.length())
            if (buffer.limit() < HEADER_BYTES) throw IOException("Not a reading index")
            ReadingIndex(buffer)
        }

        /** FNV-1a over the UTF-16 units of the key. */
        internal fun hash(key: String): Long {
            var hash = FNV_OFFSET
            for (c in key) hash = (hash xor c.code.toLong()) * FNV_PRIME
            return hash
        }

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
    }
}
