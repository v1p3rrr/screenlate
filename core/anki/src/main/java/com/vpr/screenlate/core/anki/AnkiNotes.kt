package com.vpr.screenlate.core.anki

import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything needed to fill one note. [values] holds the text markers; media markers (`{screenshot}`,
 * `{audio}`) are filled from the files after they are copied into AnkiDroid.
 */
data class NoteRequest(
    val values: Map<String, String>,
    val screenshot: File? = null,
    val audio: AudioClip? = null,
)

sealed interface AddResult {
    data class Added(val noteId: Long) : AddResult

    data class Updated(val noteId: Long) : AddResult

    /** Blocked by the duplicate check with [DuplicateBehavior.PREVENT]; [noteIds] are the existing notes. */
    data class Duplicate(val noteIds: List<Long>) : AddResult

    data object NotConfigured : AddResult

    data class Unavailable(val availability: AnkiAvailability) : AddResult

    data class Failed(val message: String) : AddResult
}

/** Builds notes from field templates and adds them to AnkiDroid with the configured duplicate handling. */
@Singleton
class AnkiNotes @Inject constructor(
    private val anki: AnkiDroid,
    private val settingsRepository: AnkiSettingsRepository,
) {
    private val fieldCache = mutableMapOf<Long, List<String>>()

    suspend fun settings(): AnkiSettings = settingsRepository.current()

    /** Whether notes can be added now: AnkiDroid is ready and the configured note type still exists. */
    suspend fun ready(): Boolean {
        val settings = settings()
        val modelId = settings.modelId ?: return false
        return settings.configured && anki.availability() == AnkiAvailability.READY && fieldNames(modelId).isNotEmpty()
    }

    /** Markers used by any field template; callers skip expensive markers (audio, screenshot) that are unused. */
    suspend fun usedMarkers(): Set<String> =
        settings().fields.values.flatMapTo(mutableSetOf()) { FieldTemplate.markersIn(it) }

    /** Markers of the first field, the one the duplicate check compares. */
    suspend fun duplicateCheckMarkers(): Set<String> {
        val settings = settings()
        val modelId = settings.modelId ?: return emptySet()
        val first = fieldNames(modelId).firstOrNull() ?: return emptySet()
        return FieldTemplate.markersIn(settings.fields[first].orEmpty())
    }

    /** Existing notes a note with these values would duplicate; empty when Anki is unavailable or unconfigured. */
    suspend fun duplicateIds(values: Map<String, String>): List<Long> {
        val settings = settings()
        if (!settings.configured || !settings.duplicateCheck || anki.availability() != AnkiAvailability.READY) {
            return emptyList()
        }
        return duplicates(settings, values).map { it.id }
    }

    /** @param force add a new note even if the settings prevent duplicates. */
    suspend fun add(request: NoteRequest, force: Boolean = false): AddResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return AddResult.Unavailable(availability)
        val settings = settings()
        val modelId = settings.modelId
        val deckId = settings.deckId
        if (!settings.configured || modelId == null || deckId == null) return AddResult.NotConfigured

        if (fieldNames(modelId).isEmpty()) return AddResult.NotConfigured
        return runCatching {
            val existing = if (settings.duplicateCheck && !force) duplicates(settings, request.values) else emptyList()
            if (existing.isNotEmpty() && settings.duplicateBehavior == DuplicateBehavior.PREVENT) {
                return AddResult.Duplicate(existing.map { it.id })
            }

            val values = request.values.toMutableMap()
            val used = usedMarkers()
            if ("screenshot" in used && request.screenshot != null) {
                anki.addMedia(request.screenshot, "screenlate_${System.currentTimeMillis()}", AnkiDroid.MediaKind.IMAGE)
                    ?.let { values["screenshot"] = it }
            }
            if ("audio" in used && request.audio != null) {
                val name = "screenlate_${request.audio.file.nameWithoutExtension}"
                anki.addMedia(request.audio.file, name, AnkiDroid.MediaKind.AUDIO)?.let { values["audio"] = it }
            }
            // Known markers without a value (no sentence, no audio, a dictionary without an entry for this term)
            // must not leak into the card as literal text.
            for (marker in used) {
                if (FieldTemplate.isKnown(marker)) values.putIfAbsent(marker, "")
            }

            val names = fieldNames(modelId)
            val fields = names.map { name -> FieldTemplate.render(settings.fields[name].orEmpty(), values) }
            val tags = settings.tags.split(' ', ',').filter { it.isNotBlank() }.toSet()
            val overwrite = existing.firstOrNull { it.modelId == modelId }
                ?.takeIf { settings.duplicateBehavior == DuplicateBehavior.OVERWRITE }
            if (overwrite != null) {
                val merged = names.mapIndexed { index, name ->
                    val mode = settings.overwriteModes[name] ?: OverwriteMode.COALESCE
                    mode.apply(overwrite.fields.getOrElse(index) { "" }, fields[index])
                }
                if (anki.updateNote(overwrite.id, merged, tags)) AddResult.Updated(overwrite.id)
                else AddResult.Failed("AnkiDroid did not update the note")
            } else {
                anki.addNote(modelId, deckId, fields, tags)?.let { AddResult.Added(it) }
                    ?: AddResult.Failed("AnkiDroid did not add the note")
            }
        }.getOrElse { AddResult.Failed(it.message ?: it.javaClass.simpleName) }
    }

    private suspend fun duplicates(settings: AnkiSettings, values: Map<String, String>): List<ExistingNote> {
        val modelId = settings.modelId ?: return emptyList()
        val deckId = settings.deckId ?: return emptyList()
        val firstFieldName = fieldNames(modelId).firstOrNull() ?: return emptyList()
        val firstField = FieldTemplate.render(settings.fields[firstFieldName].orEmpty(), values)
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
}
