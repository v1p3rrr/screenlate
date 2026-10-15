package com.vpr.screenlate.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
    private val choices: BackupChoiceStore,
) : ViewModel() {
    val state: StateFlow<BackupState> = manager.state

    private val mutableFiles = MutableStateFlow<BackupFiles?>(null)

    /** The fonts and dictionaries a new backup can hold; null until read. */
    val files: StateFlow<BackupFiles?> = mutableFiles

    private val mutableCreateChoice = MutableStateFlow<BackupChoice?>(null)

    /** What the next backup holds: the last choice at first; null until read. */
    val createChoice: StateFlow<BackupChoice?> = mutableCreateChoice

    private val mutableCreateDraft = MutableStateFlow<BackupChoice?>(null)

    /** The choice being made in the dialog before a backup is created; null while the dialog is closed. */
    val createDraft: StateFlow<BackupChoice?> = mutableCreateDraft

    private val mutableRestoreChoice = MutableStateFlow(BackupChoice.ALL)

    /** What is restored; everything the backup holds at first. */
    val restoreChoice: StateFlow<BackupChoice> = mutableRestoreChoice

    init {
        loadFiles()
        viewModelScope.launch { mutableCreateChoice.value = choices.choice.first() }
        viewModelScope.launch {
            var loaded: Uri? = null
            manager.state.collect { state ->
                if (state is BackupState.Loaded && state.uri != loaded) mutableRestoreChoice.value = BackupChoice.ALL
                loaded = (state as? BackupState.Loaded)?.uri
                // A restore can add fonts and dictionaries.
                if (state is BackupState.Restored) loadFiles()
            }
        }
    }

    private fun loadFiles() {
        viewModelScope.launch { mutableFiles.value = manager.files() }
    }

    /** Opens the dialog with the last choice. */
    fun openCreateChoice() {
        mutableCreateDraft.value = mutableCreateChoice.value ?: return
    }

    fun changeCreateDraft(choice: BackupChoice) {
        mutableCreateDraft.value = choice
    }

    fun closeCreateChoice() {
        mutableCreateDraft.value = null
    }

    /** Keeps the dialog's choice for this and later backups and closes the dialog. */
    fun acceptCreateChoice() {
        val choice = mutableCreateDraft.value ?: return
        mutableCreateDraft.value = null
        mutableCreateChoice.value = choice
        viewModelScope.launch { choices.save(choice) }
    }

    fun changeRestoreChoice(choice: BackupChoice) {
        mutableRestoreChoice.value = choice
    }

    fun create(uri: Uri) {
        val choice = mutableCreateChoice.value ?: return
        val files = mutableFiles.value ?: return
        val fonts = choice.visible(files.fonts, files.languages).map { it.key }
        val dictionaries = choice.visible(files.dictionaries, files.languages).map { it.key }
        manager.create(
            uri,
            sections = choice.sections(BackupSection.entries, fonts, dictionaries),
            fontIds = choice.fonts.chosen(fonts),
            dictionaryTitles = choice.dictionaries.chosen(dictionaries),
            languages = choice.chosenLanguages(files.languages),
        )
    }

    fun open(uri: Uri) = manager.open(uri)

    fun restore() {
        val loaded = state.value as? BackupState.Loaded ?: return
        val choice = mutableRestoreChoice.value
        val fonts = choice.visible(loaded.fonts, loaded.languages).map { it.key }
        val dictionaries = choice.visible(loaded.dictionaryFiles, loaded.languages).map { it.key }
        manager.restore(
            loaded.uri,
            sections = choice.sections(loaded.sections, fonts, dictionaries),
            fontIds = choice.fonts.chosen(fonts),
            dictionaryIndices = choice.dictionaries.chosen(dictionaries).mapTo(hashSetOf()) { it.toInt() },
            languages = choice.chosenLanguages(loaded.languages),
        )
    }

    fun reset() = manager.reset()
}
