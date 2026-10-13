package com.vpr.screenlate.dictionary.api.registry

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * The tags of a dictionary's phonetic transcriptions (Yomitan's `ipa` term meta), which the engine drops: regions as
 * flag emoji or accent names, notations such as `SAMPA`, and other remarks. Only tagged transcriptions are kept,
 * by a 64-bit hash of the term and the transcription, which is enough for the few hundred thousand of a dictionary.
 */
class TranscriptionTags private constructor(
    private val hashes: LongArray,
    private val sets: IntArray,
    private val tagSets: List<List<String>>,
) {
    val size: Int get() = hashes.size

    /** The tags of [ipa] as a transcription of [term]; empty when it has none. */
    fun of(term: String, ipa: String): List<String> {
        val index = hashes.binarySearch(key(term, ipa))
        return if (index < 0) emptyList() else tagSets[sets[index]]
    }

    fun write(file: File) {
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(tagSets.size)
            for (set in tagSets) {
                out.writeInt(set.size)
                set.forEach(out::writeUTF)
            }
            out.writeInt(hashes.size)
            hashes.forEach(out::writeLong)
            sets.forEach(out::writeInt)
        }
    }

    /** Collects the tags of an archive's transcriptions; one transcription listed twice gets the tags of both. */
    class Builder {
        private val byKey = HashMap<Long, LinkedHashSet<String>>()

        val isEmpty: Boolean get() = byKey.isEmpty()

        fun add(term: String, ipa: String, tags: List<String>) {
            val kept = tags.filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
            if (kept.isEmpty()) return
            byKey.getOrPut(key(term, ipa)) { LinkedHashSet() }.addAll(kept)
        }

        fun build(): TranscriptionTags {
            val keys = byKey.keys.toLongArray().apply { sort() }
            val setIndex = HashMap<List<String>, Int>()
            val tagSets = mutableListOf<List<String>>()
            val sets = IntArray(keys.size) { i ->
                val set = byKey.getValue(keys[i]).toList()
                setIndex.getOrPut(set) { tagSets.size.also { tagSets.add(set) } }
            }
            return TranscriptionTags(keys, sets, tagSets)
        }
    }

    companion object {
        private const val MAGIC = 0x53545431 // "STT1"

        /** Longer tags are sentences of remarks, not names, and would not fit `writeUTF` anyway. */
        private const val MAX_TAG_LENGTH = 200

        fun read(file: File): TranscriptionTags =
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC) throw IOException("Not a transcription tag file")
                val tagSets = List(input.readInt()) { List(input.readInt()) { input.readUTF() } }
                val count = input.readInt()
                val hashes = LongArray(count) { input.readLong() }
                val sets = IntArray(count) { input.readInt() }
                if (sets.any { it !in tagSets.indices }) throw IOException("Bad transcription tag file")
                TranscriptionTags(hashes, sets, tagSets)
            }

        /** FNV-1a over the UTF-16 units of the term, a separator and the transcription. */
        internal fun key(term: String, ipa: String): Long {
            var hash = FNV_OFFSET
            fun mix(c: Char) {
                hash = (hash xor c.code.toLong()) * FNV_PRIME
            }
            term.forEach(::mix)
            mix('\u0000')
            ipa.forEach(::mix)
            return hash
        }

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
    }
}
