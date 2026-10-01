package com.vpr.screenlate.dictionary.api.imports

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Returns the dictionaries to a fresh install. */
@Singleton
class DictionaryReset @Inject constructor(
    private val imports: DictionaryImports,
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
    private val preferences: DataStore<Preferences>,
) {
    enum class Phase {
        /** Stopping imports and deleting the dictionaries. */
        DELETING,

        /** Installing the bundled dictionaries; their import shows as a task. */
        INSTALLING,
    }

    private val mutablePhase = MutableStateFlow<Phase?>(null)

    /** The step of the running reset; null when none runs. */
    val phase: StateFlow<Phase?> = mutablePhase

    /**
     * Cancels running and queued imports, deletes every dictionary and installs the bundled ones of this version, which
     * come back in their shipped versions with the default order, switches and sort dictionary; returns once they are
     * installed. Waits for a running import to stop first, which can take as long as its current step. A reset the
     * process died in is finished by [resumeInterrupted].
     */
    suspend fun reset() {
        // A second tap while one runs does nothing.
        if (!mutablePhase.compareAndSet(expect = null, update = Phase.DELETING)) return
        try {
            preferences.edit { it[PENDING] = true }
            imports.cancelAll()
            // Forgotten before the deletion: if the app stops right after it, the next start installs every archive,
            // while records of installs would make it take the empty registry for dictionaries the user deleted.
            bundled.reset()
            repository.deleteAll()
            val install = imports.installBundled()
            // From here the import queue finishes the job on its own, also after the process dies.
            preferences.edit { it.remove(PENDING) }
            mutablePhase.value = Phase.INSTALLING
            imports.awaitFinished(install)
        } finally {
            mutablePhase.value = null
        }
    }

    /** Finishes a reset that the process died in before the bundled dictionaries were queued. Call at startup. */
    suspend fun resumeInterrupted() {
        if (preferences.data.first()[PENDING] == true) reset()
    }

    private companion object {
        val PENDING = booleanPreferencesKey("dictionary_reset_pending")
    }
}
