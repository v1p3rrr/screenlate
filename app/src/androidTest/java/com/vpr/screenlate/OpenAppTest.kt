package com.vpr.screenlate

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.overlay.OverlayIntents
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening the app from the bubble menu, the launcher or the search of the selection menu brings back its task with the
 * same main screen, however the task was started.
 */
@RunWith(AndroidJUnit4::class)
class OpenAppTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    @After
    fun closeTasks() {
        context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        waitUntil("the main screens closed") { liveMainScreens().isEmpty() }
    }

    @Test
    fun bubbleReturnsToTheAppOpenedFromSearch() {
        OverlayIntents.openScreen(context, OverlayIntents.OPEN_DICTIONARIES)
        val first = awaitResumed()
        goHome(first)

        OverlayIntents.openApp(context)

        assertThat(awaitResumed()).isSameInstanceAs(first)
        assertThat(mainScreens()).containsExactly(first)
    }

    @Test
    fun launcherReturnsToTheAppOpenedFromSearch() {
        OverlayIntents.openScreen(context, OverlayIntents.OPEN_DICTIONARIES)
        val first = awaitResumed()
        goHome(first)

        context.startActivity(launcherIntent())

        assertThat(awaitResumed()).isSameInstanceAs(first)
        assertThat(mainScreens()).containsExactly(first)
    }

    @Test
    fun bubbleReturnsToATaskStartedByAnotherIntent() {
        // The intent the selection menu's search used before: the task's root intent is not the launcher's.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val first = awaitResumed()
        goHome(first)

        OverlayIntents.openApp(context)

        assertThat(awaitResumed()).isSameInstanceAs(first)
        assertThat(mainScreens()).containsExactly(first)
    }

    @Test
    fun searchOpensAScreenInTheRunningApp() {
        context.startActivity(launcherIntent())
        val first = awaitResumed()
        goHome(first)

        OverlayIntents.openScreen(context, OverlayIntents.OPEN_DICTIONARIES)

        assertThat(awaitResumed()).isSameInstanceAs(first)
        assertThat(mainScreens()).containsExactly(first)
    }

    /** What a launcher starts on a tap on the icon. */
    private fun launcherIntent(): Intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun goHome(screen: MainActivity) {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil("the main screen stopped") { stageOf(screen) == Stage.STOPPED }
    }

    private fun awaitResumed(): MainActivity {
        var resumed: MainActivity? = null
        waitUntil("a resumed main screen") {
            resumed = mainScreens().singleOrNull { stageOf(it) == Stage.RESUMED }
            resumed != null
        }
        return resumed!!
    }

    private fun mainScreens(): List<MainActivity> = liveMainScreens().filterNot { it.isFinishing }

    /** Main screens of this process not destroyed yet, finishing ones included. */
    private fun liveMainScreens(): List<MainActivity> {
        val screens = mutableListOf<MainActivity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            (Stage.entries - Stage.DESTROYED).forEach { stage ->
                screens += monitor.getActivitiesInStage(stage).filterIsInstance<MainActivity>()
            }
        }
        return screens
    }

    private fun stageOf(activity: Activity): Stage {
        var stage = Stage.DESTROYED
        instrumentation.runOnMainSync { stage = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity) }
        return stage
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Timed out waiting for $what" }
            SystemClock.sleep(POLL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
