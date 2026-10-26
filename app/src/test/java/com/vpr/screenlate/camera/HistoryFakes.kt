package com.vpr.screenlate.camera

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.HistoryEntry
import com.vpr.screenlate.core.photos.HistoryLimits
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoHistory
import com.vpr.screenlate.core.photos.PhotoImage
import com.vpr.screenlate.core.photos.PhotoRecognition
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

internal fun cameraPreferences() = CameraPreferences(MemoryStore(), LanguageProfiles(MemoryStore()))

internal fun entry(id: Long, bytes: Long = 1_000) = HistoryEntry(id, createdAt = id, language = null, thumbnail = null, bytes = bytes)

/** Only the current photo; the history pages do not capture or recognize. */
internal class CurrentPhoto : PhotoDocuments {
    override val current = MutableStateFlow<PhotoDocument?>(null)
    override suspend fun pinImage(documentId: Long): PhotoImage? = null
    override suspend fun newCaptureFile(): File = error("Not used")
    override suspend fun replaceCurrent(capture: File): PhotoDocument = error("Not used")
    override suspend fun discard(capture: File) = error("Not used")
    override suspend fun clearCurrent() = error("Not used")
    override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? =
        error("Not used")
    override suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean): Boolean = error("Not used")
}

/** A history in memory that marks [current] saved, replaces it or deletes it the way the store does. */
internal class FakeHistory(private val current: MutableStateFlow<PhotoDocument?>) : PhotoHistory {
    override val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val saved = mutableListOf<Long>()
    val opened = mutableListOf<Long>()
    val removed = mutableListOf<Set<Long>>()
    val trimmed = mutableListOf<HistoryLimits>()
    var cleared = 0
    var saveFails = false
    var excess = 0

    override suspend fun save(documentId: Long, limits: HistoryLimits): Boolean {
        if (saveFails) return false
        saved += documentId
        entries.value = listOf(entry(documentId)) + entries.value
        current.value?.takeIf { it.id == documentId }?.let { current.value = it.copy(saved = true) }
        return true
    }

    override suspend fun open(documentId: Long): PhotoDocument? {
        opened += documentId
        return photo(documentId).copy(saved = true).also { current.value = it }
    }

    override suspend fun remove(ids: Set<Long>) {
        removed += ids
        entries.value = entries.value.filterNot { it.id in ids }
        if (current.value?.let { it.saved && it.id in ids } == true) current.value = null
    }

    override suspend fun clear() {
        cleared++
        entries.value = emptyList()
        if (current.value?.saved == true) current.value = null
    }

    override suspend fun excess(limits: HistoryLimits): Int = excess

    override suspend fun trim(limits: HistoryLimits) {
        trimmed += limits
    }
}
