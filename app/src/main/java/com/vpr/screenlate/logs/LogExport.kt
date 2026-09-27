package com.vpr.screenlate.logs

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shares [LogExport] files; a subclass so its paths do not clash with other FileProviders in the app. */
class LogFileProvider : FileProvider()

/**
 * The app's own log (Android shows an app only its own log lines) with the app version and device model, as a file
 * for the share sheet. Logs never contain recognized text, words or note contents (see `redacted()`).
 */
object LogExport {
    private const val DIRECTORY = "logs"
    private const val MAX_LINES = 20_000

    suspend fun write(context: Context): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
        val file = File(directory, "screenlate-log-$stamp.txt")
        file.bufferedWriter().use { out ->
            out.write(header(context))
            out.write("\n")
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", MAX_LINES.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { input -> input.copyTo(out) }
            process.waitFor()
        }
        file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    internal fun header(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("Screenlate ${info.versionName} (${info.longVersionCode}), ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
        }
    }
}
