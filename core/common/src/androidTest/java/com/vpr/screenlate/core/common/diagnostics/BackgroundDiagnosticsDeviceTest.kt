package com.vpr.screenlate.core.common.diagnostics

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.log.AppLog
import com.vpr.screenlate.core.common.log.LogFile
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Test

class BackgroundDiagnosticsDeviceTest {
    @Test
    fun devMonitorExportsRealProcessSamplesButReleaseContextCannotEnableIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val release = object : ContextWrapper(context) {
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(context.applicationInfo).apply {
                flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
        }
        BackgroundDiagnostics.install(release)
        assertThat(BackgroundDiagnostics.isEnabled).isFalse()
        val directory = File(context.cacheDir, "background-diagnostics-test").apply { mkdirs() }
        val log = LogFile(directory)
        AppLog.install(log)
        BackgroundDiagnostics.install(context)
        assertThat(BackgroundDiagnostics.isEnabled).isTrue()
        BackgroundDiagnostics.update("device_test_docked") { it.copy(serviceConnected = true, docked = true) }
        BackgroundDiagnostics.sampleForExport()
        val output = ByteArrayOutputStream()
        log.copyTo(output)
        val text = output.toString("UTF-8")
        assertThat(text).contains("event=device_test_docked")
        assertThat(text).contains("sample=export")
        assertThat(text).contains("pssMb=")
        assertThat(text).contains("rssKb=")
        assertThat(text).contains("cpuTotalMs=")
        assertThat(text).contains("batteryExempt=")
        assertThat(text).doesNotContain("sample_failed")
        assertThat(text).doesNotContain("systemState=unavailable")
    }
}
