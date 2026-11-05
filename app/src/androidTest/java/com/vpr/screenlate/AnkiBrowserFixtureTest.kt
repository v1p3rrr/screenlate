package com.vpr.screenlate

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.api.AddContentApi
import com.vpr.screenlate.core.anki.AnkiDroid
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit fixture for the emulator's new, local, unsynced collection only. */
class AnkiBrowserFixtureTest {
    @Test
    fun openKnownNoteInAnotherDeck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stage = InstrumentationRegistry.getArguments().getString("browserFixture")
        assumeTrue(context.packageName == "com.vpr.screenlate.verification" &&
            Build.HARDWARE in listOf("ranchu", "goldfish") && stage in listOf("prepare", "existing", "new"))
        val api = AddContentApi(context)
        check(AddContentApi.getAnkiDroidPackageName(context) == "com.ichi2.anki")
        val state = File(context.noBackupFilesDir, "anki-browser-fixture.txt")
        if (stage == "prepare") {
            check(!state.exists()) { "Fixture already prepared" }
            val name = "Screenlate Browser Acceptance"
            val model = checkNotNull(api.addNewCustomModel(name, arrayOf("Front", "Back"), arrayOf("Card"),
                arrayOf("{{Front}}"), arrayOf("{{Back}}"), null, null, null))
            val deck = checkNotNull(api.addNewDeck(name))
            checkNotNull(api.addNote(model, 1L, arrayOf("default fixture", "fixture"), emptySet()))
            val note = checkNotNull(api.addNote(model, deck, arrayOf("existing fixture", "fixture"), emptySet()))
            state.writeText("$model\n$deck\n$note")
        } else {
            val (model, deck, oldNote) = state.readLines().map(String::toLong)
            val note = if (stage == "new") {
                checkNotNull(api.addNote(model, deck, arrayOf("new fixture", "fixture"), emptySet()))
            } else oldNote
            check(AnkiDroid(context).browseNotes(listOf(note)))
            if (InstrumentationRegistry.getArguments().getString("expectVisible") == "true") {
                val wanted = if (stage == "new") "new fixture" else "existing fixture"
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val deadline = SystemClock.uptimeMillis() + 6_000
                fun visible() = contains(automation.rootInActiveWindow, wanted)
                while (!visible() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                check(visible()) { "Expected isolated fixture note to be visible in AnkiDroid" }
            }
        }
    }

    private fun contains(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        return (node.packageName == "com.ichi2.anki" && node.text?.toString() == text) ||
            (0 until node.childCount).any { contains(node.getChild(it), text) }
    }
}
