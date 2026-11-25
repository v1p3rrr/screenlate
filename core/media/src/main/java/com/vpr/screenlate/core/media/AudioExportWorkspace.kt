package com.vpr.screenlate.core.media

import java.io.File
import java.io.IOException

/**
 * Bounded output workspace for one export. The raw kept PCM plus container slack is a deliberately conservative
 * admission ceiling, not an estimate of compressed size. The caller retains ownership of destination cleanup.
 */
internal class AudioExportWorkspace(
    private val destination: File,
    outputFrames: Long,
    private val minFreeSpaceBytes: Long,
    private val usableSpace: (File) -> Long,
) {
    val maxOutputBytes = Math.addExact(Math.multiplyExact(outputFrames, FRAME_BYTES), CONTAINER_SLACK_BYTES)

    init {
        require(outputFrames > 0 && minFreeSpaceBytes >= 0)
    }

    fun preflight() {
        requireSpace(maxOutputBytes)
    }

    /** Recheck both the bounded output and free space immediately before each encoded container write. */
    fun beforeWrite(byteCount: Int) {
        require(byteCount >= 0)
        val resultingBytes = Math.addExact(destination.length(), byteCount.toLong())
        if (resultingBytes > maxOutputBytes) throw IOException("Audio export file budget exceeded")
        requireSpace(byteCount.toLong())
    }

    /** MediaMuxer may flush buffered bytes on close, so validate the final file and reserve after encoding. */
    fun finish() {
        if (destination.length() > maxOutputBytes) throw IOException("Audio export file budget exceeded")
        requireSpace(0)
    }

    private fun requireSpace(nextBytes: Long) {
        val required = Math.addExact(minFreeSpaceBytes, nextBytes)
        if (usableSpace(destination) < required) throw IOException("Audio export workspace is not available")
    }

    private companion object {
        const val FRAME_BYTES = 2L
        const val CONTAINER_SLACK_BYTES = 65_536L
    }
}
