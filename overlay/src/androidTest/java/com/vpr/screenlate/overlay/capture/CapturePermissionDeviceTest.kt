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
    fun screenshotSurvivesAudioDenialAndReusesProjectionAfterGrant() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
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
            captureAdditionalFrame(projection)

            val allowed = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
            respondToAudioPermission(automation, allow = true)
            assertThat(withTimeout(10_000) { allowed.await() }).isSameInstanceAs(projection)
            assertThat(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)).isEqualTo(PackageManager.PERMISSION_GRANTED)
            captureWhileAudioActive(projection)
            withContext(Dispatchers.Main) { MediaProjectionService.updateRecordingState(context, isRecording = false) }
            assertThat(MediaProjectionHolder.hasActiveProjection).isTrue()
            captureAdditionalFrame(projection)

            withContext(Dispatchers.Main) { MediaProjectionHolder.stop(context) }
            val audioFirst = scope.async { MediaProjectionHolder.getOrCreate(context, isRecording = true) }
            clickSystemButton(automation, setOf("android:id/button1", "com.android.systemui:id/start"))
            val audioProjection = checkNotNull(withTimeout(10_000) { audioFirst.await() })
            assertThat(withContext(Dispatchers.Main) { MediaProjectionHolder.getOrCreate(context) }).isSameInstanceAs(audioProjection)
            captureWhileAudioActive(audioProjection)
            withContext(Dispatchers.Main) { MediaProjectionService.updateRecordingState(context, isRecording = false) }
            captureAdditionalFrame(audioProjection)
        } finally {
            withContext(Dispatchers.Main) {
                MediaProjectionHolder.stop(context)
            }
            scope.cancel()
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test
    fun modernCapturerAcquiresRealWindowFramesWithoutMediaProjection() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val component = android.content.ComponentName(context, LegacyCaptureAccessibilityService::class.java)
        val previousServices = secureSetting(automation, "enabled_accessibility_services")
        val previousEnabled = secureSetting(automation, "accessibility_enabled")
        try {
            shell(automation, "settings put secure accessibility_enabled 1")
            shell(automation, "settings put secure enabled_accessibility_services ${component.flattenToString()}")
            val service = withTimeout(15_000) {
                while (LegacyCaptureAccessibilityService.active == null) delay(50)
                LegacyCaptureAccessibilityService.active!!
            }
            val capturer = ModernAccessibilityCapturer(service, java.util.concurrent.Executor { it.run() })
            assertThat(MediaProjectionHolder.hasActiveProjection).isFalse()
            repeat(2) {
                val captured = withTimeout(10_000) { capturer.capture(0, 0) { it() } }
                assertThat(captured.bitmap.width).isGreaterThan(0)
                assertThat(captured.bitmap.height).isGreaterThan(0)
                captured.bitmap.recycle()
                delay(400)
            }
            assertThat(MediaProjectionHolder.hasActiveProjection).isFalse()
        } finally {
            secureSetting(automation, "enabled_accessibility_services", previousServices)
            secureSetting(automation, "accessibility_enabled", previousEnabled)
        }
    }

    @Test
    fun legacyCapturerAcquiresRealFramesAndReusesConsent() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT in 24..29)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val component = android.content.ComponentName(context, LegacyCaptureAccessibilityService::class.java)
        val previousServices = secureSetting(automation, "enabled_accessibility_services")
        val previousEnabled = secureSetting(automation, "accessibility_enabled")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            shell(automation, "settings put secure accessibility_enabled 1")
            shell(automation, "settings put secure enabled_accessibility_services ${component.flattenToString()}")
            val service = withTimeout(15_000) {
                while (LegacyCaptureAccessibilityService.active == null) delay(50)
                LegacyCaptureAccessibilityService.active!!
            }
            val capturer = LegacyMediaProjectionCapturer(service)
            val first = scope.async { capturer.capture(0, 0) { it() } }
            if (Build.VERSION.SDK_INT == 24 || Build.VERSION.SDK_INT == 29) {
                clickLegacyConsentByCoordinates(automation, context)
            } else {
                clickSystemButton(automation, setOf("android:id/button1", "com.android.systemui:id/start"))
            }
            val expectedWidth = realDisplayMetrics(context).widthPixels
            val expectedHeight = realDisplayMetrics(context).heightPixels
            withTimeout(10_000) { first.await() }.bitmap.let {
                assertThat(it.width).isEqualTo(expectedWidth)
                assertThat(it.height).isEqualTo(expectedHeight)
            }
            val second = withTimeout(5_000) { capturer.capture(0, 0) { it() } }
            assertThat(second.bitmap.width).isEqualTo(expectedWidth)
            assertThat(second.bitmap.height).isEqualTo(expectedHeight)
            assertThat(MediaProjectionHolder.hasActiveProjection).isTrue()
        } finally {
            withContext(Dispatchers.Main) { MediaProjectionHolder.stop(context) }
            secureSetting(automation, "enabled_accessibility_services", previousServices)
            secureSetting(automation, "accessibility_enabled", previousEnabled)
            scope.cancel()
        }
    }

    private fun secureSetting(automation: UiAutomation, name: String): String =
        shell(automation, "settings get secure $name").trim().takeUnless { it == "null" }.orEmpty()

    private fun secureSetting(automation: UiAutomation, name: String, value: String) {
        shell(automation, if (value.isEmpty()) "settings delete secure $name" else "settings put secure $name $value")
    }

    private fun shell(automation: UiAutomation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    /** These two emulator images expose no usable accessibility nodes for their system consent dialog. */
    private suspend fun clickLegacyConsentByCoordinates(automation: UiAutomation, context: Context) {
        withTimeout(10_000) {
            while (true) {
                val activity = withContext(Dispatchers.IO) { shell(automation, "dumpsys activity activities") }
                if (activity.lineSequence().any {
                    it.contains("mResumedActivity:") && it.contains("MediaProjectionPermissionActivity")
                }) break
                delay(50)
            }
        }
        delay(300)
        val metrics = realDisplayMetrics(context)
        val x = metrics.widthPixels * (if (Build.VERSION.SDK_INT == 24) 780 else 802) / 1000
        val y = metrics.heightPixels * (if (Build.VERSION.SDK_INT == 24) 557 else 639) / 1000
        withContext(Dispatchers.IO) { shell(automation, "input tap $x $y") }
    }

    private fun realDisplayMetrics(context: Context): android.util.DisplayMetrics =
        android.util.DisplayMetrics().also { metrics ->
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        }

    private suspend fun captureWhileAudioActive(projection: MediaProjection) {
        val source = withContext(Dispatchers.IO) { checkNotNull(AudioRecordSource.create(projection)) }
        try {
            assertThat(withContext(Dispatchers.IO) { source.start() }).isTrue()
            captureAdditionalFrame(projection)
        } finally {
            withContext(Dispatchers.IO) { source.stop(); source.close() }
        }
    }

    /** Android 14+ allows one VirtualDisplay per projection; production stops modern projections after audio capture. */
    private suspend fun captureAdditionalFrame(projection: MediaProjection) {
        if (Build.VERSION.SDK_INT < 34) captureFrame(projection)
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
        // The platform callback that clears the holder is asynchronous on recent Android versions.
        withTimeout(5000) { while (MediaProjectionHolder.hasActiveProjection) delay(50) }
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

    /** Some permission windows are absent from UiAutomation's node tree; use their keyboard focus order. */
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
        val denyTabs = if (Build.VERSION.SDK_INT >= 34) 3 else 2
        repeat(if (allow) 1 else denyTabs) { press(KeyEvent.KEYCODE_TAB) }
        press(KeyEvent.KEYCODE_ENTER)
    }

    private suspend fun awaitAudioPermission(automation: UiAutomation) {
        withTimeout(10_000) {
            while (true) {
                val ready = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys activity activities"))
                        .bufferedReader().useLines { lines ->
                            lines.any { it.contains("ResumedActivity") && it.contains("GrantPermissionsActivity") }
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
                                // Android 7's consent dialog does not expose the later framework button IDs.
                                for (label in listOf("Start now", "START NOW")) {
                                    val nodes = root.findAccessibilityNodeInfosByText(label)
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
