package com.vpr.screenlate.logs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.vpr.screenlate.core.common.log.AppLog
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.log.LogFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's own log file: kept in `noBackupFilesDir/logs` (out of backups, removed with the app's data), at most two
 * files of [LogFile.DEFAULT_MAX_FILE_BYTES]. Each process start notes the version and how earlier processes ended.
 */
object AppLogs {
    private const val TAG = "Screenlate"
    private const val DIRECTORY = "logs"
    private const val EXITS_SEEN = "exits-seen"
    private const val MAX_EXITS = 16

    fun directory(context: Context): File = File(context.noBackupFilesDir, DIRECTORY)

    /** Called first in `Application.onCreate`; the exit records are read on a background thread. */
    fun install(context: Context) {
        AppLog.install(LogFile(directory(context)))
        Thread({ logStart(context) }, "AppLogStart").start()
    }

    private fun logStart(context: Context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            Log.i(TAG, "Process started: ${info.versionName} (${info.longVersionCode}), Android ${Build.VERSION.RELEASE}")
        }
        runCatching { logExits(context) }.onFailure { Log.w(TAG, "Cannot read the exit reasons: ${it.javaClass.simpleName}") }
    }

    private fun logExits(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java)
        val records = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS).map {
            ExitRecord(it.timestamp, it.pid, it.reason, it.status, it.importance, it.pss, it.rss, it.description)
        }
        val marker = File(directory(context), EXITS_SEEN)
        val seenUpTo = marker.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
        val exits = ExitReasons.newSince(records, seenUpTo)
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())
        exits.forEach { Log.i(TAG, ExitReasons.describe(it) { stamp -> time.format(Instant.ofEpochMilli(stamp)) }) }
        exits.lastOrNull()?.let {
            marker.parentFile?.mkdirs()
            marker.writeText(it.timestamp.toString())
        }
    }
}
