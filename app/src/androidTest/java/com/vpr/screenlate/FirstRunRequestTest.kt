package com.vpr.screenlate

import android.app.ActivityManager
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.camera.PhotoTestAccess
import com.vpr.screenlate.overlay.OverlayIntents
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A page another app asks for during the first run opens over the first run, which stays until it is answered. */
@RunWith(AndroidJUnit4::class)
class FirstRunRequestTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        .commonPreferences()
    private val key = booleanPreferencesKey("languages_first_run")
    private var previous: Boolean? = null

    @Before
    fun startFirstRun() = runBlocking {
        closeTasks()
        previous = preferences.data.first()[key]
        preferences.edit { it[key] = true }
        Unit
    }

    @After
    fun restore() = runBlocking {
        closeTasks()
        preferences.edit { prefs -> previous?.let { prefs[key] = it } ?: prefs.remove(key) }
        Unit
    }

    @Test
    fun backFromARequestedPageReturnsToTheFirstRun() {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText(R.string.first_run_question)

        OverlayIntents.openScreen(context, OverlayIntents.OPEN_DICTIONARIES)
        awaitText(R.string.dictionaries_title)
        // Without the navigation bar no tab can leave the first run behind.
        assertThat(shows(R.string.nav_home)).isFalse()

        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single().onBackPressedDispatcher.onBackPressed()
        }
        awaitText(R.string.first_run_question)
    }

    /**
     * Reads the window as the bubble does: Espresso, under the Compose test rule, cannot start on the newest Android.
     * Compose does not answer a search by text, so the tree is walked.
     */
    private fun shows(text: Int): Boolean {
        val string = context.getString(text)
        fun AccessibilityNodeInfo.has(): Boolean = this.text?.toString() == string ||
            contentDescription?.toString() == string || (0 until childCount).any { getChild(it)?.has() == true }
        return instrumentation.uiAutomation.rootInActiveWindow?.has() == true
    }

    private fun awaitText(text: Int) {
        val end = System.currentTimeMillis() + TIMEOUT_MS
        while (!shows(text)) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for " + context.getString(text) }
            Thread.sleep(POLL_MS)
        }
    }

    private fun closeTasks() {
        context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        val end = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < end) {
            var open = false
            instrumentation.runOnMainSync {
                open = (Stage.entries - Stage.DESTROYED).any { stage ->
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).any { it is MainActivity }
                }
            }
            if (!open) return
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
