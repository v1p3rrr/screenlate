package com.vpr.screenlate.dictionary.api.registry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An imported dictionary as known to the app. The converted data lives in [directory] under the dictionary
 * storage root (see `DictionaryStorage`).
 *
 * A dictionary may carry several kinds of data (for example terms and frequencies); the counts tell which
 * lookups it takes part in, [kind] is the primary one for display.
 *
 * @property priority lookup and display order; lower comes first.
 * @property frequencyMode Yomitan `frequencyMode`: `rank-based` or `occurrence-based`.
 * @property indexUrl Yomitan `indexUrl`, used to check for updates.
 * @property bundled whether the dictionary was installed from the APK.
 */
@Entity(tableName = "dictionaries", indices = [Index(value = ["title"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val revision: String,
    val kind: DictionaryKind,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val frequencyMode: String?,
    val enabled: Boolean,
    val priority: Int,
    val directory: String,
    val termCount: Long,
    val frequencyCount: Long,
    val pitchCount: Long,
    val kanjiCount: Long,
    val mediaCount: Long,
    val isUpdatable: Boolean,
    val indexUrl: String?,
    val downloadUrl: String?,
    val author: String?,
    val url: String?,
    val description: String?,
    val attribution: String?,
    val bundled: Boolean,
    val importedAt: Long,
)
