package com.vpr.screenlate.core.common.diagnostics

import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock

enum class OverlayLifecycleState {
    IDLE,
    SCANNING,
    OCR,
    LOOKUP,
    TRANSLATE,
}

data class TrimMemoryEvent(
    val level: Int,
    val timestamp: Long,
)

data class ProcessDiagnosticSnapshot(
    val totalPssMb: Long,
    val dalvikPssMb: Long,
    val nativePssMb: Long,
    val otherPssMb: Long,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val nativeAllocatedMb: Long,
    val cpuTimeMs: Long,
    val state: OverlayLifecycleState,
    val lastTrim: TrimMemoryEvent?,
)

object ProcessDiagnostics {
    @Volatile
    var state: OverlayLifecycleState = OverlayLifecycleState.IDLE
        private set

    @Volatile
    var lastTrim: TrimMemoryEvent? = null
        private set

    fun setLifecycleState(newState: OverlayLifecycleState) {
        state = newState
    }

    fun recordTrimMemory(level: Int, timestamp: Long = System.currentTimeMillis()) {
        lastTrim = TrimMemoryEvent(level, timestamp)
    }

    fun resetForTest() {
        state = OverlayLifecycleState.IDLE
        lastTrim = null
    }

    /**
     * Reads memory stats (PSS, Heap) and CPU time. Safe to call from background or UI threads.
     */
    fun snapshot(): ProcessDiagnosticSnapshot {
        val memInfo = Debug.MemoryInfo()
        runCatching { Debug.getMemoryInfo(memInfo) }
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)
        val nativeAllocated = runCatching { Debug.getNativeHeapAllocatedSize() / (1024 * 1024) }.getOrDefault(0L)
        val cpuTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getElapsedCpuTime()
        } else {
            SystemClock.currentThreadTimeMillis()
        }

        return ProcessDiagnosticSnapshot(
            totalPssMb = memInfo.totalPss.toLong() / 1024,
            dalvikPssMb = memInfo.dalvikPss.toLong() / 1024,
            nativePssMb = memInfo.nativePss.toLong() / 1024,
            otherPssMb = memInfo.otherPss.toLong() / 1024,
            heapUsedMb = heapUsed,
            heapMaxMb = heapMax,
            nativeAllocatedMb = nativeAllocated,
            cpuTimeMs = cpuTime,
            state = state,
            lastTrim = lastTrim,
        )
    }

    @Suppress("DEPRECATION")
    fun trimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        else -> "LEVEL_$level"
    }
}
