package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioExportPreparationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `changing the accepted edit replaces retained encoding and cancellation clears both files`() = runBlocking {
        val initial = AudioEdit(AudioRange(100, 400))
        val files = mutableListOf<java.io.File>()
        val preparation = AudioExportPreparation(encode = {
            if (files.isNotEmpty()) assertThat(files.last().exists()).isFalse()
            folder.newFile("edit${files.size}.opus").also { files += it }
        }, prepare = { false })
        val operation = AudioEditorOperation(this, AudioRange(0, 1000), initial) { preparation.export(it) }
        try {
            operation.save()
            withTimeout(3000) { while (operation.phase == AudioEditorOperation.Phase.EXPORTING) delay(1) }
            assertThat(operation.accept(1, 0, AudioEdit(AudioRange(200, 600)))).isTrue()
            operation.save()
            withTimeout(3000) { while (operation.phase == AudioEditorOperation.Phase.EXPORTING) delay(1) }
            assertThat(files).hasSize(2)
            assertThat(files.last().exists()).isTrue()
        } finally {
            operation.cancel()
            operation.awaitClosed()
            preparation.close()
        }
        assertThat(files.any { it.exists() }).isFalse()
    }

    @Test
    fun `cancel waits for an in-flight media copy before deleting its staging and rejects late success`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val file = folder.newFile("copying.opus")
        val preparation = AudioExportPreparation(encode = { file }, prepare = {
            started.complete(Unit)
            withContext(NonCancellable) { complete.await() }
            true
        })
        val operation = AudioEditorOperation(this, AudioRange(0, 1000), AudioEdit(AudioRange(100, 400))) { preparation.export(it) }
        try {
            operation.save()
            withTimeout(3000) { started.await() }
            operation.cancel()
            val retirement = async { operation.awaitClosed(); preparation.close() }
            delay(20)
            assertThat(retirement.isCompleted).isFalse()
            assertThat(file.exists()).isTrue()
            complete.complete(Unit)
            withTimeout(3000) { retirement.await() }
            assertThat(operation.result.await()).isEqualTo(AudioEditorOperation.Result.Cancelled)
            assertThat(file.exists()).isFalse()
        } finally {
            complete.complete(Unit)
            operation.cancel()
            operation.awaitClosed()
            preparation.close()
        }
    }

    @Test
    fun `failed transfer keeps editor and encoded file and retry does not encode again`() = runBlocking {
        val initial = AudioEdit(AudioRange(100, 400), listOf(AudioRange(200, 250)))
        var encodes = 0
        var transfers = 0
        val file = folder.newFile("retained.opus")
        val preparation = AudioExportPreparation(encode = { encodes++; file }, prepare = {
            assertThat(it).isEqualTo(file)
            assertThat(it.exists()).isTrue()
            ++transfers > 1
        })
        val operation = AudioEditorOperation(this, AudioRange(0, 1000), initial) { preparation.export(it) }
        try {
            operation.save()
            withTimeout(3000) { while (operation.phase == AudioEditorOperation.Phase.EXPORTING) delay(1) }
            assertThat(operation.phase).isEqualTo(AudioEditorOperation.Phase.FAILED)
            assertThat(operation.result.isCompleted).isFalse()
            assertThat(operation.edit).isEqualTo(initial)
            assertThat(file.exists()).isTrue()
            operation.save()
            val result = withTimeout(3000) { operation.result.await() } as AudioEditorOperation.Result.Audio
            assertThat(result.edit).isEqualTo(initial)
            assertThat(result.file).isEqualTo(file)
            assertThat(encodes).isEqualTo(1)
            assertThat(transfers).isEqualTo(2)
            preparation.close(keep = result.file)
            assertThat(file.exists()).isTrue()
        } finally {
            operation.cancel()
            operation.awaitClosed()
            preparation.close()
            file.delete()
        }
    }
}
