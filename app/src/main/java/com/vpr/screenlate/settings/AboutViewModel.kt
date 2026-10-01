package com.vpr.screenlate.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.logs.LogExport
import com.vpr.screenlate.logs.SavedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Installed dictionaries for their attributions, and the log file for "Share logs". */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: DictionaryRepository,
) : ViewModel() {
    val dictionaries: StateFlow<List<DictionaryEntity>> = repository.dictionaries
        .map { list -> list.sortedBy { it.priority } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.cachedDictionaries?.sortedBy { it.priority }.orEmpty(),
        )

    /** A share intent for a fresh log file. */
    suspend fun logShareIntent(): Intent = LogExport.shareIntent(context, LogExport.write(context))

    /** Saves a fresh log to Downloads. */
    suspend fun saveLog(): SavedLog = LogExport.saveToDownloads(context)
}
