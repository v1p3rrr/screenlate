package com.vpr.screenlate.core.media

/**
 * Process-local admission for PCM bytes and chunk files across every live or leased recording session.
 * Reservations belong to the history that acquired them and are released only after bytes are rolled back
 * or files are actually deleted. This ledger never selects or deletes protected audio itself.
 */
internal class PcmStorageLedger(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    val maxFiles: Int = DEFAULT_MAX_FILES,
) {
    private var bytes = 0L
    private var files = 0
    private var peakBytes = 0L
    private var peakFiles = 0

    init {
        require(maxBytes > 0 && maxFiles > 0)
    }

    val usedBytes: Long @Synchronized get() = bytes
    val usedFiles: Int @Synchronized get() = files
    val highWaterBytes: Long @Synchronized get() = peakBytes
    val highWaterFiles: Int @Synchronized get() = peakFiles

    @Synchronized
    fun reserve(additionalBytes: Long, additionalFiles: Int = 0): Boolean {
        require(additionalBytes >= 0 && additionalFiles >= 0)
        if (additionalBytes > maxBytes - bytes || additionalFiles > maxFiles - files) return false
        bytes += additionalBytes
        files += additionalFiles
        peakBytes = maxOf(peakBytes, bytes)
        peakFiles = maxOf(peakFiles, files)
        return true
    }

    @Synchronized
    fun release(releasedBytes: Long, releasedFiles: Int = 0) {
        require(releasedBytes >= 0 && releasedFiles >= 0)
        check(releasedBytes <= bytes && releasedFiles <= files) { "PCM storage reservation released twice" }
        bytes -= releasedBytes
        files -= releasedFiles
    }

    companion object {
        // Existing provisional ceilings, now shared by all sessions rather than applied independently.
        const val DEFAULT_MAX_BYTES = 134_217_728L
        const val DEFAULT_MAX_FILES = 1500
    }
}
