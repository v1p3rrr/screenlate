package com.vpr.screenlate.dictionaries

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import java.util.UUID
import org.junit.Test

class WaitingForNetworkTest {
    private fun task(name: String, state: ImportTask.State, retries: Int = 0, needsNetwork: Boolean = false) =
        ImportTask(
            UUID.randomUUID(), name, state, percent = null, titles = emptyList(), error = null, retries = retries,
            needsNetwork = needsNetwork,
        )

    @Test
    fun `queued downloads wait for a network only while none is up`() {
        val download = task("Jitendex", ImportTask.State.QUEUED, needsNetwork = true)
        val retry = task("JMnedict", ImportTask.State.QUEUED, retries = 1, needsNetwork = true)
        val file = task("File", ImportTask.State.QUEUED)
        val running = task("KANJIDIC", ImportTask.State.DOWNLOADING)
        val tasks = listOf(download, retry, file, running)

        assertThat(waitingForNetwork(tasks, online = false)).containsExactly(download.id, retry.id)
        assertThat(waitingForNetwork(tasks, online = true)).isEmpty()
    }
}
