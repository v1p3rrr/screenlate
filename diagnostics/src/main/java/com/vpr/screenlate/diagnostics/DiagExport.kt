package com.vpr.screenlate.diagnostics

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TEMPORARY DIAGNOSTICS BUILD. Packs the exported log with everything [Diag] collected and a snapshot of the current
 * state into one zip file next to it (`ai/notes/diagnostics.md`).
 */
object DiagExport {
    const val MIME = "application/zip"

    /** The zip in [log]'s folder; [log] itself goes into it and is deleted. Without collected data, [log] unchanged. */
    fun zip(context: Context, log: File): File {
        val directory = Diag.directory() ?: return log
        Diag.event("export", "diag_kb" to (Diag.sizeBytes() shr 10))
        val files = Diag.files()
        val zip = File(log.parentFile, log.nameWithoutExtension + ".zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry(log.name))
            log.inputStream().use { it.copyTo(out) }
            out.closeEntry()
            out.putNextEntry(ZipEntry("diagnostics/state.json"))
            out.write(snapshot(context).encodeToByteArray())
            out.closeEntry()
            for (file in files) {
                // The logcat copy keeps growing while it is read; whatever was there by then goes in.
                val name = "diagnostics/" + file.relativeTo(directory).invariantSeparatorsPath
                runCatching {
                    out.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        log.delete()
        return zip
    }

    private fun snapshot(context: Context): String {
        val fields = buildList<Pair<String, Any?>> {
            add("since" to Diag.since())
            add("diag_kb" to (Diag.sizeBytes() shr 10))
            runCatching { DeviceState.identity(context) }.getOrNull()?.forEach { add(it.key to it.value) }
            runCatching { DeviceState.full(context) }.getOrNull()?.forEach { add(it.key to it.value) }
            runCatching { Memory.full(context) }.getOrNull()?.forEach { add(it.key to it.value) }
            addAll(ProcessInfo.start().map { it.key to it.value })
        }
        return DiagJson.line(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), "state", fields.toTypedArray()) + "\n"
    }
}
