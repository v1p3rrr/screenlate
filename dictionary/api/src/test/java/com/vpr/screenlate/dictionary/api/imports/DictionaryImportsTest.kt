package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ARCHIVE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CANCELLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CONNECTION_LOST
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DOWNLOAD_STOPPED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_FREE_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INTERRUPTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NAME
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NEEDED_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PAUSED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_RESUMABLE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SOURCE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_DOWNLOAD
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
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
        constraints: Constraints = Constraints.NONE,
    ) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = setOfNotNull(
            DictionaryImports.TAG,
            DictionaryImports.NAME_TAG_PREFIX + name,
            order?.let { DictionaryImports.ORDER_TAG_PREFIX + it },
            archive?.let { DictionaryImports.ARCHIVE_TAG_PREFIX + it },
        ),
        outputData = output,
        progress = progress,
        constraints = constraints,
    )

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
    fun `a stopped download keeps its archive for a resume`() {
        val works = listOf(
            work("stopped", WorkInfo.State.SUCCEEDED, order = 1, archive = "a.zip", output = workDataOf(KEY_RESUMABLE to true)),
            work("failed", WorkInfo.State.SUCCEEDED, order = 2, archive = "b.zip", output = workDataOf(KEY_ERROR to "x")),
        )

        assertThat(archivesInUse(works)).containsExactly("a.zip")
    }

    @Test
    fun `stopped downloads can be resumed, dismissed tasks are left out`() {
        val stopped = work(
            "Stopped",
            WorkInfo.State.SUCCEEDED,
            1,
            output = workDataOf(KEY_ERROR to "timeout", KEY_RESUMABLE to true, KEY_DOWNLOAD_STOPPED to true, KEY_CONNECTION_LOST to true),
        )
        val importFailed = work("Broken", WorkInfo.State.SUCCEEDED, 2, output = workDataOf(KEY_ERROR to "bad zip", KEY_RESUMABLE to true))
        val dismissed = work("Dismissed", WorkInfo.State.SUCCEEDED, 3, output = workDataOf(KEY_ERROR to "x"))
        val retrying = work("Retrying", WorkInfo.State.ENQUEUED, 4)

        val tasks = importTasks(
            listOf(stopped, importFailed, dismissed, retrying),
            dismissed = setOf(dismissed.id.toString()),
            retries = mapOf(retrying.id.toString() to 2, stopped.id.toString() to 3),
        )

        assertThat(tasks.map { it.name }).containsExactly("Stopped", "Broken", "Retrying").inOrder()
        assertThat(tasks.map { it.resumable }).containsExactly(true, true, false).inOrder()
        assertThat(tasks.map { it.downloadStopped }).containsExactly(true, false, false).inOrder()
        assertThat(tasks.map { it.connectionLost }).containsExactly(true, false, false).inOrder()
        // Retries show only while the task is waiting for its next try.
        assertThat(tasks.map { it.retries }).containsExactly(0, 0, 2).inOrder()
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

    @Test
    fun `a stop by WorkManager is logged with what follows it`() {
        assertThat(DictionaryImportWorker.afterStop(WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY))
            .isEqualTo("waits for a network, then runs again")
        assertThat(DictionaryImportWorker.afterStop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)).isEqualTo("cancelled")
        listOf(
            WorkInfo.STOP_REASON_TIMEOUT,
            WorkInfo.STOP_REASON_QUOTA,
            WorkInfo.STOP_REASON_DEVICE_STATE,
            WorkInfo.STOP_REASON_APP_STANDBY,
            WorkInfo.STOP_REASON_SYSTEM_PROCESSING,
            WorkInfo.STOP_REASON_UNKNOWN,
        ).forEach { assertThat(DictionaryImportWorker.afterStop(it)).isEqualTo("runs again later") }
    }

    @Test
    fun `the end of a task is kept for a run WorkManager starts after dropping its result`() {
        val stopped = UUID.randomUUID()
        val cancelled = UUID.randomUUID()
        val finished = UUID.randomUUID()
        val output = workDataOf(
            KEY_SOURCE to SOURCE_URL,
            KEY_URL to "https://example.org/a.zip",
            KEY_ERROR to "timeout",
            KEY_RESUMABLE to true,
            KEY_DOWNLOAD_STOPPED to true,
            KEY_CONNECTION_LOST to true,
            KEY_TITLES to arrayOf("Jitendex"),
        )
        val unfinished = setOf(stopped, cancelled).mapTo(hashSetOf()) { it.toString() }
        var ends = DictionaryImports.recordedEnds(emptySet(), finished, workDataOf(KEY_TITLES to arrayOf("Old")), setOf(finished.toString()))
        ends = DictionaryImports.recordedEnds(ends, cancelled, workDataOf(KEY_CANCELLED to true), unfinished)
        ends = DictionaryImports.recordedEnds(ends, stopped, output, unfinished)

        val kept = DictionaryImports.endIn(ends, stopped)!!
        assertThat(kept.keyValueMap.keys).isEqualTo(output.keyValueMap.keys)
        assertThat(kept.getBoolean(KEY_CONNECTION_LOST, false)).isTrue()
        assertThat(kept.getString(KEY_ERROR)).isEqualTo("timeout")
        assertThat(kept.getStringArray(KEY_TITLES)).asList().containsExactly("Jitendex")
        assertThat(DictionaryImports.endIn(ends, cancelled)!!.getBoolean(KEY_CANCELLED, false)).isTrue()
        // The record of a task that finished meanwhile is dropped; a task without one has none.
        assertThat(DictionaryImports.endIn(ends, finished)).isNull()
        assertThat(DictionaryImports.endIn(ends, UUID.randomUUID())).isNull()
        assertThat(ends).hasSize(2)
        // Recording again replaces, and finished tasks leave.
        ends = DictionaryImports.recordedEnds(ends, stopped, workDataOf(KEY_CANCELLED to true), unfinished)
        assertThat(ends).hasSize(2)
        assertThat(DictionaryImports.endIn(ends, stopped)!!.getBoolean(KEY_CANCELLED, false)).isTrue()
        assertThat(DictionaryImports.unfinishedEnds(ends, setOf(cancelled.toString())).map { it.substringBefore('=') })
            .containsExactly(cancelled.toString())
        // An entry that does not parse reads as none.
        assertThat(DictionaryImports.endIn(setOf("$stopped=not base64!"), stopped)).isNull()
    }

    @Test
    fun `a download needs a network until it starts`() {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val waiting = work("Jitendex", WorkInfo.State.ENQUEUED, 1, constraints = network)
        val behind = work("JMnedict", WorkInfo.State.BLOCKED, 2, constraints = network)
        val running = work("KANJIDIC", WorkInfo.State.RUNNING, 3, constraints = network, progress = workDataOf(KEY_STAGE to STAGE_DOWNLOAD))
        val file = work("File", WorkInfo.State.ENQUEUED, 4)

        val tasks = importTasks(listOf(waiting, behind, running, file))

        assertThat(tasks.map { it.name to it.needsNetwork }).containsExactly(
            "Jitendex" to true,
            "JMnedict" to true,
            "KANJIDIC" to false,
            "File" to false,
        ).inOrder()
    }

    @Test
    fun `a download WorkManager stopped for the lost network waits for it, not stopped`() {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        // Stopped by WorkManager during its third try: it is enqueued again, without an output.
        val stopped = WorkInfo(
            id = UUID.randomUUID(),
            state = WorkInfo.State.ENQUEUED,
            tags = setOf(DictionaryImports.TAG, DictionaryImports.NAME_TAG_PREFIX + "Jitendex", DictionaryImports.ORDER_TAG_PREFIX + 1),
            runAttemptCount = 3,
            constraints = network,
            stopReason = WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY,
        )

        val task = importTasks(listOf(stopped), retries = mapOf(stopped.id.toString() to 2)).single()

        assertThat(task.state).isEqualTo(ImportTask.State.QUEUED)
        assertThat(task.needsNetwork).isTrue()
        assertThat(task.retries).isEqualTo(2)
        assertThat(task.resumable).isFalse()
        assertThat(task.downloadStopped).isFalse()
        assertThat(task.connectionLost).isFalse()
        assertThat(task.error).isNull()
        // Its partial file stays for the run after the network is back.
        val archive = work("Jitendex", WorkInfo.State.ENQUEUED, 1, archive = "part.zip", constraints = network)
        assertThat(archivesInUse(listOf(archive))).containsExactly("part.zip")
    }

    @Test
    fun `a task reads its own input, not the output of the task before it in its chain`() {
        val own = workDataOf(KEY_SOURCE to SOURCE_URL, KEY_URL to "https://example.org/jmdict.zip", KEY_NAME to "JMdict", KEY_ARCHIVE to "b.zip")
        val stoppedBefore = workDataOf(
            KEY_SOURCE to SOURCE_URL,
            KEY_URL to "https://example.org/jitendex.zip",
            KEY_NAME to "Jitendex",
            KEY_ARCHIVE to "a.zip",
            KEY_RESUMABLE to true,
            KEY_DOWNLOAD_STOPPED to true,
        )

        val input = OwnInputMerger().merge(listOf(own, stoppedBefore, workDataOf(KEY_TITLES to arrayOf("KANJIDIC"))))

        assertThat(input.keyValueMap).isEqualTo(own.keyValueMap)
        assertThat(OwnInputMerger().merge(emptyList())).isEqualTo(Data.EMPTY)
    }
}
