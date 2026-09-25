package com.vpr.screenlate.dictionary.api.registry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An imported dictionary as known to the app. The dictionary data itself lives in the engine's storage at [path].
 *
 * @property priority display and lookup order; lower comes first.
 * @property indexUrl Yomitan `indexUrl`, used to check for updates.
 * @property bundled whether the dictionary ships inside the APK.
 */
@Entity(tableName = "dictionaries", indices = [Index(value = ["title"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val revision: String,
    val kind: DictionaryKind,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val enabled: Boolean,
    val priority: Int,
    val path: String,
    val indexUrl: String?,
    val downloadUrl: String?,
    val bundled: Boolean,
    val importedAt: Long,
)
