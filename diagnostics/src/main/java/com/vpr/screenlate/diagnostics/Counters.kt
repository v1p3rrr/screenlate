package com.vpr.screenlate.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Event counts between two samples. Thread-safe. */
internal class Counters {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun add(key: String) {
        counts.getOrPut(key) { AtomicLong() }.incrementAndGet()
    }

    /** The counts since the last call, sorted by key; the counters start again from zero. */
    fun drain(): Map<String, Long> = counts.keys.sorted()
        .associateWith { counts[it]?.getAndSet(0) ?: 0L }
        .filterValues { it > 0 }
}
