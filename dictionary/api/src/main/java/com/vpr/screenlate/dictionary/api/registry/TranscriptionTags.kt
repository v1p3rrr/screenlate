package com.vpr.screenlate.dictionary.api.registry

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.LongBuffer
import java.nio.channels.FileChannel

/**
 * The tags of a dictionary's phonetic transcriptions (Yomitan's `ipa` term meta), which the engine drops: regions as
 * flag emoji or accent names, notations such as `SAMPA`, and other remarks. Only tagged transcriptions are kept, each
 * as one long: the top 39 bits of a 64-bit hash of the term and the transcription above the index of its tag set,
 * sorted by hash. The file's entries are mapped, not read: a Wiktionary dictionary has millions. Among 4 million,
 * about 15 pairs share a hash and get the tags of both, and about 8 in a million lookups of an untagged transcription
 * get another one's tags.
 */
class TranscriptionTags private constructor(
    private val tagSets: List<List<String>>,
    private val entries: Entries,
) {
    val size: Int get() = entries.size

    /** The tags of [ipa] as a transcription of [term]; empty when it has none. */
    fun of(term: String, ipa: String): List<String> = tagSets.getOrNull(entries.setOf(key(term, ipa))) ?: emptyList()

    private interface Entries {
        val size: Int

        /** The index of the tag set of [key]; -1 when it has none. */
        fun setOf(key: Long): Int
    }

    /** Hashes packed with their tag set indexes ([pack]), sorted by hash. */
    private class Packed(private val entries: LongBuffer) : Entries {
        override val size: Int get() = entries.limit()

        override fun setOf(key: Long): Int {
            val hash = hashOf(key)
            var low = 0
            var high = entries.limit() - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val entry = entries.get(middle)
                val found = entry ushr SET_BITS
                when {
                    found < hash -> low = middle + 1
                    found > hash -> high = middle - 1
                    else -> return (entry and SET_MASK).toInt()
                }
            }
            return -1
        }
    }

    /** The first version's full hashes, sorted, and their tag set indexes. */
    private class Hashed(private val hashes: LongArray, private val sets: IntArray) : Entries {
        override val size: Int get() = hashes.size

        override fun setOf(key: Long): Int = hashes.binarySearch(key).let { if (it < 0) -1 else sets[it] }
    }

    /**
     * Collects the tags of an archive's transcriptions; one transcription listed twice gets the tags of both, those of
     * the first listing first. Each tagged transcription takes one long, in chunks of [chunkSize] sorted when full,
     * which [write] merges without copying them.
     */
    class Builder internal constructor(private val chunkSize: Int) {
        constructor() : this(CHUNK_SIZE)

        private val setIndexes = HashMap<List<String>, Int>()
        private val tagSets = ArrayList<List<String>>()
        private val names = HashMap<String, String>()

        /** Sorted by hash, the listing order kept for equal ones; earlier chunks have the earlier listings. */
        private val chunks = ArrayList<LongArray>()

        /** The chunk being filled: hashes packed with their position in it, and the tag set index at each position. */
        private var current: LongArray? = null
        private var currentSets: IntArray? = null
        private var filled = 0

        init {
            require(chunkSize in 1..MAX_SETS)
        }

        val isEmpty: Boolean get() = chunks.isEmpty() && current == null

        fun add(term: String, ipa: String, tags: List<String>) {
            val kept = tags.filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }.distinct()
            if (kept.isEmpty()) return
            val chunk = current ?: LongArray(chunkSize).also { current = it }
            val sets = currentSets ?: IntArray(chunkSize).also { currentSets = it }
            chunk[filled] = pack(hashOf(key(term, ipa)), filled)
            sets[filled] = intern(kept)
            if (++filled == chunkSize) seal()
        }

        /** Writes the tags into [file], merging the chunks twice: for the count, then for the entries. */
        fun write(file: File) {
            val count = sealedCount()
            DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(count)
                merge { hash, set -> out.writeLong(pack(hash, set)) }
                out.writeInt(tagSets.size)
                for (set in tagSets) {
                    out.writeInt(set.size)
                    set.forEach(out::writeUTF)
                }
            }
        }

        /** The tags in memory, for tests: unlike [write], this needs a second copy of the entries. */
        internal fun build(): TranscriptionTags {
            val entries = LongArray(sealedCount())
            var index = 0
            merge { hash, set -> entries[index++] = pack(hash, set) }
            return TranscriptionTags(tagSets.toList(), Packed(LongBuffer.wrap(entries)))
        }

        private fun sealedCount(): Int {
            seal()
            var count = 0
            merge { _, _ -> count++ }
            return count
        }

        /** Sorts the chunk being filled and puts the tag set indexes in place of the positions. */
        private fun seal() {
            val filledChunk = current ?: return
            val sets = currentSets ?: return
            val chunk = if (filled == filledChunk.size) filledChunk else filledChunk.copyOf(filled)
            chunk.sort()
            for (i in chunk.indices) chunk[i] = pack(chunk[i] ushr SET_BITS, sets[(chunk[i] and SET_MASK).toInt()])
            chunks += chunk
            current = null
            filled = 0
        }

        /**
         * Calls [action] with each hash and the index of its tag set, in hash order. A hash found more than once gets
         * the union of their sets, in listing order.
         */
        private inline fun merge(action: (hash: Long, set: Int) -> Unit) {
            val positions = IntArray(chunks.size)
            while (true) {
                var hash = Long.MAX_VALUE
                for (c in chunks.indices) {
                    if (positions[c] < chunks[c].size) hash = minOf(hash, chunks[c][positions[c]] ushr SET_BITS)
                }
                if (hash == Long.MAX_VALUE) return
                var set = -1
                var union: LinkedHashSet<String>? = null
                for (c in chunks.indices) {
                    val chunk = chunks[c]
                    while (positions[c] < chunk.size && chunk[positions[c]] ushr SET_BITS == hash) {
                        val next = (chunk[positions[c]++] and SET_MASK).toInt()
                        if (set < 0) {
                            set = next
                        } else if (next != set) {
                            union = (union ?: LinkedHashSet(tagSets[set])).apply { addAll(tagSets[next]) }
                        }
                    }
                }
                action(hash, union?.let { intern(it.toList()) } ?: set)
            }
        }

        private fun intern(set: List<String>): Int = setIndexes[set] ?: run {
            check(tagSets.size < MAX_SETS) { "Too many transcription tag sets" }
            val interned = set.map { name -> names.getOrPut(name) { name } }
            setIndexes[interned] = tagSets.size
            tagSets += interned
            tagSets.size - 1
        }
    }

    companion object {
        /** Tag sets, then the entry count, the 64-bit hashes and the tag set indexes; read into the heap. */
        private const val MAGIC_V1 = 0x53545431 // "STT1"

        /** The entry count, the packed entries ([pack]), then the tag sets. */
        private const val MAGIC = 0x53545432 // "STT2"
        private const val HEADER_BYTES = 2 * Int.SIZE_BYTES

        private const val HASH_BITS = 39
        private const val SET_BITS = Long.SIZE_BITS - 1 - HASH_BITS
        private const val SET_MASK = (1L shl SET_BITS) - 1
        private const val MAX_SETS = 1 shl SET_BITS

        /** 2 MB of entries; the last chunk is trimmed. */
        private const val CHUNK_SIZE = 1 shl 18

        /** Longer tags are sentences of remarks, not names, and would not fit `writeUTF` anyway. */
        private const val MAX_TAG_LENGTH = 200

        fun read(file: File): TranscriptionTags = FileInputStream(file).use { stream ->
            val input = DataInputStream(BufferedInputStream(stream))
            when (input.readInt()) {
                MAGIC -> {
                    val count = input.readInt()
                    val setsAt = HEADER_BYTES + count.toLong() * Long.SIZE_BYTES
                    val channel = stream.channel
                    if (count < 0 || setsAt > channel.size()) throw IOException("Bad transcription tag file")
                    val entries = channel.map(FileChannel.MapMode.READ_ONLY, HEADER_BYTES.toLong(), setsAt - HEADER_BYTES)
                    channel.position(setsAt)
                    TranscriptionTags(readSets(DataInputStream(BufferedInputStream(stream))), Packed(entries.asLongBuffer()))
                }
                MAGIC_V1 -> {
                    val tagSets = readSets(input)
                    val count = input.readInt()
                    val hashes = LongArray(count) { input.readLong() }
                    val sets = IntArray(count) { input.readInt() }
                    if (sets.any { it !in tagSets.indices }) throw IOException("Bad transcription tag file")
                    TranscriptionTags(tagSets, Hashed(hashes, sets))
                }
                else -> throw IOException("Not a transcription tag file")
            }
        }

        /** Equal tag names become one string. */
        private fun readSets(input: DataInput): List<List<String>> {
            val names = HashMap<String, String>()
            return List(input.readInt()) { List(input.readInt()) { input.readUTF().let { names.getOrPut(it) { it } } } }
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

        /** The top [HASH_BITS] bits of a [key], which the packed entries keep. */
        private fun hashOf(key: Long): Long = key ushr (Long.SIZE_BITS - HASH_BITS)

        /** Non-negative, so a packed entry sorts by [hash]. */
        private fun pack(hash: Long, set: Int): Long = (hash shl SET_BITS) or set.toLong()

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
    }
}
