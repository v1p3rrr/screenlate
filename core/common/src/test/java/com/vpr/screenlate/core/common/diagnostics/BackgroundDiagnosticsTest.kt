package com.vpr.screenlate.core.common.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackgroundDiagnosticsTest {
    @Test
    fun `event bursts share a memory sample budget and elapsed time includes sleep`() {
        val gate = DiagnosticSampleGate()
        assertThat(gate.admit(100)).isTrue()
        repeat(100) { assertThat(gate.admit(101L + it)).isFalse() }
        assertThat(gate.admit(5_100)).isTrue()
        assertThat(gate.admit(60_100)).isTrue()
    }

    @Test
    fun `CPU interval counts process core time separately from elapsed and awake duration`() {
        val line = cpuInterval(DiagnosticCpuPoint(100, 100, 10), DiagnosticCpuPoint(60_100, 1_100, 2010))
        assertThat(line).contains("cpuDeltaMs=2000 elapsedDeltaMs=60000 awakeDeltaMs=1000")
        assertThat(line).contains("cpuCorePermille=33")
        assertThat(cpuInterval(DiagnosticCpuPoint(0, 0, 0), DiagnosticCpuPoint(1000, 1000, 4000)))
            .contains("cpuCorePermille=4000")
    }

    @Test
    fun `initial or reset counters do not produce a fabricated CPU rate`() {
        assertThat(cpuInterval(null, DiagnosticCpuPoint(1, 1, 1))).isEqualTo("cpuDelta=unavailable")
        assertThat(cpuInterval(DiagnosticCpuPoint(10, 10, 10), DiagnosticCpuPoint(20, 20, 5)))
            .isEqualTo("cpuDelta=unavailable")
    }

    @Test
    fun `state preserves independent foreground service and window facts`() {
        val state = BackgroundDiagnosticState(serviceConnected = true, foregroundService = true, catcherAttached = true)
        assertThat(state.describe()).contains("foregroundService=true")
        assertThat(state.describe()).contains("catcherAttached=true catcherActive=false")
        assertThat(state.describe()).contains("docked=true scanOpen=false")
    }
}
