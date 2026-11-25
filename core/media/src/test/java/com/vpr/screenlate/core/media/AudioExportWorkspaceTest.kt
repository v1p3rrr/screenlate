package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioExportWorkspaceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun insufficientWorkspaceFailsBeforeCodecCreation() {
        val output = temporary.newFile("low-space.opus")
        val edit = AudioEdit(AudioRange(0, 1))
        val workspaceBytes = 1 * 2 + 65_536L

        assertThrows(IOException::class.java) {
            AudioExporter.exportOpus(
                ByteArrayInputStream(ByteArray(2)), edit, 48_000, output,
                minFreeSpaceBytes = 100, usableSpace = { 100 + workspaceBytes - 1 },
            )
        }
        assertThat(output.length()).isEqualTo(0)
    }

    @Test
    fun exactPcmPlusSlackCeilingIsEnforcedBeforeAContainerWrite() {
        val output = temporary.newFile("bounded.opus")
        val workspace = AudioExportWorkspace(output, outputFrames = 10, minFreeSpaceBytes = 100,
            usableSpace = { 1_000_000 })
        assertThat(workspace.maxOutputBytes).isEqualTo(65_556)
        workspace.preflight()
        output.writeBytes(ByteArray(10))

        workspace.beforeWrite(65_546)
        assertThrows(IOException::class.java) { workspace.beforeWrite(65_547) }
    }

    @Test
    fun finalizationChecksBytesFlushedByTheMuxerAndTheRemainingReserve() {
        val output = temporary.newFile("flushed.opus")
        var freeBytes = 1000L
        val workspace = AudioExportWorkspace(output, outputFrames = 10, minFreeSpaceBytes = 100,
            usableSpace = { freeBytes })
        output.writeBytes(ByteArray(workspace.maxOutputBytes.toInt()))
        workspace.finish()

        freeBytes = 99
        assertThrows(IOException::class.java) { workspace.finish() }
        output.appendBytes(ByteArray(1))
        freeBytes = 1000
        assertThrows(IOException::class.java) { workspace.finish() }
    }

    @Test
    fun freeSpaceIsRecheckedForTheNextActualWrite() {
        val output = temporary.newFile("raced.opus")
        val workspace = AudioExportWorkspace(output, outputFrames = 100, minFreeSpaceBytes = 100,
            usableSpace = { 119 })
        workspace.beforeWrite(19)
        assertThrows(IOException::class.java) { workspace.beforeWrite(20) }
    }
}
