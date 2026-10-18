package com.vpr.screenlate.diagnostics

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Lines appended to numbered files `<prefix>-0001.jsonl`, `<prefix>-0002.jsonl`, …: a file that reaches
 * [maxFileBytes] is closed and the next one started; beyond [maxFiles] the oldest is deleted. A new process continues
 * the newest file while it has room. Not thread-safe: one writer thread uses it.
 */
internal class RotatingLog(
    private val directory: File,
    private val prefix: String,
    private val maxFileBytes: Long,
    private val maxFiles: Int,
) {
    private var out: OutputStream? = null
    private var number = 0
    private var size = 0L

    fun append(line: String) {
        val bytes = line.encodeToByteArray()
        val stream = out ?: open()
        stream.write(bytes)
        stream.write(NEWLINE)
        size += bytes.size + 1
        if (size >= maxFileBytes) {
            close()
            number++
            size = 0
            prune()
        }
    }

    fun flush() {
        out?.flush()
    }

    fun close() {
        out?.close()
        out = null
    }

    /** Oldest first. */
    fun files(): List<File> = directory.listFiles { file -> numberOf(file) != null }.orEmpty().sortedBy { numberOf(it) }

    /** Deletes every file; the next line starts a new one. */
    fun clear() {
        close()
        files().forEach(File::delete)
        number = 0
        size = 0
    }

    private fun open(): OutputStream {
        directory.mkdirs()
        if (number == 0) {
            val last = files().lastOrNull()
            number = last?.let(::numberOf) ?: 1
            if (last != null && last.length() >= maxFileBytes) number++
        }
        val file = fileOf(number)
        size = file.length()
        prune()
        return BufferedOutputStream(FileOutputStream(file, true), BUFFER).also { out = it }
    }

    /** Keeps the current file and the newest [maxFiles] - 1 before it. */
    private fun prune() {
        files().filter { (numberOf(it) ?: 0) < number }.dropLast(maxFiles - 1).forEach(File::delete)
    }

    private fun fileOf(number: Int) = File(directory, "$prefix-${number.toString().padStart(4, '0')}$SUFFIX")

    private fun numberOf(file: File): Int? {
        val name = file.name
        if (!name.startsWith("$prefix-") || !name.endsWith(SUFFIX)) return null
        return name.substring(prefix.length + 1, name.length - SUFFIX.length).toIntOrNull()
    }

    private companion object {
        const val SUFFIX = ".jsonl"
        const val BUFFER = 16 * 1024
        const val NEWLINE = '\n'.code
    }
}
