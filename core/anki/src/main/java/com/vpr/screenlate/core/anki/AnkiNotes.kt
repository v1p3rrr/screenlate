package com.vpr.screenlate.core.anki

import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import com.vpr.screenlate.core.common.Language
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SentenceAudioAttachment {
    /** Sentence audio was not requested for this note. */
    data object None : SentenceAudioAttachment

    /** Sentence audio was requested and successfully prepared in [file]. */
    data class Attached(val file: File) : SentenceAudioAttachment

    /** Sentence audio was intentionally omitted by the user or skipped by duplicate keep policy. */
    data object Omitted : SentenceAudioAttachment

    /** Sentence audio was requested but export or media preparation failed. */
    data class Failed(val cause: Throwable? = null) : SentenceAudioAttachment
}

/**
 * Everything needed to fill one note. [values] holds the text markers; media markers (`{screenshot}`,
 * `{audio}`) are filled from the files after they are copied into AnkiDroid.
 */
data class NoteRequest(
    val values: Map<String, String>,
    val screenshot: File? = null,
    val audio: AudioClip? = null,
    val sentenceAudio: SentenceAudioAttachment = SentenceAudioAttachment.None,
)

sealed interface AddResult {
    data class Added(val noteId: Long) : AddResult

    data class Updated(val noteId: Long) : AddResult

    /** Blocked by the duplicate check with [DuplicateBehavior.PREVENT]; [noteIds] are the existing notes. */
    data class Duplicate(val noteIds: List<Long>) : AddResult

    data object ConfigurationChanged : AddResult

    data object NotConfigured : AddResult

    data class Unavailable(val availability: AnkiAvailability) : AddResult

    /** AnkiDroid answered but did not store the note. */
    data object Rejected : AddResult

    data class Failed(val message: String) : AddResult
}

/** Effective destination and write policy captured before preparing a note's attachments. */
class NoteTarget internal constructor(val language: Language, internal val settings: AnkiSettings) {
    val markers: Set<String> = settings.fields.values.flatMapTo(mutableSetOf()) { FieldTemplate.markersIn(it) }
}

/** Something that broke in the Anki setup without the user changing Screenlate's settings. */
enum class AnkiProblem { NOT_INSTALLED, NO_PERMISSION, MODEL_MISSING, DECK_MISSING, FIELDS_CHANGED }

sealed interface AnkiStatus {
    data object Ready : AnkiStatus

    /** Anki export was never set up; nothing to warn about. */
    data object NotConfigured : AnkiStatus

    data class Broken(val problem: AnkiProblem) : AnkiStatus
}

