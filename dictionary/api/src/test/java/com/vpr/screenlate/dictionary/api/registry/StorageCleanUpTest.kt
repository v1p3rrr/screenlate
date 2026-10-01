package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Test

class StorageCleanUpTest {
    private val base: File = Files.createTempDirectory("storage").toFile()
    private val root = File(base, "dictionaries")
    private val staging = File(root, ".staging")
    private val downloads = File(base, "downloads")

    private val now = 100 * HOUR
    private val processStart = now - 60_000

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun directory(parent: File, name: String, modified: Long = now): File =
        File(parent, name).apply {
            mkdirs()
            File(this, "data").writeText("x")
            setLastModified(modified)
        }

    private fun archive(name: String, modified: Long): File =
        File(downloads.apply { mkdirs() }, name).apply {
            writeText("zip")
            setLastModified(modified)
        }

    private fun leftovers(archivesInUse: Set<String>?) =
        importLeftovers(staging, downloads, archivesInUse, processStart, now)

    @Test
    fun `directories of no dictionary go, the staging area and registered ones stay`() {
        val registered = directory(root, "registered")
        val orphan = directory(root, "orphan")
        staging.mkdirs()

        assertThat(orphanDirectories(root, staging, known = listOf("registered"))).containsExactly(orphan)
        assertThat(registered.exists()).isTrue()
    }

    @Test
    fun `staging from an earlier process goes at once, staging of this process stays`() {
        val earlier = directory(staging, "earlier", modified = processStart - 1)
        val current = directory(staging, "current", modified = processStart + 1)

        assertThat(leftovers(archivesInUse = emptySet())).containsExactly(earlier)
        assertThat(current.exists()).isTrue()
    }

    @Test
    fun `archives from an earlier process go unless a queued import reads them`() {
        val partialDownload = archive("download.zip", modified = processStart - 1)
        val queuedCopy = archive("copy.zip", modified = processStart - 1)
        val beingWritten = archive("new.zip", modified = processStart + 1)

        assertThat(leftovers(archivesInUse = setOf("copy.zip"))).containsExactly(partialDownload)
        assertThat(queuedCopy.exists() && beingWritten.exists()).isTrue()
    }

    @Test
    fun `with the archives in use unknown only old archives go`() {
        val old = archive("old.zip", modified = now - 7 * HOUR)
        val recent = archive("recent.zip", modified = processStart - 1)

        assertThat(leftovers(archivesInUse = null)).containsExactly(old)
        assertThat(recent.exists()).isTrue()
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
