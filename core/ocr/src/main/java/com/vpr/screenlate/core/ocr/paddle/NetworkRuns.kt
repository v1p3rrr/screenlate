package com.vpr.screenlate.core.ocr.paddle

/**
 * TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md): the runs of one network during a pass. An input shape other
 * than the network's last one ([shape]) resizes its session before the run.
 */
internal class NetworkRuns(last: List<Int>?) {
    var shape: List<Int>? = last
        private set
    var ms = 0L
        private set
    private var runs = 0
    private var maxMs = 0L
    private var resizes = 0
    private var resizeMs = 0L
    private var largest: List<Int>? = null

    fun add(input: IntArray, ms: Long) {
        val current = input.toList()
        runs++
        this.ms += ms
        if (ms > maxMs) maxMs = ms
        if (current != shape) {
            resizes++
            resizeMs += ms
            shape = current
        }
        if (largest.let { it == null || it.elements() < current.elements() }) largest = current
    }

    /** `runs=… ms=… max=… resizes=… resize_ms=… largest=AxBxCxD`; `resize_ms` sums the runs that resized. */
    fun describe(): String =
        "runs=$runs ms=$ms max=$maxMs resizes=$resizes resize_ms=$resizeMs largest=${largest?.joinToString("x")}"

    private fun List<Int>.elements(): Long = fold(1L) { product, dimension -> product * dimension }
}
