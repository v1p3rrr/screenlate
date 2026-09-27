package com.vpr.screenlate.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.vpr.screenlate.BuildConfig
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class UpdateError {
    /** The release list or the APK could not be downloaded. */
    NETWORK,

    /** The release has no APK for this device. */
    NO_APK,

    /** The APK is not a newer Screenlate signed with the same key. */
    BAD_APK,

    /** Android refused or aborted the installation. */
    INSTALL_FAILED,
}

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val release: Release) : UpdateState

    data class Downloading(val release: Release, val fraction: Float) : UpdateState

    /** Handed to Android; the app restarts when the update is installed. */
    data class Installing(val release: Release) : UpdateState

    data class Failed(val error: UpdateError, val release: Release?) : UpdateState
}

/**
 * Updates from GitHub Releases for release builds: finds a newer stable release, downloads the APK for this device,
 * checks it and installs it through [PackageInstaller]. Android installs without asking where it allows that (the
 * app installed the current version itself, Android 12+); otherwise it shows its own prompt.
 */
@Singleton
class AppUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settings: UpdateSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState

    /** Debug builds are a separate app and are never replaced by releases. */
    val supported: Boolean get() = !BuildConfig.DEBUG

    private val installedCode: Long
        get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** The automatic check when the app opens: at most once a day, and only when announcements are on. */
    suspend fun checkIfDue() {
        val current = settings.current()
        if (!supported || !current.announce || System.currentTimeMillis() - current.lastCheck < CHECK_INTERVAL_MS) return
        settings.setLastCheck(System.currentTimeMillis())
        check()
    }

    /** Looks for a newer release; returns the resulting state. */
    suspend fun check(): UpdateState {
        if (!supported || job?.isActive == true) return state.value
        mutableState.value = UpdateState.Checking
        val next = withContext(Dispatchers.IO) {
            runCatching { fetchLatest() }
                .map { release -> if (Releases.isNewer(release, installedCode)) UpdateState.Available(release) else UpdateState.UpToDate }
                .onFailure { Log.w(TAG, "Update check failed", it.redacted()) }
                .getOrElse { UpdateState.Failed(UpdateError.NETWORK, null) }
        }
        mutableState.value = next
        return next
    }

    suspend fun setAnnounced(release: Release) = settings.setAnnounced(release.tag)

    /** Whether Android lets Screenlate install APKs; otherwise the user allows it in the system settings first. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Downloads and installs [release]; progress and errors go to [state]. */
    fun update(release: Release) {
        if (!supported || job?.isActive == true) return
        job = scope.launch {
            val asset = Releases.apkFor(release, Build.SUPPORTED_ABIS.toList())
            if (asset == null) {
                mutableState.value = UpdateState.Failed(UpdateError.NO_APK, release)
                return@launch
            }
            mutableState.value = UpdateState.Downloading(release, 0f)
            val file = runCatching { download(asset, release) }
                .onFailure { Log.w(TAG, "Update download failed", it.redacted()) }
                .getOrElse {
                    mutableState.value = UpdateState.Failed(UpdateError.NETWORK, release)
                    return@launch
                }
            if (!verify(file)) {
                file.delete()
                mutableState.value = UpdateState.Failed(UpdateError.BAD_APK, release)
                return@launch
            }
            mutableState.value = UpdateState.Installing(release)
            runCatching { install(file) }
                .onFailure {
                    Log.w(TAG, "Update install failed", it.redacted())
                    mutableState.value = UpdateState.Failed(UpdateError.INSTALL_FAILED, release)
                }
        }
    }

    /** Status of the install session, from [UpdateReceiver]. */
    internal fun onInstallStatus(status: Int, confirmation: Intent?) {
        val release = (state.value as? UpdateState.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirmation
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                Log.w(TAG, "Update install ended with status $status")
                mutableState.value = UpdateState.Failed(UpdateError.INSTALL_FAILED, release)
            }
        }
    }

    private fun fetchLatest(): Release = client.newCall(Request.Builder().url(BuildConfig.UPDATE_FEED).header("Accept", "application/vnd.github+json").build())
        .execute()
        .use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Releases.parse(response.body.string())
        }

    private fun download(asset: ReleaseAsset, release: Release): File {
        val directory = File(context.cacheDir, DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(directory, "update.apk")
        client.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: asset.size
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    var reported = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val fraction = if (total > 0) (copied.toFloat() / total).coerceAtMost(1f) else 0f
                        if (fraction - reported >= PROGRESS_STEP) {
                            reported = fraction
                            mutableState.value = UpdateState.Downloading(release, fraction)
                        }
                    }
                }
            }
        }
        return file
    }

    /** A newer version of this app signed with the same certificates. */
    private fun verify(file: File): Boolean = runCatching {
        val packageManager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = packageManager.getPackageArchiveInfo(file.path, flags) ?: return false
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        archive.packageName == context.packageName &&
            archive.longVersionCode > installed.longVersionCode &&
            archive.signingInfo?.apkContentsSigners?.toSet() == installed.signingInfo?.apkContentsSigners?.toSet()
    }.getOrDefault(false)

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("screenlate.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION)
            // Mutable: the installer adds the status and, if needed, the confirmation intent.
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    private companion object {
        const val TAG = "AppUpdates"
        const val DIRECTORY = "updates"
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 0.01f
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
