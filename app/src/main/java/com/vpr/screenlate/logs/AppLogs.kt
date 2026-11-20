package com.vpr.screenlate.logs

import android.content.pm.ApplicationInfo
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.vpr.screenlate.core.common.log.AppLog
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.log.LogFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The app's own log file: kept in `noBackupFilesDir/logs` (out of backups, removed with the app's data), at most two
 * files of [LogFile.DEFAULT_MAX_FILE_BYTES]. Each process start notes the version and how earlier processes ended.
 */
object AppLogs {
    private const val TAG = "Screenlate"
    private const val DIRECTORY = "logs"
    private const val EXITS_SEEN = "exits-seen"

    fun directory(context: Context): File = File(context.noBackupFilesDir, DIRECTORY)

    /** Called first in `Application.onCreate`; the exit records are read on a background thread. */
    fun install(context: Context) {
        AppLog.install(LogFile(directory(context)))
        Thread({ logStart(context) }, "AppLogStart").start()
    }

    private fun logStart(context: Context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(info)
            Log.i(TAG, "Process started: ${info.versionName} ($versionCode), Android ${Build.VERSION.RELEASE}" +
                if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)) {
                    ", pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()} process=${context.applicationInfo.processName}"
                } else "")
        }
        runCatching { logExits(context) }.onFailure { Log.w(TAG, "Cannot read the exit reasons: ${it.javaClass.simpleName}") }
    }

    private fun logExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val records = ExitReasons.readRecords(context)
        val marker = File(directory(context), EXITS_SEEN)
        val seenUpTo = marker.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
        val exits = ExitReasons.newSince(records, seenUpTo)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        exits.forEach { Log.i(TAG, ExitReasons.describe(it, detailed = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)) { stamp -> time.format(Date(stamp)) }) }
        exits.lastOrNull()?.let {
            marker.parentFile?.mkdirs()
            marker.writeText(it.timestamp.toString())
        }
    }
}
