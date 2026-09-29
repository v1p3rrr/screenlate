package com.vpr.screenlate.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(private val manager: BackupManager) : ViewModel() {
    val state: StateFlow<BackupState> = manager.state

    private val mutableChecked = MutableStateFlow<Set<BackupSection>>(emptySet())

    /** Sections chosen for restoring; all the backup holds at first. */
    val checked: StateFlow<Set<BackupSection>> = mutableChecked

    init {
        viewModelScope.launch {
            var loaded: Uri? = null
            manager.state.collect { state ->
                if (state is BackupState.Loaded && state.uri != loaded) mutableChecked.value = state.manifest.sections.toSet()
                loaded = (state as? BackupState.Loaded)?.uri
            }
        }
    }

    fun create(uri: Uri, includeDictionaries: Boolean) = manager.create(uri, includeDictionaries)

    fun open(uri: Uri) = manager.open(uri)

    fun toggle(section: BackupSection) = mutableChecked.update { if (section in it) it - section else it + section }

    fun restore() {
        val loaded = state.value as? BackupState.Loaded ?: return
        manager.restore(loaded.uri, checked.value)
    }

    fun reset() = manager.reset()
}
