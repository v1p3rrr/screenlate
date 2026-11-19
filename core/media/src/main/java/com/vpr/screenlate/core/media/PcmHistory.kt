package com.vpr.screenlate.core.media

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

internal class HistoryQuotaException : IOException("PCM history quota reached")

internal fun interface PcmWriter {
    fun write(file: File, position: Long, bytes: ByteArray, offset: Int, length: Int)
}

private object FilePcmWriter : PcmWriter {
    override fun write(file: File, position: Long, bytes: ByteArray, offset: Int, length: Int) {
        RandomAccessFile(file, "rw").use { output ->
            output.seek(position)
            output.write(bytes, offset, length)
        }
    }
}

/**
 * Frame-aligned mono PCM16 storage. Call writes/pruning on a worker; never pass raw PCM through a UI bridge.
 * A published prefix is immutable even while its last file grows. Leases and readers independently pin files.
 */
internal class PcmHistory(
    private val directory: File,
    private val chunkFrames: Int,
    private val maxBytes: Long,
    private val maxFiles: Int,
    private val writer: PcmWriter = FilePcmWriter,
) : AutoCloseable {
    private val chunks = mutableListOf<Chunk>()
    private var active: Chunk? = null
    private var keep = emptyList<AudioRange>()
    private var closed = false
    private var failed = false
    private var storedBytes = 0L
    private var end = 0L

    init {
        require(chunkFrames > 0 && maxBytes > 0 && maxFiles > 0)
        require(directory.isDirectory || directory.mkdirs())
        check(directory.list()?.isEmpty() == true) { "PCM history requires a fresh directory" }
    }

    val committedEnd: Long @Synchronized get() = end
    val bytes: Long @Synchronized get() = storedBytes
    val files: Int @Synchronized get() = chunks.size

    @Synchronized
    fun append(pcm: ByteArray, count: Int = pcm.size) {
        check(!closed && !failed)
        require(count in 0..pcm.size && count % FRAME_BYTES == 0)
        if (count.toLong() > maxBytes - storedBytes) throw HistoryQuotaException()
        var offset = 0
        while (offset < count) {
            val chunk = active ?: newChunk().also { active = it }
            val length = minOf(count - offset, (chunkFrames - chunk.frames) * FRAME_BYTES)
            try {
                writer.write(chunk.file, chunk.frames.toLong() * FRAME_BYTES, pcm, offset, length)
            } catch (error: IOException) {
                failed = true
                // A failed write must not publish or expose its possibly partial bytes.
                runCatching { RandomAccessFile(chunk.file, "rw").use { it.setLength(chunk.frames.toLong() * FRAME_BYTES) } }
                throw error
            }
            chunk.frames += length / FRAME_BYTES
            end = Math.addExact(end, length / FRAME_BYTES.toLong())
            storedBytes += length
            offset += length
            if (chunk.frames == chunkFrames) active = null
        }
    }

    /** Keep ring and protected ranges separately, so a gap between a capped context and a later ring is evicted. */
    @Synchronized
    fun retain(ranges: List<AudioRange>) {
        check(!closed)
        keep = ranges.toList()
        prune()
    }

    @Synchronized
    fun snapshot(range: AudioRange): Snapshot {
        check(!closed)
        require(range.frames > 0)
        val parts = mutableListOf<Part>()
        var next = range.start
        for (chunk in chunks) {
            val overlap = range.intersect(chunk.range) ?: continue
            if (overlap.start != next) throw EOFException("PCM history is not continuous")
            parts += Part(chunk, (overlap.start - chunk.start) * FRAME_BYTES, overlap.frames * FRAME_BYTES)
            next = overlap.endExclusive
        }
        if (next != range.endExclusive) throw EOFException("PCM history is not available")
        pin(parts)
        return Snapshot(range, parts)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        active = null
        keep = emptyList()
        prune()
    }

    private fun newChunk(): Chunk {
        prune()
        if (chunks.size >= maxFiles) throw HistoryQuotaException()
        val file = File(directory, "${UUID.randomUUID()}.pcm")
        if (!file.createNewFile()) throw IOException("Cannot create PCM chunk")
        return Chunk(file, end).also { chunks += it }
    }

    private fun prune() {
        val iterator = chunks.iterator()
        while (iterator.hasNext()) {
            val chunk = iterator.next()
            if (chunk === active || chunk.pins > 0 || keep.any { it.intersect(chunk.range) != null }) continue
            if (!chunk.file.exists() || chunk.file.delete()) {
                storedBytes -= chunk.frames.toLong() * FRAME_BYTES
                iterator.remove()
            }
        }
    }

    @Synchronized
    private fun pin(parts: List<Part>) { parts.forEach { it.chunk.pins++ } }

    @Synchronized
    private fun unpin(parts: List<Part>) {
        parts.forEach { check(it.chunk.pins > 0); it.chunk.pins-- }
        prune()
    }

    inner class Snapshot internal constructor(val range: AudioRange, private val parts: List<Part>) : AutoCloseable {
        private var released = false

        @Synchronized
        fun openStream(): InputStream {
            check(!released)
            pin(parts)
            return ChunkInput(parts) { unpin(parts) }
        }

        @Synchronized
        override fun close() {
            if (!released) {
                released = true
                unpin(parts)
            }
        }
    }

    internal class Chunk(val file: File, val start: Long, var frames: Int = 0, var pins: Int = 0) {
        val range: AudioRange get() = AudioRange(start, start + frames)
    }

    internal data class Part(val chunk: Chunk, val offset: Long, val bytes: Long)

    private class ChunkInput(private val parts: List<Part>, private val release: () -> Unit) : InputStream() {
        private var index = 0
        private var position = 0L
        private var input: RandomAccessFile? = null
        private var closed = false
        private val single = ByteArray(1)

        override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 255

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            check(!closed)
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            if (length == 0) return 0
            if (index == parts.size) return -1
            val part = parts[index]
            val file = input ?: RandomAccessFile(part.chunk.file, "r").also {
                if (it.length() < part.offset + part.bytes) {
                    it.close()
                    throw EOFException("PCM chunk is truncated")
                }
                it.seek(part.offset)
                input = it
            }
            val count = file.read(buffer, offset, minOf(length.toLong(), part.bytes - position).toInt())
            if (count <= 0) throw EOFException("PCM chunk ended unexpectedly")
            position += count
            if (position == part.bytes) {
                file.close()
                input = null
                index++
                position = 0
            }
            return count
        }

        override fun close() {
            if (!closed) {
                closed = true
                try { input?.close() } finally { release() }
                input = null
            }
        }
    }

    private companion object { const val FRAME_BYTES = 2 }
}
