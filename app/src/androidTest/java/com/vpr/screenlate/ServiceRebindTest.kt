package com.vpr.screenlate

import android.app.KeyguardManager
import android.app.UiAutomation
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.camera.PhotoTestAccess
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.overlay.OverlayServiceStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the disposable verification application's service and restores the system settings. */
@RunWith(AndroidJUnit4::class)
class ServiceRebindTest {
    @Test
    fun runningStateFollowsQuickRebinds() = checkRebinds(checkHome = false)

    @Test
    fun homeFollowsQuickRebinds() = checkRebinds(checkHome = true)

    private fun checkRebinds(checkHome: Boolean): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName == "com.vpr.screenlate.verification")
        if (checkHome) assumeTrue(!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resolver = context.contentResolver
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val enabledKey = Settings.Secure.ACCESSIBILITY_ENABLED
        val original = Settings.Secure.getString(resolver, key)
        val originalEnabled = Settings.Secure.getString(resolver, enabledKey)
        val component = "${context.packageName}/com.vpr.screenlate.overlay.ScreenlateAccessibilityService"
        assumeTrue(original.orEmpty().split(':').none { it.startsWith(context.packageName + "/") })
        val preferences = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
            .commonPreferences()
        val savedPreferences = preferences.data.first()
        val tipSeen = booleanPreferencesKey("background_tip_seen")
        var scenario: ActivityScenario<MainActivity>? = null
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try {
            // Avoid competing Screenlate overlays while leaving unrelated accessibility services intact.
            val others = original.orEmpty().split(':').filter {
                it.isNotBlank() && !it.contains("com.vpr.screenlate.overlay.ScreenlateAccessibilityService")
            }
            fun enable(on: Boolean) {
                Settings.Secure.putString(resolver, key, (others + if (on) listOf(component) else emptyList()).joinToString(":"))
                Settings.Secure.putInt(resolver, enabledKey, 1)
            }
            enable(true)
            await { OverlayServiceStatus.running.value }
            if (checkHome) {
                preferences.edit { it[LanguageProfiles.FIRST_RUN] = false; it[tipSeen] = true }
                scenario = ActivityScenario.launch(MainActivity::class.java)
            }
            fun assertHomeRunning() {
                if (!checkHome) return
                val shown = context.getString(R.string.home_bubble_shown)
                await { homeShows(automation, shown) }
            }
            assertHomeRunning()
            repeat(12) {
                if (it % 2 == 0) scenario?.moveToState(Lifecycle.State.CREATED)
                enable(false)
                SystemClock.sleep(100)
                enable(true)
                await { OverlayServiceStatus.running.value }
                SystemClock.sleep(350)
                // Android may still be between the old instance's destruction and the replacement's connection.
                await { OverlayServiceStatus.running.value }
                scenario?.moveToState(Lifecycle.State.RESUMED)
                assertHomeRunning()
            }
            enable(false)
            await { !OverlayServiceStatus.running.value }
        } finally {
            scenario?.close()
            Settings.Secure.putString(resolver, key, original)
            Settings.Secure.putString(resolver, enabledKey, originalEnabled)
            automation.dropShellPermissionIdentity()
            assertThat(Settings.Secure.getString(resolver, key)).isEqualTo(original)
            assertThat(Settings.Secure.getString(resolver, enabledKey)).isEqualTo(originalEnabled)
            if (checkHome) preferences.edit {
                val firstRun = savedPreferences[LanguageProfiles.FIRST_RUN]
                val tip = savedPreferences[tipSeen]
                if (firstRun == null) it.remove(LanguageProfiles.FIRST_RUN) else it[LanguageProfiles.FIRST_RUN] = firstRun
                if (tip == null) it.remove(tipSeen) else it[tipSeen] = tip
            }
        }
    }

    private fun homeShows(automation: UiAutomation, text: String): Boolean {
        repeat(8) {
            if (findText(automation.rootInActiveWindow, text)) return true
            findScrollable(automation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(150)
        }
        return findText(automation.rootInActiveWindow, text)
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        return node.text?.toString() == text || (0 until node.childCount).any { findText(node.getChild(it), text) }
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) findScrollable(node.getChild(index))?.let { return it }
        return null
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertThat(condition()).isTrue()
    }
}
