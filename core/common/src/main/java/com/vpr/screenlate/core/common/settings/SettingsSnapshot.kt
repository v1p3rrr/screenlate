package com.vpr.screenlate.core.common.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * The settings store with the settings as last read kept at hand, so a screen draws its first frame with the user's
 * values instead of defaults that change a moment later. Reads and writes go to [delegate] unchanged; the snapshot
 * follows them and is only ever a first value.
 */
internal class SnapshotDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> by delegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: StateFlow<Preferences?> = delegate.data
        .catch { /* A read error reaches the screens through their own flows. */ }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

/** The settings as last read; null before the first read after the app started, or for a store without a snapshot. */
fun DataStore<Preferences>.snapshot(): Preferences? = (this as? SnapshotDataStore)?.snapshot?.value

/** [read] applied to the settings as last read ([snapshot]); null before the first read. */
fun <T> DataStore<Preferences>.cached(read: (Preferences) -> T): T? = snapshot()?.let(read)
