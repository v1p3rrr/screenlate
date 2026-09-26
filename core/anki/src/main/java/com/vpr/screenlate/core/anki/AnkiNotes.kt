package com.vpr.screenlate.core.anki

import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.note.FieldTemplate
import com.vpr.screenlate.core.anki.settings.AnkiSettings
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
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

    /** Blocked by the duplicate check with [DuplicateBehavior.PREVENT]. */
    data object Duplicate : AddResult

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

    /** Markers used by any field template; callers skip expensive markers (audio, screenshot) that are unused. */
    suspend fun usedMarkers(): Set<String> =
        settings().fields.values.flatMapTo(mutableSetOf()) { FieldTemplate.markersIn(it) }

    /** Whether a note with these values would be a duplicate. False when Anki is unavailable or unconfigured. */
    suspend fun isDuplicate(values: Map<String, String>): Boolean {
        val settings = settings()
        if (!settings.configured || !settings.duplicateCheck || anki.availability() != AnkiAvailability.READY) return false
        return duplicates(settings, values).isNotEmpty()
    }

    suspend fun add(request: NoteRequest): AddResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return AddResult.Unavailable(availability)
        val settings = settings()
        val modelId = settings.modelId
        val deckId = settings.deckId
        if (!settings.configured || modelId == null || deckId == null) return AddResult.NotConfigured

        return runCatching {
            val existing = if (settings.duplicateCheck) duplicates(settings, request.values) else emptyList()
            if (existing.isNotEmpty() && settings.duplicateBehavior == DuplicateBehavior.PREVENT) {
                return AddResult.Duplicate
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
                if (marker in FieldTemplate.MARKERS || marker.startsWith(FieldTemplate.GLOSSARY_PREFIX)) {
                    values.putIfAbsent(marker, "")
                }
            }

            val fields = fieldNames(modelId).map { name -> FieldTemplate.render(settings.fields[name].orEmpty(), values) }
            val tags = settings.tags.split(' ', ',').filter { it.isNotBlank() }.toSet()
            val overwrite = existing.firstOrNull { it.modelId == modelId }
                ?.takeIf { settings.duplicateBehavior == DuplicateBehavior.OVERWRITE }
            if (overwrite != null) {
                if (anki.updateNote(overwrite.id, fields, tags)) AddResult.Updated(overwrite.id)
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
            synchronized(fieldCache) { fieldCache[modelId] = fields }
        }

    /** Forgets cached note type fields, e.g. after the user edited the note type. */
    fun invalidate() {
        synchronized(fieldCache) { fieldCache.clear() }
    }
}
