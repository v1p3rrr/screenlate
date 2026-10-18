package com.vpr.screenlate.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.system.Os
import android.system.OsConstants
import java.io.File

/** Memory of the app's process, in KB. */
internal object Memory {
    private val pageKb: Long by lazy { runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) / 1024 }.getOrDefault(4L) }

    /** Cheap enough for every scan: the allocators' counters and the resident size. */
    fun cheap(): Map<String, Any?> {
        val runtime = Runtime.getRuntime()
        return linkedMapOf(
            "native_kb" to Debug.getNativeHeapAllocatedSize() / 1024,
            "java_kb" to (runtime.totalMemory() - runtime.freeMemory()) / 1024,
            "rss_kb" to residentKb(),
            "threads" to File("/proc/self/task").list()?.size,
        )
    }

    /**
     * The process's PSS by kind and the system's memory state. Reads the whole memory map, which takes tens of
     * milliseconds in a large process: only on the diagnostics thread, and not often.
     */
    fun full(context: Context): Map<String, Any?> {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val manager = context.getSystemService(ActivityManager::class.java)
        val system = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val process = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        fun stat(name: String) = info.getMemoryStat("summary.$name")?.toLongOrNull()
        return linkedMapOf<String, Any?>(
            "pss_kb" to info.totalPss.toLong(),
            "pss_java_kb" to stat("java-heap"),
            "pss_native_kb" to stat("native-heap"),
            "pss_code_kb" to stat("code"),
            "pss_stack_kb" to stat("stack"),
            "pss_graphics_kb" to stat("graphics"),
            "pss_other_kb" to stat("private-other"),
            "pss_system_kb" to stat("system"),
            "swap_kb" to stat("total-swap"),
            "private_dirty_kb" to info.totalPrivateDirty.toLong(),
            "sys_avail_mb" to (system.availMem shr 20),
            "sys_threshold_mb" to (system.threshold shr 20),
            "sys_low" to system.lowMemory,
            "importance" to process.importance,
            "trim_level" to process.lastTrimLevel,
        ) + cheap()
    }

    /** From `/proc/self/statm`: its second field is the resident size in pages. */
    fun residentKb(): Long? = runCatching { parseStatmResident(File("/proc/self/statm").readText())?.times(pageKb) }.getOrNull()

    internal fun parseStatmResident(statm: String): Long? = statm.trim().split(' ').getOrNull(1)?.toLongOrNull()
}
