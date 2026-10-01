package com.vpr.screenlate.dictionary.api.imports

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    private val mutableError = MutableStateFlow<String?>(null)

    /** Why the last reset failed, until [dismissError]; such a reset is not tried again by itself. */
    val error: StateFlow<String?> = mutableError

    /** Whether a reset was given up after the app died during it twice; until [dismissGaveUp] or a reset succeeds. */
    val gaveUp: Flow<Boolean> = preferences.data.map { it[GAVE_UP] == true }

    /** Whether the start screen has yet to tell once about [gaveUp]. */
    val gaveUpUntold: Flow<Boolean> = preferences.data.map { it[GAVE_UP] == true && it[GAVE_UP_TOLD] != true }

    /**
     * Cancels running and queued imports, deletes every dictionary and installs the bundled ones of this version, which
     * come back in their shipped versions with the default order, switches and sort dictionary; returns once they are
     * installed. Waits for a running import to stop first, which can take as long as its current step. A reset the
     * process died in is finished by [resumeInterrupted]; one that failed is reported in [error].
     */
    suspend fun reset() = run(attempt = 1)

    /**
     * Finishes a reset that the process died in before the bundled dictionaries were queued, once; when the process
     * died in that run too, gives up and sets [gaveUp]. Call at startup.
     */
    suspend fun resumeInterrupted() {
        val attempts = preferences.data.first()[ATTEMPTS]
        when (resumeAfter(attempts)) {
            Resume.NONE -> Unit
            Resume.RETRY -> {
                Log.w(TAG, "The app died during the dictionary reset; running it again")
                run(attempt = attempts!! + 1)
            }
            Resume.GIVE_UP -> {
                Log.w(TAG, "The app died during the dictionary reset $attempts times; not tried again")
                preferences.edit {
                    it.remove(ATTEMPTS)
                    it[GAVE_UP] = true
                    it.remove(GAVE_UP_TOLD)
                }
            }
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    suspend fun dismissGaveUp() {
        Log.i(TAG, "Given-up reset dismissed")
        preferences.edit {
            it.remove(GAVE_UP)
            it.remove(GAVE_UP_TOLD)
        }
    }

    suspend fun markGaveUpTold() {
        preferences.edit { it[GAVE_UP_TOLD] = true }
    }

    private suspend fun run(attempt: Int) {
        // A second tap while one runs does nothing.
        if (!mutablePhase.compareAndSet(expect = null, update = Phase.DELETING)) {
            Log.i(TAG, "A reset is already running")
            return
        }
        mutableError.value = null
        val started = System.currentTimeMillis()
        Log.i(TAG, "Reset started, run $attempt")
        try {
            preferences.edit { it[ATTEMPTS] = attempt }
            imports.cancelAll()
            // Forgotten before the deletion: if the app stops right after it, the next start installs every archive,
            // while records of installs would make it take the empty registry for dictionaries the user deleted.
            bundled.reset()
            repository.deleteAll()
            val install = imports.installBundled()
            Log.i(TAG, "Dictionaries deleted in ${System.currentTimeMillis() - started} ms; installing the bundled ones")
            // From here the import queue finishes the job on its own, also after the process dies.
            preferences.edit {
                it.remove(ATTEMPTS)
                it.remove(GAVE_UP)
                it.remove(GAVE_UP_TOLD)
            }
            mutablePhase.value = Phase.INSTALLING
            imports.awaitFinished(install)
            Log.i(TAG, "Reset done in ${System.currentTimeMillis() - started} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Dictionary reset failed", e)
            mutableError.value = e.message ?: e.javaClass.simpleName
            // The user sees the error and may start the reset again; the next start does not (owner).
            try {
                preferences.edit { it.remove(ATTEMPTS) }
            } catch (e: Exception) {
                Log.w(TAG, "Forgetting the failed reset failed", e)
            }
        } finally {
            mutablePhase.value = null
        }
    }

    internal enum class Resume { NONE, RETRY, GIVE_UP }

    internal companion object {
        private const val TAG = "DictionaryReset"

        /** Runs of a reset the process dies in: the user's, and one more at the next start (owner). */
        const val MAX_ATTEMPTS = 2

        /** Runs of a reset so far, while one has not got as far as queueing the bundled install. */
        private val ATTEMPTS = intPreferencesKey("dictionary_reset_attempts")
        private val GAVE_UP = booleanPreferencesKey("dictionary_reset_gave_up")
        private val GAVE_UP_TOLD = booleanPreferencesKey("dictionary_reset_gave_up_told")

        /** What a start does about a reset that the process died in after [attempts] runs (null: none). */
        fun resumeAfter(attempts: Int?): Resume = when {
            attempts == null -> Resume.NONE
            attempts >= MAX_ATTEMPTS -> Resume.GIVE_UP
            else -> Resume.RETRY
        }
    }
}
