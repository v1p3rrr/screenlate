package com.vpr.screenlate.overlay.capture

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.media.AudioRecordSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** System dialogs in the isolated library test package; no app data, audio playback or images are saved. */
@RunWith(AndroidJUnit4::class)
class CapturePermissionDeviceTest {
    @Test
    fun api29ScreenshotSurvivesAudioDenialAndReusesProjectionAfterGrant() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT == 29)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertThat(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)).isEqualTo(PackageManager.PERMISSION_DENIED)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val projection = screenshotAfterRevokedConsent(context, automation, scope)
            assertThat(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)).isEqualTo(PackageManager.PERMISSION_DENIED)
            captureFrame(projection)

            val denied = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
            respondToAudioPermission(automation, allow = false)
            assertThat(withTimeout(10_000) { denied.await() }).isNull()
            assertThat(MediaProjectionHolder.hasActiveProjection).isTrue()
            assertThat(withContext(Dispatchers.Main) { MediaProjectionHolder.getOrCreate(context) }).isSameInstanceAs(projection)
            captureFrame(projection)

            val allowed = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
            respondToAudioPermission(automation, allow = true)
            assertThat(withTimeout(10_000) { allowed.await() }).isSameInstanceAs(projection)
            assertThat(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)).isEqualTo(PackageManager.PERMISSION_GRANTED)
            captureWhileAudioActive(projection)
            withContext(Dispatchers.Main) { MediaProjectionService.updateRecordingState(context, isRecording = false) }
            assertThat(MediaProjectionHolder.hasActiveProjection).isTrue()
            captureFrame(projection)

            withContext(Dispatchers.Main) { MediaProjectionHolder.stop(context) }
            val audioFirst = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
            clickSystemButton(automation, setOf("android:id/button1", "com.android.systemui:id/start"))
            val audioProjection = checkNotNull(withTimeout(10_000) { audioFirst.await() })
            assertThat(withContext(Dispatchers.Main) { MediaProjectionHolder.getOrCreate(context) }).isSameInstanceAs(audioProjection)
            captureWhileAudioActive(audioProjection)
            withContext(Dispatchers.Main) { MediaProjectionService.updateRecordingState(context, isRecording = false) }
            captureFrame(audioProjection)
        } finally {
            withContext(Dispatchers.Main) {
                MediaProjectionHolder.stop(context)
            }
            scope.cancel()
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private suspend fun captureWhileAudioActive(projection: MediaProjection) {
        val source = withContext(Dispatchers.IO) { checkNotNull(AudioRecordSource.create(projection)) }
        try {
            assertThat(withContext(Dispatchers.IO) { source.start() }).isTrue()
            captureFrame(projection)
        } finally {
            withContext(Dispatchers.IO) { source.stop(); source.close() }
        }
    }

    private suspend fun screenshotAfterRevokedConsent(
        context: Context,
        automation: UiAutomation,
        scope: CoroutineScope,
    ): MediaProjection {
        val first = scope.async { MediaProjectionHolder.getOrCreate(context) }
        clickSystemButton(automation, setOf("android:id/button1", "com.android.systemui:id/start"))
        val projection = checkNotNull(withTimeout(10_000) { first.await() })
        captureFrame(projection)
        val pending = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
        awaitAudioPermission(automation)
        withContext(Dispatchers.Main) { projection.stop() }
        assertThat(withTimeout(5000) { pending.await() }).isNull()
        assertThat(MediaProjectionHolder.hasActiveProjection).isFalse()
        assertThat(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)).isEqualTo(PackageManager.PERMISSION_DENIED)

        val retry = scope.async { MediaProjectionHolder.getOrCreate(context) }
        clickSystemButton(automation, setOf("android:id/button1", "com.android.systemui:id/start"))
        return checkNotNull(withTimeout(10_000) { retry.await() }).also {
            assertThat(it).isNotSameInstanceAs(projection)
        }
    }

    /** Mirrors the legacy screenshot backend: acquire one frame, then release the display. */
    private suspend fun captureFrame(projection: MediaProjection) = withContext(Dispatchers.Main) {
        val captured = CompletableDeferred<Unit>()
        val reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 2)
        var display: android.hardware.display.VirtualDisplay? = null
        try {
            reader.setOnImageAvailableListener({
                it.acquireLatestImage()?.let { image -> image.close(); captured.complete(Unit) }
            }, Handler(Looper.getMainLooper()))
            display = projection.createVirtualDisplay(
                "permission-fixture", 64, 64, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, Handler(Looper.getMainLooper()),
            )
            assertThat(display).isNotNull()
            withTimeout(5000) { captured.await() }
        } finally {
            display?.release()
            reader.close()
        }
    }

    /** API29's permission window can be absent from UiAutomation's node tree; use keyboard navigation. */
    private suspend fun respondToAudioPermission(automation: UiAutomation, allow: Boolean) {
        awaitAudioPermission(automation)
        fun press(key: Int) {
            val time = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                check(automation.injectInputEvent(KeyEvent(
                    time, SystemClock.uptimeMillis(), action, key, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
                ), true)) { "System dialog key injection failed ($key/$action)" }
            }
        }
        repeat(if (allow) 1 else 2) { press(KeyEvent.KEYCODE_TAB) }
        press(KeyEvent.KEYCODE_ENTER)
    }

    private suspend fun awaitAudioPermission(automation: UiAutomation) {
        withTimeout(10_000) {
            while (true) {
                val ready = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys activity activities"))
                        .bufferedReader().useLines { lines ->
                            lines.any { it.contains("mResumedActivity:") && it.contains("GrantPermissionsActivity") }
                        }
                }
                if (ready) break
                delay(50)
            }
        }
    }

    private suspend fun clickSystemButton(automation: UiAutomation, ids: Set<String>) {
        try {
            withTimeout(10_000) {
                while (true) {
                    val windows = automation.windows
                    try {
                        for (window in windows) {
                            val root = window.root ?: continue
                            try {
                                for (id in ids) {
                                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                                    try {
                                        val button = nodes.firstOrNull { it.isEnabled && it.isClickable }
                                        if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return@withTimeout
                                    } finally { nodes.forEach { @Suppress("DEPRECATION") it.recycle() } }
                                }
                            } finally { @Suppress("DEPRECATION") root.recycle() }
                        }
                    } finally { windows.forEach { @Suppress("DEPRECATION") it.recycle() } }
                    delay(50)
                }
            }
        } catch (error: TimeoutCancellationException) {
            throw AssertionError("System consent button unavailable: ${ids.joinToString()}", error)
        }
    }
}
