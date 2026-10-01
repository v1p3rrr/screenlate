package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Returns the dictionaries to a fresh install. */
@Singleton
class DictionaryReset @Inject constructor(
    private val imports: DictionaryImports,
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
) {
    private val mutableRunning = MutableStateFlow(false)

    /** Whether a reset is stopping imports or deleting dictionaries; the bundled installs then show as imports. */
    val running: StateFlow<Boolean> = mutableRunning

    /**
     * Cancels running and queued imports, deletes every dictionary and queues the bundled ones of this version, which
     * come back in their shipped versions with the default order, switches and sort dictionary. Waits for a running
     * import to stop first, which can take as long as its current step.
     */
    suspend fun reset() {
        // A second tap while one runs does nothing.
        if (!mutableRunning.compareAndSet(expect = false, update = true)) return
        try {
            imports.cancelAll()
            // Forgotten before the deletion: if the app stops right after it, the next start installs every archive,
            // while records of installs would make it take the empty registry for dictionaries the user deleted.
            bundled.reset()
            repository.deleteAll()
            imports.installBundled()
        } finally {
            mutableRunning.value = false
        }
    }
}
