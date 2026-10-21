package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CANCELLED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_CONNECTION_LOST
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_DOWNLOAD_STOPPED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ERROR
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_FREE_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_INTERRUPTED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_NEEDED_BYTES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_ENTRY_ID
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PAUSED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_PATH
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_REQUEUED
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_RESUMABLE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_SOURCE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_STAGE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_TITLES
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.KEY_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_FILE
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_MODEL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.SOURCE_URL
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_DOWNLOAD
import com.vpr.screenlate.dictionary.api.imports.DictionaryImportWorker.Companion.STAGE_WAIT
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.io.File
import java.nio.file.Files
import java.util.Locale
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
        language: String? = null,
        entry: String? = null,
        download: String? = null,
        chain: Int? = null,
        retry: Int? = null,
        constraints: Constraints = Constraints.NONE,
    ) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = setOfNotNull(
            DictionaryImports.TAG,
            DictionaryImports.NAME_TAG_PREFIX + name,
            order?.let { DictionaryImports.ORDER_TAG_PREFIX + it },
            archive?.let { DictionaryImports.ARCHIVE_TAG_PREFIX + it },
            source?.let { DictionaryImports.SOURCE_TAG_PREFIX + it },
            language?.let { DictionaryImports.LANGUAGE_TAG_PREFIX + it },
            entry?.let { DictionaryImports.ENTRY_TAG_PREFIX + it },
            download?.let { DictionaryImports.DOWNLOAD_TAG_PREFIX + it },
            chain?.let { DictionaryImports.CHAIN_TAG_PREFIX + it },
            retry?.let { DictionaryImports.RETRY_TAG_PREFIX + it },
        ),
        outputData = output,
        progress = progress,
        constraints = constraints,
    )

    @Test
    fun `catalog language and entry survive changes in displayed progress`() {
        val task = importTasks(listOf(work("English", WorkInfo.State.RUNNING, 1, language = "en", entry = "english-main",
            progress = workDataOf(DictionaryImportWorker.KEY_NAME to "new archive title")))).single()
        assertThat(task.language).isEqualTo("en")
        assertThat(task.catalogEntry).isEqualTo("english-main")
        assertThat(task.name).isEqualTo("new archive title")
    }

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
            retry = 3,
        )
        val importFailed = work("Broken", WorkInfo.State.SUCCEEDED, 2, output = workDataOf(KEY_ERROR to "bad zip", KEY_RESUMABLE to true))
        val dismissed = work("Dismissed", WorkInfo.State.SUCCEEDED, 3, output = workDataOf(KEY_ERROR to "x"))
        val retrying = work("Retrying", WorkInfo.State.ENQUEUED, 4, retry = 2)

        val tasks = importTasks(listOf(stopped, importFailed, dismissed, retrying), dismissed = setOf(dismissed.id.toString()))

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
        val requeued = UUID.randomUUID()
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
        val unfinished = setOf(stopped, requeued).mapTo(hashSetOf()) { it.toString() }
        var ends = DictionaryImports.recordedEnds(emptySet(), finished, workDataOf(KEY_TITLES to arrayOf("Old")), setOf(finished.toString()))
        ends = DictionaryImports.recordedEnds(ends, requeued, workDataOf(KEY_REQUEUED to true), unfinished)
        ends = DictionaryImports.recordedEnds(ends, stopped, output, unfinished)

        val kept = DictionaryImports.endIn(ends, stopped)!!
        assertThat(kept.keyValueMap.keys).isEqualTo(output.keyValueMap.keys)
        assertThat(kept.getBoolean(KEY_CONNECTION_LOST, false)).isTrue()
        assertThat(kept.getString(KEY_ERROR)).isEqualTo("timeout")
        assertThat(kept.getStringArray(KEY_TITLES)).asList().containsExactly("Jitendex")
        assertThat(DictionaryImports.endIn(ends, requeued)!!.getBoolean(KEY_REQUEUED, false)).isTrue()
        // The record of a task that finished meanwhile is dropped; a task without one has none.
        assertThat(DictionaryImports.endIn(ends, finished)).isNull()
        assertThat(DictionaryImports.endIn(ends, UUID.randomUUID())).isNull()
        assertThat(ends).hasSize(2)
        // Recording again replaces, and finished tasks leave.
        ends = DictionaryImports.recordedEnds(ends, stopped, workDataOf(KEY_CANCELLED to true), unfinished)
        assertThat(ends).hasSize(2)
        assertThat(DictionaryImports.endIn(ends, stopped)!!.getBoolean(KEY_CANCELLED, false)).isTrue()
        assertThat(DictionaryImports.unfinishedEnds(ends, setOf(requeued.toString())).map { it.substringBefore('=') })
            .containsExactly(requeued.toString())
        // An entry that does not parse reads as none.
        assertThat(DictionaryImports.endIn(setOf("$stopped=not base64!"), stopped)).isNull()
    }

    @Test
    fun `a download WorkManager stopped for the lost network waits for it, not stopped`() {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        // Stopped by WorkManager at its third retry: it is enqueued again, without an output.
        val stopped = WorkInfo(
            id = UUID.randomUUID(),
            state = WorkInfo.State.ENQUEUED,
            tags = setOf(DictionaryImports.TAG, DictionaryImports.NAME_TAG_PREFIX + "Jiten", DictionaryImports.ORDER_TAG_PREFIX + 1,
                DictionaryImports.RETRY_TAG_PREFIX + 3, DictionaryImports.SOURCE_TAG_PREFIX + SOURCE_URL),
            runAttemptCount = 1,
            constraints = network,
            stopReason = WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY,
        )

        val task = importTasks(listOf(stopped)).single()

        assertThat(task.state).isEqualTo(ImportTask.State.QUEUED)
        assertThat(task.needsNetwork).isTrue()
        assertThat(task.retries).isEqualTo(3)
        assertThat(task.resumable).isFalse()
        assertThat(task.downloadStopped).isFalse()
        assertThat(task.connectionLost).isFalse()
        assertThat(task.error).isNull()
        // Its partial file stays for the run after the network is back.
        val archive = work("Jiten", WorkInfo.State.ENQUEUED, 1, archive = "part.zip", constraints = network)
        assertThat(archivesInUse(listOf(archive))).containsExactly("part.zip")
    }

    @Test
    fun `a catalog task shows the localized title of its entry`() {
        val entry = CatalogEntry(
            id = "wty-en-de", title = "Wiktionary glossary (en–de)", sourceLanguage = "en", targetLanguage = "de",
            titles = mapOf("en" to "Wiktionary: short translations ({src}–{tgt})"), downloadUrl = "https://example.org/g.zip",
        )
        val entries = listOf(entry)

        assertThat(shownImportName(entry.title, entry.id, entries, Locale.ENGLISH))
            .isEqualTo("Wiktionary: short translations (en–de)")
        // A file import, a renamed task and an entry missing from the catalog keep the stored name.
        assertThat(shownImportName("my.zip", null, entries, Locale.ENGLISH)).isEqualTo("my.zip")
        assertThat(shownImportName("Old title", entry.id, entries, Locale.ENGLISH)).isEqualTo("Old title")
        assertThat(shownImportName(entry.title, entry.id, emptyList(), Locale.ENGLISH)).isEqualTo(entry.title)
    }

    @Test
    fun `a download queued again for a retry leaves the list, the retry shows while it waits`() {
        val requeued = work("Jiten", WorkInfo.State.SUCCEEDED, 1, output = workDataOf(KEY_REQUEUED to true))
        val waiting = work("Jiten", WorkInfo.State.BLOCKED, 3, retry = 1)
        val downloading = work("Model", WorkInfo.State.RUNNING, 2, retry = 2, progress = workDataOf(KEY_STAGE to STAGE_DOWNLOAD))
        val downloaded = work("Wiktionary", WorkInfo.State.RUNNING, 4, progress = workDataOf(KEY_STAGE to STAGE_WAIT))

        val tasks = importTasks(listOf(requeued, waiting, downloading, downloaded))

        assertThat(tasks.map { it.name }).containsExactly("Model", "Jiten", "Wiktionary").inOrder()
        assertThat(tasks.map { it.state })
            .containsExactly(ImportTask.State.DOWNLOADING, ImportTask.State.QUEUED, ImportTask.State.QUEUED).inOrder()
        // A running try is not a waiting retry.
        assertThat(tasks.map { it.retries }).containsExactly(0, 1, 0).inOrder()
    }

    @Test
    fun `a download needs a network until it starts`() {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val waiting = work("Jiten", WorkInfo.State.ENQUEUED, 1, constraints = network)
        val behind = work("Wiktionary", WorkInfo.State.BLOCKED, 2, constraints = network)
        val running = work("Model", WorkInfo.State.RUNNING, 3, constraints = network, progress = workDataOf(KEY_STAGE to STAGE_WAIT))
        val file = work("File", WorkInfo.State.ENQUEUED, 4)

        val tasks = importTasks(listOf(waiting, behind, running, file))

        assertThat(tasks.map { it.name to it.needsNetwork }).containsExactly(
            "Model" to false,
            "Jiten" to true,
            "Wiktionary" to true,
            "File" to false,
        )
    }

    @Test
    fun `downloads of one entry or URL share a key, other imports have none`() {
        val entry = workDataOf(KEY_SOURCE to SOURCE_URL, KEY_URL to "https://example.org/a.zip", KEY_ENTRY_ID to "jiten-global")
        assertThat(downloadKey(entry)).isEqualTo("entry:jiten-global")
        assertThat(downloadKey(workDataOf(KEY_SOURCE to SOURCE_MODEL, KEY_ENTRY_ID to "paddle"))).isEqualTo("entry:paddle")
        assertThat(downloadKey(workDataOf(KEY_SOURCE to SOURCE_URL, KEY_URL to "https://example.org/a.zip")))
            .isEqualTo("url:https://example.org/a.zip")
        assertThat(downloadKey(workDataOf(KEY_SOURCE to SOURCE_FILE, KEY_PATH to "/a.zip"))).isNull()
    }

    @Test
    fun `the same download is the unfinished task, else the last stopped one`() {
        val key = "entry:jiten-global"
        val resumable = workDataOf(KEY_ERROR to "timeout", KEY_RESUMABLE to true)
        val installed = work("Jiten", WorkInfo.State.SUCCEEDED, 1, download = key)
        val stoppedFirst = work("Jiten", WorkInfo.State.SUCCEEDED, 2, output = resumable, download = key)
        val stoppedLast = work("Jiten", WorkInfo.State.SUCCEEDED, 3, output = resumable, download = key)
        val dismissed = work("Jiten", WorkInfo.State.SUCCEEDED, 4, output = resumable, download = key)
        val cancelled = work("Jiten", WorkInfo.State.ENQUEUED, 5, download = key)
        val other = work("Model", WorkInfo.State.RUNNING, 6, download = "entry:paddle")
        val works = listOf(installed, stoppedFirst, stoppedLast, dismissed, cancelled, other)
        val cancelledIds = setOf(cancelled.id.toString())
        val dismissedIds = setOf(dismissed.id.toString())

        assertThat(sameDownload(works, key, cancelledIds, dismissedIds)).isEqualTo(stoppedLast)
        val queued = work("Jiten", WorkInfo.State.BLOCKED, 7, download = key, retry = 1)
        assertThat(sameDownload(works + queued, key, cancelledIds, dismissedIds)).isEqualTo(queued)
        assertThat(sameDownload(listOf(installed, other), key, emptySet(), emptySet())).isNull()
        assertThat(sameDownload(works, null, emptySet(), emptySet())).isNull()
    }

    @Test
    fun `a task goes to the chain with fewer unfinished tasks`() {
        val old = work("old", WorkInfo.State.RUNNING, 1)
        val first = work("a", WorkInfo.State.BLOCKED, 2, chain = 0)
        val second = work("b", WorkInfo.State.RUNNING, 3, chain = 1)
        val done = work("c", WorkInfo.State.SUCCEEDED, 4, chain = 1)
        val cancelled = work("d", WorkInfo.State.ENQUEUED, 5, chain = 1)

        // Tasks queued before there were two chains ran on the first.
        assertThat(leastLoadedChain(listOf(old, first, second, done, cancelled), setOf(cancelled.id.toString()))).isEqualTo(1)
        assertThat(leastLoadedChain(listOf(first, second, done), emptySet())).isEqualTo(0)
        assertThat(leastLoadedChain(listOf(first, second, cancelled), emptySet())).isEqualTo(0)
        // A task queued again leaves itself out.
        assertThat(leastLoadedChain(listOf(old, first, second), emptySet(), exclude = second.id)).isEqualTo(1)
        assertThat(leastLoadedChain(emptyList(), emptySet())).isEqualTo(0)
    }
}
