package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CropToggleSyncTest {

    @Test
    fun `external updates sync to editor toggle in reverse direction`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MutableStateFlow(false)
        val uiValues = mutableListOf<Boolean>()
        val sync = ToggleSync(
            initial = false,
            updates = settings,
            persist = { settings.value = it },
            onExternalValue = { uiValues += it },
            scope = this,
            dispatcher = dispatcher,
        )
        sync.start()
        runCurrent()
        assertThat(uiValues).isEmpty()

        settings.value = true
        runCurrent()
        assertThat(sync.value).isTrue()
        assertThat(uiValues).containsExactly(true)

        settings.value = false
        runCurrent()
        assertThat(sync.value).isFalse()
        assertThat(uiValues).containsExactly(true, false).inOrder()

        sync.stop()
    }

    @Test
    fun `rapid local flips do not bounce back when first write echoes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MutableStateFlow(false)
        val firstWriteGate = CompletableDeferred<Unit>()
        val persisted = mutableListOf<Boolean>()
        val uiCallbacks = mutableListOf<Boolean>()
        var firstWrite = true
        val sync = ToggleSync(
            initial = false,
            updates = settings,
            persist = { target ->
                if (firstWrite) {
                    firstWrite = false
                    firstWriteGate.await()
                }
                persisted += target
                settings.value = target
            },
            onExternalValue = { uiCallbacks += it },
            scope = this,
            dispatcher = dispatcher,
        )
        sync.start()
        runCurrent()

        // Flip ON then OFF before the first write completes.
        sync.setLocal(true)
        runCurrent()
        sync.setLocal(false)
        runCurrent()

        // Let the first write finish and emit `true` into settings.
        firstWriteGate.complete(Unit)
        advanceUntilIdle()
        runCurrent()

        assertThat(sync.value).isFalse()
        assertThat(settings.value).isFalse()
        assertThat(persisted).containsExactly(true, false).inOrder()
        assertThat(uiCallbacks).isEmpty()

        // After settling, external changes still propagate.
        settings.value = true
        runCurrent()
        assertThat(sync.value).isTrue()
        assertThat(uiCallbacks).containsExactly(true)

        sync.stop()
    }

    @Test
    fun `stop stops external updates while letting an in-flight write persist`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MutableStateFlow(false)
        val writeGate = CompletableDeferred<Unit>()
        val uiCallbacks = mutableListOf<Boolean>()
        val sync = ToggleSync(
            initial = false,
            updates = settings,
            persist = { target ->
                writeGate.await()
                settings.value = target
            },
            onExternalValue = { uiCallbacks += it },
            scope = this,
            dispatcher = dispatcher,
        )
        sync.start()
        runCurrent()

        sync.setLocal(true)
        runCurrent()
        sync.stop()

        writeGate.complete(Unit)
        advanceUntilIdle()
        runCurrent()

        assertThat(settings.value).isTrue()

        settings.value = false
        runCurrent()
        assertThat(uiCallbacks).isEmpty()
    }

    @Test
    fun `concurrent external write arriving during local persist is reconciled`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MutableStateFlow(false)
        val writeGate = CompletableDeferred<Unit>()
        val uiCallbacks = mutableListOf<Boolean>()
        val sync = ToggleSync(
            initial = false,
            updates = settings,
            persist = { target ->
                settings.value = target
                writeGate.await()
            },
            onExternalValue = { uiCallbacks += it },
            scope = this,
            dispatcher = dispatcher,
        )
        sync.start()
        runCurrent()

        sync.setLocal(true)
        runCurrent()
        // An external write commits after our local write reached DataStore, before persist() returns.
        settings.value = false
        runCurrent()
        assertThat(uiCallbacks).isEmpty()

        writeGate.complete(Unit)
        advanceUntilIdle()
        runCurrent()

        assertThat(sync.value).isFalse()
        assertThat(uiCallbacks).containsExactly(false)
        sync.stop()
    }

    @Test
    fun `persistence failure does not cancel scope or block subsequent external updates`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MutableStateFlow(false)
        val uiCallbacks = mutableListOf<Boolean>()
        val sync = ToggleSync(
            initial = false,
            updates = settings,
            persist = { throw java.io.IOException("disk full") },
            onExternalValue = { uiCallbacks += it },
            scope = this,
            dispatcher = dispatcher,
        )
        sync.start()
        runCurrent()

        sync.setLocal(true)
        advanceUntilIdle()
        runCurrent()

        // The scope stayed alive and subsequent external changes still propagate.
        settings.value = true
        runCurrent()
        settings.value = false
        runCurrent()
        assertThat(sync.value).isFalse()
        assertThat(uiCallbacks).containsExactly(false)
        sync.stop()
    }
}
