package com.vpr.screenlate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class StartupLocaleTest {
    @Test
    fun activityNamesLanguagesInItsConfiguredLocaleOnFirstLaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName == "com.vpr.screenlate.verification")
        val previous = LocaleList.getDefault()
        val application = instrumentation.targetContext.applicationContext as Application
        val initialName = AtomicReference<String>()
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) LocaleList.setDefault(LocaleList.forLanguageTags("en-US"))
            }
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) initialName.set(Language.CANTONESE.displayName())
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        application.registerActivityLifecycleCallbacks(callback)
        try {
            // Reproduce a cold process whose default locale has not caught up with its Russian resources.
            instrumentation.runOnMainSync { LocaleList.setDefault(LocaleList.forLanguageTags("en-US")) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertThat(activity.resources.configuration.locales[0].language).isEqualTo("ru")
                    assertThat(initialName.get()).isEqualTo("Кантонский")
                }
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callback)
            instrumentation.runOnMainSync { LocaleList.setDefault(previous) }
        }
    }
}
