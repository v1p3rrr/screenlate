package com.vpr.screenlate.logs

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shares [LogExport] files; a subclass so its paths do not clash with other FileProviders in the app. */
class LogFileProvider : FileProvider()

/** A log saved to the shared Downloads folder: its content URI and the path a file manager shows. */
data class SavedLog(val uri: Uri, val path: String)

/**
 * The app's own log (Android shows an app only its own log lines) with the app version and device model, as a file
 * for the share sheet or saved to Downloads. Logs never contain recognized text, words or note contents (see
 * `redacted()`).
 */
object LogExport {
    private const val DIRECTORY = "logs"
    private const val MAX_LINES = 20_000
    private const val DOWNLOADS_FOLDER = "Screenlate"

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

    /** Writes a fresh log to `Download/Screenlate/`, where any file manager finds it; no permission is needed. */
    suspend fun saveToDownloads(context: Context): SavedLog {
        val file = write(context)
        return withContext(Dispatchers.IO) {
            val folder = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_FOLDER"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, folder)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Downloads refused a new file")
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write to Downloads")
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
            SavedLog(uri, "$folder/${file.name}")
        }
    }

    /** Opens a saved log in a text viewer, if the phone has one. */
    fun viewIntent(log: SavedLog): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(log.uri, "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

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
