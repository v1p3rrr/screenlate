package com.vpr.screenlate.camera

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.ichi2.anki.api.AddContentApi
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.common.Language
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in manual fixture, only for the emulator's local unsynced Anki collection. Restores the original app settings. */
@RunWith(AndroidJUnit4::class)
class PhotoAnkiFixtureTest {
    @Test
    fun photoNoteFixture() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("photoAnki")
        assumeTrue("Explicit emulator-only fixture required", stage in listOf("prepare", "verify", "restore") &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, AnkiDroid.PERMISSION)
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val settings = access.ankiSettings()
        val backup = File(context.noBackupFilesDir, "photo-anki-fixture.json")
        val api = AddContentApi(context)
        when (stage) {
            "prepare" -> {
                assumeTrue("Needs local AnkiDroid", AddContentApi.getAnkiDroidPackageName(context) != null)
                if (!backup.exists()) backup.writeText(Json.encodeToString(AnkiSettings.serializer(), settings.current(Language.JAPANESE)))
                val name = "Screenlate Photo Test"
                val model = api.modelList.orEmpty().entries.firstOrNull { it.value == name }?.key
                    ?: api.addNewCustomModel(name, arrayOf("Expression", "Meaning", "Sentence", "Picture"), arrayOf("Card 1"),
                        arrayOf("{{Expression}}"), arrayOf("{{Meaning}}<br>{{Sentence}}<br>{{Picture}}"), null, null, null)
                val deck = api.deckList.orEmpty().entries.firstOrNull { it.value == name }?.key ?: api.addNewDeck(name)
                check(model != null && deck != null) { "Test note type/deck unavailable" }
                settings.update(Language.JAPANESE) { it.copy(modelId = model, modelName = name, deckId = deck, deckName = name,
                    fields = mapOf("Expression" to "{expression}", "Meaning" to "{glossary}", "Sentence" to "{sentence}", "Picture" to "{screenshot}"),
                    duplicateBehavior = DuplicateBehavior.NEW, tags = "screenlate-photo-test") }
            }
            "verify" -> {
                val word = InstrumentationRegistry.getArguments().getString("photoWord") ?: "語"
                val model = settings.current(Language.JAPANESE).modelId!!
                val found = api.findDuplicateNotes(model, word).orEmpty().any { note ->
                    note.fields.size == 4 && note.fields[2].contains("これは日本語の写真です") && note.fields[3].contains("<img")
                }
                assertThat(found).isTrue()
            }
            "restore" -> {
                if (backup.exists()) {
                    val previous = Json.decodeFromString(AnkiSettings.serializer(), backup.readText())
                    settings.update(Language.JAPANESE) { previous }
                    backup.delete()
                }
            }
        }
    }
}
