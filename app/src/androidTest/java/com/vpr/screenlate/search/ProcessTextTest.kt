package com.vpr.screenlate.search

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.R
import org.junit.Test
import org.junit.runner.RunWith

/** "Look up in Screenlate" in the text selection menu of other apps. */
@RunWith(AndroidJUnit4::class)
class ProcessTextTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectionMenusOfferTheLookup() {
        // What a text selection menu asks the system for its extra items.
        val query = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        @Suppress("DEPRECATION") // The flags overload needs API 33.
        val activities = context.packageManager.queryIntentActivities(query, 0)
        val ours = activities.singleOrNull { it.activityInfo.packageName == context.packageName }

        assertThat(ours).isNotNull()
        assertThat(ours!!.activityInfo.name).isEqualTo(ProcessTextActivity::class.java.name)
        assertThat(ours.activityInfo.exported).isTrue()
        assertThat(ours.loadLabel(context.packageManager).toString()).isEqualTo(context.getString(R.string.process_text_label))
    }

    @Test
    fun selectedTextOpensTheSearch() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setClass(context, ProcessTextActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "食べる")
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        ActivityScenario.launch<ProcessTextActivity>(intent).use { scenario ->
            assertThat(scenario.state).isAtLeast(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertThat(activity.intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()).isEqualTo("食べる")
            }
        }
    }
}