/** Builds notes from field templates and adds them to AnkiDroid with the configured duplicate handling. */
@Singleton
class AnkiNotes @Inject constructor(
    private val anki: AnkiBackend,
    private val settingsRepository: AnkiSettingsRepository,
) {
    private val fieldCache = mutableMapOf<Long, List<String>>()

    suspend fun settings(language: Language): AnkiSettings = settingsRepository.current(language)

    suspend fun freezeTarget(language: Language): NoteTarget = NoteTarget(language, settings(language).effective())

    fun cachedTarget(language: Language): NoteTarget? = settingsRepository.cachedSettings(language)
        ?.let { NoteTarget(language, it.effective()) }

    fun targetChanged(target: NoteTarget): Flow<Boolean> = settingsRepository.settings(target.language)
        .map { it.effective() != target.settings }

    private suspend fun isCurrent(target: NoteTarget): Boolean = settings(target.language).effective() == target.settings

    private fun AnkiSettings.effective(): AnkiSettings = copy(
        deckName = null, modelName = null, savedTemplates = emptyMap(), screenshotByDefault = false,
        fields = fields.toMap(), overwriteModes = overwriteModes.toMap(),
    )

    /** Whether notes can be added now: AnkiDroid is ready and [language]'s note type still exists. */
    suspend fun ready(language: Language): Boolean {
        val settings = settings(language)
        val modelId = settings.modelId ?: return false
        return settings.configured && anki.availability() == AnkiAvailability.READY && fieldNames(modelId).isNotEmpty()
    }

    /**
     * Checks the configured deck, note type and fields against AnkiDroid. Asks AnkiDroid every time, so it is meant
     * for occasional checks (screen resume, popup opening), not for every note.
     */
    suspend fun status(language: Language): AnkiStatus {
        val settings = settings(language)
        val modelId = settings.modelId
        if (!settings.configured || modelId == null) return AnkiStatus.NotConfigured
        when (anki.availability()) {
            AnkiAvailability.NOT_INSTALLED -> return AnkiStatus.Broken(AnkiProblem.NOT_INSTALLED)
            AnkiAvailability.NO_PERMISSION -> return AnkiStatus.Broken(AnkiProblem.NO_PERMISSION)
            AnkiAvailability.READY -> Unit
        }
        val fields = anki.fields(modelId)
        synchronized(fieldCache) { if (fields.isEmpty()) fieldCache.remove(modelId) else fieldCache[modelId] = fields }
        if (fields.isEmpty()) return AnkiStatus.Broken(AnkiProblem.MODEL_MISSING)
        val decks = anki.decks()
        // An empty list means AnkiDroid did not answer, not that every deck is gone.
        if (decks.isNotEmpty() && decks.none { it.id == settings.deckId }) return AnkiStatus.Broken(AnkiProblem.DECK_MISSING)
        if (settings.fields.keys != fields.toSet()) return AnkiStatus.Broken(AnkiProblem.FIELDS_CHANGED)
        return AnkiStatus.Ready
    }

    /**
     * Asks AnkiDroid about every part of [language]'s setup, for the connection check on the Anki page. Only reads:
     * nothing is added to the collection. Items that need a failed one (decks need access) are not checked.
     */
    suspend fun check(language: Language): AnkiCheck {
        when (anki.availability()) {
            AnkiAvailability.NOT_INSTALLED -> return AnkiCheck(installed = CheckOutcome.FAILED)
            AnkiAvailability.NO_PERMISSION -> return AnkiCheck(installed = CheckOutcome.PASSED, access = CheckOutcome.FAILED)
            AnkiAvailability.READY -> Unit
        }
        val settings = settings(language)
        val deckId = settings.deckId
        val modelId = settings.modelId
        val decks = if (deckId != null) anki.decks() else emptyList()
        val deck = decks.firstOrNull { it.id == deckId }
        val models = if (modelId != null) anki.models() else emptyList()
        val fields = if (modelId != null) anki.fields(modelId) else emptyList()
        if (modelId != null) {
            synchronized(fieldCache) { if (fields.isEmpty()) fieldCache.remove(modelId) else fieldCache[modelId] = fields }
        }
        val model = when {
            modelId == null -> CheckOutcome.NOT_CHOSEN
            fields.isNotEmpty() -> CheckOutcome.PASSED
            models.isEmpty() -> CheckOutcome.NO_ANSWER
            else -> CheckOutcome.FAILED
        }
        val missing = settings.fields.keys.filter { it !in fields }
        val new = fields.filter { it !in settings.fields.keys }
        return AnkiCheck(
            installed = CheckOutcome.PASSED,
            access = CheckOutcome.PASSED,
            deck = when {
                deckId == null -> CheckOutcome.NOT_CHOSEN
                deck != null -> CheckOutcome.PASSED
                // AnkiDroid always has a deck; an empty list means it did not answer.
                decks.isEmpty() -> CheckOutcome.NO_ANSWER
                else -> CheckOutcome.FAILED
            },
            model = model,
            fields = when {
                model != CheckOutcome.PASSED -> CheckOutcome.NOT_CHECKED
                missing.isEmpty() && new.isEmpty() -> CheckOutcome.PASSED
                else -> CheckOutcome.FAILED
            },
            deckName = deck?.name ?: settings.deckName,
            modelName = models.firstOrNull { it.id == modelId }?.name ?: settings.modelName,
            missingFields = if (model == CheckOutcome.PASSED) missing else emptyList(),
            newFields = if (model == CheckOutcome.PASSED) new else emptyList(),
        )
    }

    /** Markers used by any field template; callers skip expensive markers (audio, screenshot) that are unused. */
    suspend fun usedMarkers(language: Language): Set<String> =
        settings(language).fields.values.flatMapTo(mutableSetOf()) { FieldTemplate.markersIn(it) }

    /** Markers of the first field, the one the duplicate check compares. */
    suspend fun duplicateCheckMarkers(language: Language): Set<String> {
        val settings = settings(language)
        val modelId = settings.modelId ?: return emptySet()
        val first = fieldNames(modelId).firstOrNull() ?: return emptySet()
        return FieldTemplate.markersIn(settings.fields[first].orEmpty())
    }

    /** Existing notes a note with these values would duplicate; empty when Anki is unavailable or unconfigured. */
    suspend fun duplicateIds(language: Language, values: Map<String, String>): List<Long> {
        val settings = settings(language)
        if (!settings.configured || !settings.duplicateCheck || anki.availability() != AnkiAvailability.READY) {
            return emptyList()
        }
        val existing = duplicates(settings, values)
        if (settings.duplicateBehavior != DuplicateBehavior.OVERWRITE) return existing.map { it.id }
        val target = resolveUpdateTarget(settings, existing) ?: return emptyList()
        return listOf(target.id)
    }

    /**
     * Returns true if an existing duplicate note would be updated by [DuplicateBehavior.OVERWRITE] and
     * all fields containing `{sentence-audio}` use [OverwriteMode.KEEP] with existing non-blank content.
     */
    suspend fun shouldSkipSentenceAudioForDuplicate(language: Language, values: Map<String, String>): Boolean =
        shouldSkipSentenceAudioForDuplicate(freezeTarget(language), values)

    suspend fun shouldSkipSentenceAudioForDuplicate(target: NoteTarget, values: Map<String, String>): Boolean {
        val settings = target.settings
        if (!settings.configured || !settings.duplicateCheck || settings.duplicateBehavior != DuplicateBehavior.OVERWRITE) {
            return false
        }
        val existing = duplicates(settings, values)
        val target = resolveUpdateTarget(settings, existing) ?: return false
        val modelId = settings.modelId ?: return false
        val names = fieldNames(modelId)
        val audioFieldIndices = names.mapIndexedNotNull { index, name ->
            val template = settings.fields[name].orEmpty()
            if (FieldTemplate.SENTENCE_AUDIO in FieldTemplate.markersIn(template)) index else null
        }
        if (audioFieldIndices.isEmpty()) return false
        return audioFieldIndices.all { index ->
            val name = names[index]
            val mode = settings.overwriteModes[name] ?: OverwriteMode.COALESCE
            val existingValue = target.fields.getOrElse(index) { "" }
            existingValue.isNotBlank() && (mode == OverwriteMode.SKIP || mode == OverwriteMode.COALESCE)
        }
    }

    /** Picks the existing note that [DuplicateBehavior.OVERWRITE] would update, or null if a new note would be added. */
    private suspend fun resolveUpdateTarget(settings: AnkiSettings, existing: List<ExistingNote>): ExistingNote? {
        val modelId = settings.modelId ?: return null
        val deckId = settings.deckId ?: return null
        val matchingModelNotes = existing.filter { it.modelId == modelId }
        val targetDecks = targetDeckIds(settings, deckId)
        val inDeckNote = matchingModelNotes.filter { note -> note.deckIds.any { it in targetDecks } }
            .maxByOrNull { it.id }
        return when {
            inDeckNote != null -> inDeckNote
            settings.duplicateScope == DuplicateScope.COLLECTION && !settings.duplicateScopeDeckOnly ->
                matchingModelNotes.maxByOrNull { it.id }
            else -> null
        }
    }

    /**
     * Adds a note with [language]'s settings (deck, note type, templates).
     *
     * @param force add a new note even if the settings prevent duplicates.
     */
    suspend fun add(language: Language, request: NoteRequest, force: Boolean = false): AddResult =
        add(freezeTarget(language), request, force)

    /** [onWriteAttempt] runs on the caller's dispatcher immediately before a collection mutation attempt. */
    suspend fun add(target: NoteTarget, request: NoteRequest, force: Boolean = false, onWriteAttempt: () -> Unit = {}): AddResult {
        return when (val prepared = prepare(target, request, force)) {
            is NotePreparationResult.Ready -> write(prepared.note, onWriteAttempt)
            is NotePreparationResult.Stopped -> prepared.result
            is NotePreparationResult.MediaFailed -> AddResult.Failed("Failed to transfer note media to Anki")
        }
    }

    /** Transfers media and renders a note without attempting a collection mutation. */
    suspend fun prepare(target: NoteTarget, request: NoteRequest, force: Boolean = false): NotePreparationResult =
        preparation(target, request, force).prepare()

    /** Retains successful media copies across safe pre-note retries for these frozen inputs. */
    fun preparation(target: NoteTarget, request: NoteRequest, force: Boolean = false): NotePreparation =
        NotePreparation(this, target, request.copy(values = request.values.toMap()), force)

    internal suspend fun prepare(preparation: NotePreparation, sentenceAudio: SentenceAudioAttachment): NotePreparationResult {
        val target = preparation.target
        val request = preparation.request.copy(sentenceAudio = sentenceAudio)
        val force = preparation.force
        currentCoroutineContext().ensureActive()
        if (!isCurrent(target)) return NotePreparationResult.Stopped(AddResult.ConfigurationChanged)
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return NotePreparationResult.Stopped(AddResult.Unavailable(availability))
        val settings = target.settings
        val modelId = settings.modelId
        val deckId = settings.deckId
        if (!settings.configured || modelId == null || deckId == null) return NotePreparationResult.Stopped(AddResult.NotConfigured)

        return runCatching {
            val names = anki.fields(modelId)
            synchronized(fieldCache) { if (names.isEmpty()) fieldCache.remove(modelId) else fieldCache[modelId] = names }
            if (names.isEmpty() || settings.fields.keys != names.toSet() || anki.decks().none { it.id == deckId }) {
                return NotePreparationResult.Stopped(AddResult.NotConfigured)
            }
            val existing = if (settings.duplicateCheck && !force) duplicates(settings, request.values) else emptyList()
            if (existing.isNotEmpty() && settings.duplicateBehavior == DuplicateBehavior.PREVENT) {
                return NotePreparationResult.Stopped(AddResult.Duplicate(existing.map { it.id }))
            }

            val values = request.values.toMutableMap()
            val used = target.markers
            if ("screenshot" in used && request.screenshot != null) {
                copyMedia(preparation, NoteMediaRole.SCREENSHOT, request.screenshot, "screenlate_${System.currentTimeMillis()}", AnkiDroid.MediaKind.IMAGE)
                    ?.let { values["screenshot"] = it }
            }
            if ("audio" in used && request.audio != null) {
                val name = "screenlate_${request.audio.file.nameWithoutExtension}"
                copyMedia(preparation, NoteMediaRole.PRONUNCIATION, request.audio.file, name, AnkiDroid.MediaKind.AUDIO)?.let { values["audio"] = it }
            }
            if (request.sentenceAudio is SentenceAudioAttachment.Failed) {
                return NotePreparationResult.MediaFailed(NoteMediaRole.SENTENCE)
            }
            if (FieldTemplate.SENTENCE_AUDIO in used && request.sentenceAudio is SentenceAudioAttachment.Attached) {
                val file = request.sentenceAudio.file
                val name = "screenlate_${file.nameWithoutExtension}"
                val mediaRef = copyMedia(preparation, NoteMediaRole.SENTENCE, file, name, AnkiDroid.MediaKind.AUDIO)
                    ?: return NotePreparationResult.MediaFailed(NoteMediaRole.SENTENCE)
                values[FieldTemplate.SENTENCE_AUDIO] = mediaRef
            }
            // Known markers without a value (no sentence, no audio, a dictionary without an entry for this term)
            // must not leak into the card as literal text.
            for (marker in used) {
                if (FieldTemplate.isKnown(marker)) values.putIfAbsent(marker, "")
            }

            val fields = names.map { name -> FieldTemplate.render(settings.fields[name].orEmpty(), values) }
            val tags = settings.tags.split(' ', ',').filter { it.isNotBlank() }.toSet()
            val targetNote = if (settings.duplicateBehavior == DuplicateBehavior.OVERWRITE) {
                resolveUpdateTarget(settings, existing)
            } else {
                null
            }

            val output = if (targetNote != null) {
                names.mapIndexed { index, name ->
                    val template = settings.fields[name].orEmpty()
                    // Without audio update: preserve existing sentence-audio field(s) rather than writing an empty Replace value.
                    if (request.sentenceAudio !is SentenceAudioAttachment.Attached && FieldTemplate.SENTENCE_AUDIO in FieldTemplate.markersIn(template)) {
                        targetNote.fields.getOrElse(index) { "" }
                    } else {
                        val mode = settings.overwriteModes[name] ?: OverwriteMode.COALESCE
                        mode.apply(targetNote.fields.getOrElse(index) { "" }, fields[index])
                    }
                }
            } else {
                fields
            }
            if (!isCurrent(target)) return NotePreparationResult.Stopped(AddResult.ConfigurationChanged)
            NotePreparationResult.Ready(PreparedNote(preparation, names.toList(), output.toList(), tags.toSet(), targetNote?.id))
        }.getOrElse {
            if (it is CancellationException) throw it
            if (it is MediaTransferException) return NotePreparationResult.MediaFailed(it.role)
            NotePreparationResult.Stopped(AddResult.Failed(it.message ?: it.javaClass.simpleName))
        }
    }

    private class MediaTransferException(val role: NoteMediaRole) : Exception("Anki media transfer failed")

    private suspend fun copyMedia(preparation: NotePreparation, role: NoteMediaRole, file: File, name: String, kind: AnkiDroid.MediaKind): String? {
        preparation.copied[role]?.takeIf { it.first == file }?.let { return it.second }
        return try {
            anki.addMedia(file, name, kind)?.also { preparation.copied[role] = file to it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { throw MediaTransferException(role) }
    }

    /** Rechecks the destination, then submits this preparation once. Never retries a provider mutation. */
    suspend fun write(note: PreparedNote, onWriteAttempt: () -> Unit = {}): AddResult {
        currentCoroutineContext().ensureActive()
        if (note.owner !== this || !note.claim()) return AddResult.Rejected
        return runCatching {
            if (!isCurrent(note.target)) return AddResult.ConfigurationChanged
            val availability = anki.availability()
            if (availability != AnkiAvailability.READY) return AddResult.Unavailable(availability)
            val settings = note.target.settings
            val model = checkNotNull(settings.modelId)
            val deck = checkNotNull(settings.deckId)
            if (anki.fields(model) != note.names) return AddResult.NotConfigured
            val decks = anki.decks()
            if (decks.none { it.id == deck }) return AddResult.NotConfigured
            if (!beginMutation(note.target, onWriteAttempt)) return AddResult.ConfigurationChanged
            if (note.updateId != null) {
                if (anki.updateNote(note.updateId, note.fields, note.tags)) AddResult.Updated(note.updateId) else AddResult.Rejected
            } else {
                anki.addNote(model, deck, note.fields, note.tags)?.let { AddResult.Added(it) } ?: AddResult.Rejected
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            AddResult.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    private suspend fun beginMutation(target: NoteTarget, onWriteAttempt: () -> Unit): Boolean {
        if (!isCurrent(target)) return false
        currentCoroutineContext().ensureActive()
        onWriteAttempt()
        return true
    }

    private suspend fun targetDeckIds(settings: AnkiSettings, deckId: Long): Set<Long> {
        if (settings.duplicateScope != DuplicateScope.DECK_ROOT) return setOf(deckId)
        val decks = anki.decks()
        val deckName = decks.firstOrNull { it.id == deckId }?.name ?: return setOf(deckId)
        val root = deckName.substringBefore(DECK_SEPARATOR)
        return decks.filter { it.name == root || it.name.startsWith(root + DECK_SEPARATOR) }.mapTo(mutableSetOf()) { it.id }
    }

    private suspend fun duplicates(settings: AnkiSettings, values: Map<String, String>): List<ExistingNote> {
        val modelId = settings.modelId ?: return emptyList()
        val deckId = settings.deckId ?: return emptyList()
        val firstFieldName = fieldNames(modelId).firstOrNull() ?: return emptyList()
        val template = settings.fields[firstFieldName].orEmpty()
        // As when adding: a known marker without a value is empty, not a literal "{marker}" to search for.
        val missing = FieldTemplate.markersIn(template).filter { FieldTemplate.isKnown(it) && it !in values }
        val firstField = FieldTemplate.render(template, values + missing.associateWith { "" })
        if (firstField.isBlank()) return emptyList()
        val models = if (settings.duplicateAllModels) anki.models().map { it.id } else listOf(modelId)
        return anki.findDuplicates(firstField, models, settings.duplicateScope, deckId)
    }

    private suspend fun fieldNames(modelId: Long): List<String> =
        synchronized(fieldCache) { fieldCache[modelId] } ?: anki.fields(modelId).also { fields ->
            // A missing note type is asked again next time: the user may be fixing the settings right now.
            if (fields.isNotEmpty()) synchronized(fieldCache) { fieldCache[modelId] = fields }
        }

    /** Forgets cached note type fields, e.g. after the user edited the note type. */
    fun invalidate() {
        synchronized(fieldCache) { fieldCache.clear() }
    }

    companion object {
        const val DECK_SEPARATOR = "::"
    }
}
