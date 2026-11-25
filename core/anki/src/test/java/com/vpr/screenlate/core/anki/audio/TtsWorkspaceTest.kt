package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsWorkspaceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun admissionAndResultEnforceFileCeilingAndSafetyReserve() {
        val freeBytes = AtomicLong(103)
        val workspace = TtsWorkspace(
            temporary.newFolder(), maxFileBytes = 4, minFreeSpaceBytes = 100,
            usableSpace = { freeBytes.get() },
        )
        assertThrows(IOException::class.java) { workspace.createFile() }

        freeBytes.set(104)
        val file = workspace.createFile()
        file.writeBytes(ByteArray(4))
        assertThat(workspace.isValid(file)).isTrue()

        file.writeBytes(ByteArray(5))
        assertThat(workspace.isValid(file)).isFalse()

        file.writeBytes(ByteArray(4))
        freeBytes.set(99)
        assertThat(workspace.isValid(file)).isFalse()
    }
}
