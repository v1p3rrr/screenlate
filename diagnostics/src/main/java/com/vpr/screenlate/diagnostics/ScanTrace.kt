package com.vpr.screenlate.diagnostics

/**
 * What one scan did, gathered from the overlay, the engines, the network and the main-thread watch, for the one
 * `scan` summary event written when the scan ends. Times are milliseconds since the scan began. Thread-safe.
 */
internal class ScanTrace(val id: Int, private val begun: Long, fields: Map<String, Any?>) {
    private val fields = LinkedHashMap(fields)
    private val marks = LinkedHashMap<String, Long>()
    private val passes = mutableListOf<String>()
    private val updates = mutableListOf<String>()
    private val net = mutableListOf<String>()
    private var stalls = 0
    private var stallMax = 0L
    private var stallSum = 0L
    private var lookups = 0
    private var lookupMax = 0L
    private var lookupSum = 0L
    private val stats = LinkedHashMap<String, LongArray>()

    /** The process's CPU time when the scan began, for the CPU time it took. */
    @Volatile
    var cpuBefore = 0L

    /** The first time a stage was reached; later ones are counted in `<stage>_n`. */
    @Synchronized
    fun mark(stage: String, now: Long) {
        if (stage !in marks) {
            marks[stage] = now - begun
        } else {
            val key = "${stage}_n"
            fields[key] = ((fields[key] as? Int) ?: 1) + 1
        }
    }

    @Synchronized
    fun note(key: String, value: Any?) {
        fields[key] = value
    }

    /** An OCR pass: `engine/kind WxH ms` and what the engine adds (model, `cold`). */
    @Synchronized
    fun pass(description: String) {
        if (passes.size < MAX_ITEMS) passes += description
    }

    /** A result shown: `draft|final:ENGINE:paragraphs@ms`. */
    @Synchronized
    fun update(kind: String, engine: String, paragraphs: Int, now: Long) {
        if (updates.size < MAX_ITEMS) updates += "$kind:$engine:$paragraphs@${now - begun}"
    }

    @Synchronized
    fun net(host: String, ms: Long, reused: Boolean, waitMs: Long?, error: String?) {
        if (net.size < MAX_ITEMS) {
            net += buildString {
                append(host).append(' ').append(ms).append("ms")
                if (!reused) append(" new")
                waitMs?.let { append(" wait=").append(it) }
                error?.let { append(" ").append(it) }
            }
        }
    }

    @Synchronized
    fun stall(ms: Long) {
        stalls++
        stallSum += ms
        if (ms > stallMax) stallMax = ms
    }

    @Synchronized
    fun lookup(ms: Long) {
        lookups++
        lookupSum += ms
        if (ms > lookupMax) lookupMax = ms
    }

    /** A repeated step of the scan (`render`, `popup_show`, …): how often, the longest and the total. */
    @Synchronized
    fun stat(name: String, ms: Long) {
        val stat = stats.getOrPut(name) { LongArray(3) }
        stat[0]++
        if (ms > stat[1]) stat[1] = ms
        stat[2] += ms
    }

    @Synchronized
    fun summary(reason: String, now: Long): Array<Pair<String, Any?>> = buildList {
        add("scan" to id)
        add("end" to reason)
        add("ms" to now - begun)
        fields.forEach { (key, value) -> add(key to value) }
        add("marks" to LinkedHashMap(marks))
        if (updates.isNotEmpty()) add("updates" to updates.toList())
        if (passes.isNotEmpty()) add("passes" to passes.toList())
        if (net.isNotEmpty()) add("net" to net.toList())
        if (stalls > 0) add("stalls" to mapOf("n" to stalls, "max_ms" to stallMax, "sum_ms" to stallSum))
        if (lookups > 0) add("lookups" to mapOf("n" to lookups, "max_ms" to lookupMax, "sum_ms" to lookupSum))
        if (stats.isNotEmpty()) {
            add("stats" to stats.mapValues { (_, stat) -> mapOf("n" to stat[0], "max_ms" to stat[1], "sum_ms" to stat[2]) })
        }
    }.toTypedArray()

    private companion object {
        const val MAX_ITEMS = 40
    }
}
