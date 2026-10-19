package com.vpr.screenlate.dictionaries

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import java.util.UUID
import org.junit.Test

class TaskItemsTest {
    private fun task(name: String, state: ImportTask.State, retries: Int = 0, needsNetwork: Boolean = false) =
        ImportTask(
            UUID.randomUUID(), name, state, percent = null, titles = emptyList(), error = null, retries = retries,
            needsNetwork = needsNetwork,
        )

    @Test
    fun `waiting tasks share one card in the place of the first`() {
        val failed = task("Jiten", ImportTask.State.FAILED)
        val a = task("A", ImportTask.State.QUEUED)
        val running = task("Model", ImportTask.State.DOWNLOADING)
        val retry = task("Jiten", ImportTask.State.QUEUED, retries = 1)
        val b = task("B", ImportTask.State.QUEUED)

        val items = taskItems(listOf(failed, a, running, retry, b))

        assertThat(items).containsExactly(
            TaskItem.One(failed),
            TaskItem.Waiting(listOf(a, b)),
            TaskItem.One(running),
            TaskItem.One(retry),
        ).inOrder()
    }

    @Test
    fun `a single waiting task keeps its own card`() {
        val running = task("Model", ImportTask.State.IMPORTING)
        val a = task("A", ImportTask.State.QUEUED)

        assertThat(taskItems(listOf(running, a))).containsExactly(TaskItem.One(running), TaskItem.One(a)).inOrder()
        assertThat(taskItems(emptyList())).isEmpty()
    }

    @Test
    fun `queued downloads wait for a network only while none is up`() {
        val download = task("Jiten", ImportTask.State.QUEUED, needsNetwork = true)
        val retry = task("Model", ImportTask.State.QUEUED, retries = 1, needsNetwork = true)
        val file = task("File", ImportTask.State.QUEUED)
        val tasks = listOf(download, retry, file)

        assertThat(waitingForNetwork(tasks, online = false)).containsExactly(download.id, retry.id)
        assertThat(waitingForNetwork(tasks, online = true)).isEmpty()
    }
}
