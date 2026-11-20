package com.vpr.screenlate.core.common.diagnostics

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("DEPRECATION")
class ProcessDiagnosticsTest {

    @Before
    @After
    fun reset() {
        ProcessDiagnostics.resetForTest()
    }

    @Test
    fun `default state is idle and last trim is null`() {
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.IDLE)
        assertThat(ProcessDiagnostics.lastTrim).isNull()
    }

    @Test
    fun `sets lifecycle state correctly`() {
        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.SCANNING)
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.SCANNING)

        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.OCR)
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.OCR)

        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.LOOKUP)
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.LOOKUP)

        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.TRANSLATE)
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.TRANSLATE)

        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.IDLE)
        assertThat(ProcessDiagnostics.state).isEqualTo(OverlayLifecycleState.IDLE)
    }

    @Test
    fun `records trim memory events`() {
        ProcessDiagnostics.recordTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, timestamp = 12345L)
        val trim = ProcessDiagnostics.lastTrim
        assertThat(trim).isNotNull()
        assertThat(trim?.level).isEqualTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertThat(trim?.timestamp).isEqualTo(12345L)
    }

    @Test
    fun `maps trim memory levels to human readable names`() {
        assertThat(ProcessDiagnostics.trimLevelName(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
            .isEqualTo("RUNNING_CRITICAL")
        assertThat(ProcessDiagnostics.trimLevelName(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
            .isEqualTo("UI_HIDDEN")
        assertThat(ProcessDiagnostics.trimLevelName(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
            .isEqualTo("COMPLETE")
        assertThat(ProcessDiagnostics.trimLevelName(999))
            .isEqualTo("LEVEL_999")
    }

    @Test
    fun `snapshot reflects current state and last trim`() {
        ProcessDiagnostics.setLifecycleState(OverlayLifecycleState.LOOKUP)
        ProcessDiagnostics.recordTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, timestamp = 9999L)

        val snapshot = ProcessDiagnostics.snapshot()
        assertThat(snapshot.state).isEqualTo(OverlayLifecycleState.LOOKUP)
        assertThat(snapshot.lastTrim?.level).isEqualTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertThat(snapshot.lastTrim?.timestamp).isEqualTo(9999L)
    }
}
