package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CANCELLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_FREE_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INTERRUPTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NEEDED_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PAUSED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_DOWNLOAD
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class DictionaryImportsTest {
    private val directory: File = Files.createTempDirectory("imports").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun work(
        name: String,
        state: WorkInfo.State,
        order: Long?,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
        archive: String? = null,
        source: String? = null,
    ) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = setOfNotNull(
            DictionaryImports.TAG,
            DictionaryImports.NAME_TAG_PREFIX + name,
            order?.let { DictionaryImports.ORDER_TAG_PREFIX + it },
            archive?.let { DictionaryImports.ARCHIVE_TAG_PREFIX + it },
            source?.let { DictionaryImports.SOURCE_TAG_PREFIX + it },
        ),
        outputData = output,
        progress = progress,
    )

    @Test
    fun `bundled source survives a change in the displayed archive name`() {
        val queued = work("", WorkInfo.State.ENQUEUED, 1, source = "bundled")
        val importing = work("", WorkInfo.State.RUNNING, 2, source = "bundled",
            progress = workDataOf(DictionaryImportWorker.KEY_NAME to "jmdict-english"))
        val oldRunning = work("", WorkInfo.State.RUNNING, 3,
            progress = workDataOf(DictionaryImportWorker.KEY_NAME to "kanjidic", DictionaryImportWorker.KEY_SOURCE to "bundled"))

        val tasks = importTasks(listOf(queued, importing, oldRunning), emptySet())
        assertThat(tasks.map { it.source }).containsExactly("bundled", "bundled", "bundled")
        assertThat(tasks.map { it.name }).containsExactly("", "jmdict-english", "kanjidic").inOrder()
    }

    @Test
    fun `tasks come in queue order, not by their random ids`() {
        val works = listOf(
            work("C", WorkInfo.State.ENQUEUED, order = 1_000_002),
            work("A", WorkInfo.State.SUCCEEDED, order = 1_000_000),
            work("B", WorkInfo.State.RUNNING, order = 1_000_001, progress = workDataOf(KEY_STAGE to STAGE_DOWNLOAD)),
            // Queued before the order was kept.
            work("Old", WorkInfo.State.SUCCEEDED, order = null),
        )

        val tasks = importTasks(works.shuffled())

        assertThat(tasks.map { it.name }).containsExactly("Old", "A", "B", "C").inOrder()
        assertThat(tasks.map { it.state }).containsExactly(
            ImportTask.State.SUCCEEDED,
            ImportTask.State.SUCCEEDED,
            ImportTask.State.DOWNLOADING,
            ImportTask.State.QUEUED,
        ).inOrder()
    }

    @Test
    fun `a finished work with an error is a failed task`() {
        val tasks = importTasks(
            listOf(
                work("Ok", WorkInfo.State.SUCCEEDED, 1, output = workDataOf(KEY_TITLES to arrayOf("Ok [1]"))),
                work("Broken", WorkInfo.State.SUCCEEDED, 2, output = workDataOf(KEY_ERROR to "HTTP 404")),
                work(
                    "Big",
                    WorkInfo.State.SUCCEEDED,
                    3,
                    output = workDataOf(KEY_ERROR to "No space", KEY_NEEDED_BYTES to 300L, KEY_FREE_BYTES to 100L),
                ),
            ),
        )

        assertThat(tasks.map { it.state })
            .containsExactly(ImportTask.State.SUCCEEDED, ImportTask.State.FAILED, ImportTask.State.FAILED).inOrder()
        assertThat(tasks[0].titles).containsExactly("Ok [1]")
        assertThat(tasks[1].error).isEqualTo("HTTP 404")
        assertThat(tasks[2].shortage).isEqualTo(CollectionSpacePlan.NotEnough(300, 100))
    }

    @Test
    fun `cancelled tasks are left out, also before their work has ended`() {
        val running = work("Running", WorkInfo.State.RUNNING, 1)
        val tasks = importTasks(
            listOf(
                running,
                work("Ended", WorkInfo.State.SUCCEEDED, 2, output = workDataOf(KEY_CANCELLED to true)),
                work("Kept", WorkInfo.State.ENQUEUED, 3),
            ),
            cancelled = setOf(running.id.toString()),
        )

        assertThat(tasks.map { it.name }).containsExactly("Kept")
    }

    @Test
    fun `an import the app died during twice is a failed task`() {
        val task = importTasks(
            listOf(work("Big", WorkInfo.State.SUCCEEDED, 1, output = workDataOf(KEY_INTERRUPTED to true))),
        ).single()

        assertThat(task.state).isEqualTo(ImportTask.State.FAILED)
        assertThat(task.interrupted).isTrue()
        assertThat(task.paused).isFalse()
        assertThat(task.error).isNull()
    }

    @Test
    fun `a bundled install the app died during twice says it is paused`() {
        val task = importTasks(
            listOf(work("", WorkInfo.State.SUCCEEDED, 1, output = workDataOf(KEY_INTERRUPTED to true, KEY_PAUSED to true))),
        ).single()

        assertThat(task.state).isEqualTo(ImportTask.State.FAILED)
        assertThat(task.paused).isTrue()
    }

    @Test
    fun `imports a dictionary reset cancelled are left out, not shown as failed`() {
        val tasks = importTasks(
            listOf(
                work("Stopped", WorkInfo.State.CANCELLED, 1),
                work("Failed", WorkInfo.State.FAILED, 2),
                work("Queued", WorkInfo.State.ENQUEUED, 3),
            ),
        )

        assertThat(tasks.map { it.name to it.state }).containsExactly(
            "Failed" to ImportTask.State.FAILED,
            "Queued" to ImportTask.State.QUEUED,
        ).inOrder()
    }

    @Test
    fun `a failed dictionary of a collection does not keep the others out`() = runTest {
        val archives = (1..3).map { File(directory, "$it.zip").apply { writeText("zip") } }
        val imported = mutableListOf<Int>()

        val error = runCatching {
            importEach(archives, titleOf = { "Dict ${it.nameWithoutExtension}" }) { index, _ ->
                imported += index
                if (index == 1) throw DictionaryImportException("empty dictionary")
                "Dict $index"
            }
        }.exceptionOrNull()

        assertThat(imported).containsExactly(0, 1, 2).inOrder()
        assertThat(error).isInstanceOf(DictionaryImportException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("Dict 2: empty dictionary")
        assertThat(archives.none { it.exists() }).isTrue()
        assertThat(importEach(emptyList(), { null }) { _, _ -> "" }).isEmpty()
    }

    @Test
    fun `a cancelled collection import stops at once`() {
        val archives = (1..2).map { File(directory, "$it.zip").apply { writeText("zip") } }
        val imported = mutableListOf<Int>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                importEach(archives, { null }) { index, _ ->
                    imported += index
                    throw CancellationException("stopped")
                }
            }
        }

        assertThat(imported).containsExactly(0)
        assertThat(archives[0].exists()).isFalse()
        assertThat(archives[1].exists()).isTrue()
    }

    @Test
    fun `archives in use are the ones unfinished imports read`() {
        val works = listOf(
            work("file", WorkInfo.State.ENQUEUED, order = 1, archive = "a.zip"),
            work("collection", WorkInfo.State.RUNNING, order = 2, archive = "b.zip"),
            work("done", WorkInfo.State.SUCCEEDED, order = 3, archive = "c.zip"),
            work("download", WorkInfo.State.ENQUEUED, order = 4, archive = ""),
        )

        assertThat(archivesInUse(works)).containsExactly("a.zip", "b.zip")
    }

    @Test
    fun `an unfinished import from an older version leaves the archives in use unknown`() {
        val works = listOf(
            work("file", WorkInfo.State.ENQUEUED, order = 1, archive = "a.zip"),
            work("old", WorkInfo.State.ENQUEUED, order = null),
        )

        assertThat(archivesInUse(works)).isNull()
        // A finished one no longer reads anything.
        assertThat(archivesInUse(listOf(work("old", WorkInfo.State.SUCCEEDED, order = null)))).isEmpty()
    }

    @Test
    fun `runs the system stopped do not count as runs the app died in`() {
        // First run, and a run after one stop by the system.
        assertThat(DictionaryImportWorker.interruptions(runAttemptCount = 0, stops = 0)).isEqualTo(0)
        assertThat(DictionaryImportWorker.interruptions(runAttemptCount = 1, stops = 1)).isEqualTo(0)
        // Two stops by the system and one death: started again, not given up.
        assertThat(DictionaryImportWorker.interruptions(runAttemptCount = 3, stops = 2)).isEqualTo(1)
        // Two deaths are counted as before.
        assertThat(DictionaryImportWorker.interruptions(runAttemptCount = 2, stops = 0)).isEqualTo(2)
        // A stop recorded for a run WorkManager never counted does not go below zero.
        assertThat(DictionaryImportWorker.interruptions(runAttemptCount = 0, stops = 1)).isEqualTo(0)
    }

    @Test
    fun `stops are stored per task and read back`() {
        val id = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val stored = DictionaryImports.writeStops(mapOf(id to 2, other to 1))

        assertThat(DictionaryImports.readStops(stored)).containsExactly(id, 2, other, 1)
        // Entries that do not parse are dropped.
        assertThat(DictionaryImports.readStops(setOf("$id=x", "no count", "$other=3"))).containsExactly(other, 3)
    }
}
