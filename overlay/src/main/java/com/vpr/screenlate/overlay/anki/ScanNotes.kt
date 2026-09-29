package com.vpr.screenlate.overlay.anki

/**
 * Notes added during one scan, by term. A note may finish after its scan closed, as docking does not cancel it; its
 * result then stays out of the next scan.
 */
internal class ScanNotes {
    private val added = mutableMapOf<Pair<String, String>, List<Long>>()

    /** Changes when the scan closes; a note keeps the value it started with. */
    var scan = 0
        private set

    operator fun get(term: Pair<String, String>): List<Long>? = added[term]

    /** Records the notes of [term]; false, recording nothing, when [scan] has closed since. */
    fun add(scan: Int, term: Pair<String, String>, ids: List<Long>): Boolean {
        if (scan != this.scan) return false
        added[term] = ids
        return true
    }

    fun close() {
        added.clear()
        scan++
    }
}
