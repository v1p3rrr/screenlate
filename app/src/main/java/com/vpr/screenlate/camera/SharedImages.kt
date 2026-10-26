package com.vpr.screenlate.camera

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Images shared to the app, waiting for the Camera page to import them; only the latest one waits. */
@Singleton
class SharedImages @Inject constructor() {
    private val pending = Channel<String>(Channel.CONFLATED)

    /** Content links, each received once. */
    val requests: Flow<String> = pending.receiveAsFlow()

    fun offer(uri: String) {
        pending.trySend(uri)
    }
}
