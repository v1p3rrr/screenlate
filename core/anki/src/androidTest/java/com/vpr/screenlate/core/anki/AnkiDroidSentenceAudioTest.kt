package com.vpr.screenlate.core.anki

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.ichi2.anki.api.AddContentApi
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import com.vpr.screenlate.core.common.Language
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real sentence-audio transfer in an isolated local AnkiDroid collection on Android 10. The provider has no media-read
 * API, so this API 29 test grants itself storage access and decodes the copied file directly from collection.media.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29, maxSdkVersion = 29)
class AnkiDroidSentenceAudioTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var settings: AnkiSettingsRepository
    private lateinit var backend: AnkiDroid
    private lateinit var notes: AnkiNotes
    private var modelId = 0L
    private var deckId = 0L
    private var testWord = ""

    @Before
    fun setUp() {
        assumeTrue("Requires an isolated emulator", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("AnkiDroid is not installed", AddContentApi.getAnkiDroidPackageName(context) != null)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, AnkiDroid.PERMISSION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_EXTERNAL_STORAGE)
        root = File(context.cacheDir, "anki-audio-${UUID.randomUUID()}").apply { mkdirs() }
        settings = AnkiSettingsRepository(PreferenceDataStoreFactory.create { File(root, "anki.preferences_pb") })
        backend = AnkiDroid(context)
        notes = AnkiNotes(backend, settings)

        val api = AddContentApi(context)
        modelId = runCatching {
            api.modelList.orEmpty().entries.firstOrNull { it.value == NAME }?.key
                ?: api.addNewCustomModel(NAME, arrayOf("Word", "Meaning", "Sentence"), arrayOf("Card 1"),
                    arrayOf("{{Word}}"), arrayOf("{{Meaning}}{{Sentence}}"), null, null, null)
        }.getOrNull() ?: 0L
        deckId = runCatching {
            api.deckList.orEmpty().entries.firstOrNull { it.value == NAME }?.key ?: api.addNewDeck(NAME)
        }.getOrNull() ?: 0L
        assumeTrue("AnkiDroid has no collection yet", modelId != 0L && deckId != 0L)
        runBlocking {
            settings.update(Language.JAPANESE) {
                it.copy(
                    deckId = deckId,
                    deckName = NAME,
                    modelId = modelId,
                    modelName = NAME,
                    fields = mapOf(
                        "Word" to "{expression}",
                        "Meaning" to "{glossary}",
                        "Sentence" to "{sentence-audio}",
                    ),
                    tags = "screenlate-test",
                    duplicateScope = DuplicateScope.DECK,
                    duplicateBehavior = DuplicateBehavior.PREVENT,
                )
            }
        }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun addsTransfersPreservesAndReplacesSentenceAudio() = runBlocking<Unit> {
        assertThat(notes.status(Language.JAPANESE)).isEqualTo(AnkiStatus.Ready)
        val word = "音声${UUID.randomUUID().toString().take(8)}".also { testWord = it }
        val values = mapOf("expression" to word, "glossary" to "first")
        val mediaDirectory = AnkiDroid.mediaDirectory(context)
        val original = File(mediaDirectory, "sentence-original-${UUID.randomUUID()}.opus")
        val replacement = File(mediaDirectory, "sentence-replacement-${UUID.randomUUID()}.opus")
        try {
            OpusTestAudio.create(original, 440)
            val added = notes.add(Language.JAPANESE,
                NoteRequest(values, sentenceAudio = SentenceAudioAttachment.Attached(original))) as AddResult.Added
            val originalReference = audioReference(added.noteId)
            val transferred = mediaFile(originalReference)
            assertThat(transferred.readBytes()).isEqualTo(original.readBytes())
            OpusTestAudio.assertDecodes(transferred, 440)

            settings.update(Language.JAPANESE) {
                it.copy(
                    duplicateBehavior = DuplicateBehavior.OVERWRITE,
                    overwriteModes = mapOf("Sentence" to OverwriteMode.OVERWRITE),
                )
            }
            val omitted = notes.add(Language.JAPANESE, NoteRequest(values + ("glossary" to "second"),
                sentenceAudio = SentenceAudioAttachment.Omitted))
            assertThat(omitted).isEqualTo(AddResult.Updated(added.noteId))
            assertThat(audioReference(added.noteId)).isEqualTo(originalReference)

            val withoutAudio = notes.add(Language.JAPANESE, NoteRequest(values + ("glossary" to "third"),
                sentenceAudio = SentenceAudioAttachment.None))
            assertThat(withoutAudio).isEqualTo(AddResult.Updated(added.noteId))
            assertThat(audioReference(added.noteId)).isEqualTo(originalReference)

            OpusTestAudio.create(replacement, 660)
            val replaced = notes.add(Language.JAPANESE, NoteRequest(values + ("glossary" to "fourth"),
                sentenceAudio = SentenceAudioAttachment.Attached(replacement)))
            assertThat(replaced).isEqualTo(AddResult.Updated(added.noteId))
            val replacementReference = audioReference(added.noteId)
            assertThat(replacementReference).isNotEqualTo(originalReference)
            val replacementTransfer = mediaFile(replacementReference)
            assertThat(replacementTransfer.readBytes()).isEqualTo(replacement.readBytes())
            OpusTestAudio.assertDecodes(replacementTransfer, 660)
        } finally {
            original.delete()
            replacement.delete()
        }
    }

    @Test
    fun providerCopyFailureRetriesSamePreparationBeforeAnyNoteMutation() = runBlocking<Unit> {
        val word = "再試行${UUID.randomUUID().toString().take(8)}".also { testWord = it }
        val values = mapOf("expression" to word, "glossary" to "retry")
        val source = File(AnkiDroid.mediaDirectory(context), "sentence-retry-${UUID.randomUUID()}.opus")
        try {
            // The real provider cannot open this granted but absent source; no collection mutation is allowed.
            val target = notes.freezeTarget(Language.JAPANESE)
            val preparation = notes.preparation(target,
                NoteRequest(values, sentenceAudio = SentenceAudioAttachment.Attached(source)))
            assertThat(preparation.prepare()).isEqualTo(NotePreparationResult.MediaFailed(NoteMediaRole.SENTENCE))
            assertThat(notes.duplicateIds(Language.JAPANESE, values)).isEmpty()

            OpusTestAudio.create(source, 440)
            val ready = preparation.prepare() as NotePreparationResult.Ready
            val added = notes.write(ready.note) as AddResult.Added
            val transferred = mediaFile(audioReference(added.noteId))
            assertThat(transferred.readBytes()).isEqualTo(source.readBytes())
            OpusTestAudio.assertDecodes(transferred, 440)
            OpusTestAudio.assertMediaPlayerCompletes(transferred)
            assertThat(notes.duplicateIds(Language.JAPANESE, values)).containsExactly(added.noteId)
        } finally {
            source.delete()
        }
    }

    private suspend fun audioReference(noteId: Long): String =
        backend.findDuplicates(testWord, listOf(modelId), DuplicateScope.DECK, deckId)
            .single { it.id == noteId }
            .fields[2]

    private fun mediaFile(reference: String): File {
        val match = checkNotNull(Regex("^\\[sound:(.+)]$").matchEntire(reference)) { "Not an audio field: $reference" }
        return File(ANKI_MEDIA_DIRECTORY, Uri.decode(match.groupValues[1])).also {
            assertThat(it.isFile).isTrue()
        }
    }

    private companion object {
        const val NAME = "Screenlate Audio Test"
        const val ANKI_MEDIA_DIRECTORY = "/sdcard/AnkiDroid/collection.media"
    }
}
