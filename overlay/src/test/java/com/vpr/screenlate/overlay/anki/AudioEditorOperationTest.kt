package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEditorOperationTest {
    private val source = AudioRange(100, 1_000)
    private val initial = AudioEdit(AudioRange(200, 400))

    @Test
    fun `opening cannot save or accept edits and duplicate Ready cannot replace user edits`() = runTest {
        var exports = 0
        val operation = AudioEditorOperation(this, source, initial, opening = true) { exports++; null }
        operation.save()
        runCurrent()
        assertThat(exports).isEqualTo(0)
        assertThat(operation.accept(1, 0, AudioEdit(AudioRange(300, 500)))).isFalse()
        assertThat(operation.ready(AudioRange(100, 1_500), initial)).isTrue()
        val edit = AudioEdit(AudioRange(300, 1_200))
        assertThat(operation.accept(2, 1, edit)).isTrue()
        assertThat(operation.ready(AudioRange(100, 2_000), initial)).isFalse()
        assertThat(operation.edit).isEqualTo(edit)
        operation.cancel()
    }

    @Test
    fun `double save exports once and keeps the operation open until export succeeds`() = runTest {
        val exported = CompletableDeferred<File?>()
        var calls = 0
        val operation = AudioEditorOperation(this, source, initial) { calls++; exported.await() }
        operation.save()
        operation.save()
        runCurrent()
        assertThat(calls).isEqualTo(1)
        assertThat(operation.result.isCompleted).isFalse()
        assertThat(operation.phase).isEqualTo(AudioEditorOperation.Phase.EXPORTING)
        val file = File.createTempFile("editor-test", ".opus")
        try {
            exported.complete(file)
            runCurrent()
            assertThat(operation.result.await()).isEqualTo(AudioEditorOperation.Result.Audio(file, initial))
        } finally { file.delete() }
    }

    @Test
    fun `cancelled export cannot finish another operation and removes its late output`() = runTest {
        val exported = CompletableDeferred<File?>()
        val old = AudioEditorOperation(this, source, initial) { withContext(NonCancellable) { exported.await() } }
        old.save()
        runCurrent()
        old.cancel()
        val next = AudioEditorOperation(this, source, initial) { error("No save requested") }
        val lateFile = File.createTempFile("editor-late", ".opus")
        try {
            exported.complete(lateFile)
            runCurrent()
            old.awaitClosed()
            assertThat(old.result.await()).isEqualTo(AudioEditorOperation.Result.Cancelled)
            assertThat(next.result.isCompleted).isFalse()
            assertThat(lateFile.exists()).isFalse()
        } finally { next.cancel(); lateFile.delete() }
    }

    @Test
    fun `failed export keeps edits and supports an explicit retry`() = runTest {
        var calls = 0
        val file = File.createTempFile("editor-retry", ".opus")
        val operation = AudioEditorOperation(this, source, initial) { if (++calls == 1) null else file }
        try {
            val edit = AudioEdit(AudioRange(250, 900), listOf(AudioRange(300, 500)))
            assertThat(operation.accept(1, 0, edit)).isTrue()
            operation.save()
            runCurrent()
            assertThat(operation.phase).isEqualTo(AudioEditorOperation.Phase.FAILED)
            assertThat(operation.result.isCompleted).isFalse()
            assertThat(operation.edit).isEqualTo(edit)
            operation.save()
            runCurrent()
            assertThat(operation.result.await()).isEqualTo(AudioEditorOperation.Result.Audio(file, edit))
        } finally { file.delete() }
    }

    @Test
    fun `source revisions reject delayed edits and refresh preserves the accepted selection`() = runTest {
        val operation = AudioEditorOperation(this, source, initial) { null }
        val edit = AudioEdit(AudioRange(250, 850))
        assertThat(operation.accept(1, 0, edit)).isTrue()
        assertThat(operation.append(AudioRange(100, 1_500))).isTrue()
        assertThat(operation.edit).isEqualTo(edit)
        assertThat(operation.accept(2, 0, initial)).isFalse()
        assertThat(operation.accept(2, 1, AudioEdit(AudioRange(250, 1_400)))).isTrue()
        assertThat(operation.accept(1, 1, initial)).isFalse()
        assertThat(operation.append(AudioRange(200, 2_000))).isFalse()
        operation.cancel()
    }

    @Test
    fun `invalid or out of source edits never become an implicit without audio result`() = runTest {
        val operation = AudioEditorOperation(this, source, initial) { null }
        assertThat(operation.accept(1, 0, AudioEdit(AudioRange(0, 200)))).isFalse()
        assertThat(operation.result.isCompleted).isFalse()
        assertThat(operation.edit).isEqualTo(initial)
        operation.withoutAudio()
        assertThat(operation.result.await()).isEqualTo(AudioEditorOperation.Result.WithoutAudio)
    }
}
