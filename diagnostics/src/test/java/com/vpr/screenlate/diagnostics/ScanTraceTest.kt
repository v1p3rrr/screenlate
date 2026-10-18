package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanTraceTest {
    @Test
    fun `sums up the scan in one event`() {
        val trace = ScanTrace(7, begun = 1_000, fields = mapOf("trigger" to "tap"))
        trace.mark("capture_start", 1_010)
        trace.mark("popup_shown", 1_400)
        trace.mark("popup_shown", 1_900)
        trace.note("engine", "lens")
        trace.pass("mlkit/recognize 1080x400 120ms")
        trace.update("draft", "ML_KIT", 3, 1_300)
        trace.net("lens.example", 380, reused = false, waitMs = 300, error = null)
        trace.stall(60)
        trace.stall(200)
        trace.lookup(15)
        trace.lookup(5)

        val summary = trace.summary("closed", 2_000).toMap()

        assertThat(summary["scan"]).isEqualTo(7)
        assertThat(summary["end"]).isEqualTo("closed")
        assertThat(summary["ms"]).isEqualTo(1_000L)
        assertThat(summary["trigger"]).isEqualTo("tap")
        assertThat(summary["engine"]).isEqualTo("lens")
        assertThat(summary["marks"]).isEqualTo(mapOf("capture_start" to 10L, "popup_shown" to 400L))
        assertThat(summary["popup_shown_n"]).isEqualTo(2)
        assertThat(summary["passes"]).isEqualTo(listOf("mlkit/recognize 1080x400 120ms"))
        assertThat(summary["updates"]).isEqualTo(listOf("draft:ML_KIT:3@300"))
        assertThat(summary["net"]).isEqualTo(listOf("lens.example 380ms new wait=300"))
        assertThat(summary["stalls"]).isEqualTo(mapOf<String, Any>("n" to 2, "max_ms" to 200L, "sum_ms" to 260L))
        assertThat(summary["lookups"]).isEqualTo(mapOf<String, Any>("n" to 2, "max_ms" to 15L, "sum_ms" to 20L))
    }

    @Test
    fun `leaves out what did not happen and caps long lists`() {
        val trace = ScanTrace(1, begun = 0, fields = emptyMap())
        repeat(100) { trace.pass("pass $it") }
        val summary = trace.summary("timeout", 30_000).toMap()
        assertThat(summary.keys).containsNoneOf("updates", "net", "stalls", "lookups")
        assertThat(summary["passes"] as List<*>).hasSize(40)
    }
}
