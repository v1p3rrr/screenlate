package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PhotoGalleryTest {
    @Test
    fun publicationFollowsTheCompleteCopyAndStreamClose() = runTest {
        val sink = Sink()
        publishImage(sink) { it.write(byteArrayOf(1, 2, 3)) }
        assertThat(sink.events).containsExactly("create", "open", "close", "publish").inOrder()
        assertThat(sink.bytes.toByteArray()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun writeFailureOrCancellationNeverPublishesAPartialImage() = runTest {
        for (failure in listOf(IOException("test"), CancellationException("test"))) {
            val sink = Sink()
            try { publishImage(sink) { it.write(1); throw failure }; error("Expected failure") }
            catch (e: Exception) { assertThat(e).isSameInstanceAs(failure) }
            assertThat(sink.events).containsExactly("create", "open", "close", "remove").inOrder()
        }
    }

    @Test
    fun openCloseAndPublicationFailuresRemoveTheirPendingRow() = runTest {
        for (step in listOf("open", "close", "publish")) {
            val sink = Sink(step)
            try { publishImage(sink) { it.write(1) }; error("Expected failure") }
            catch (_: IOException) { }
            assertThat(sink.events.last()).isEqualTo("remove")
        }
    }

    private class Sink(private val fail: String? = null) : ImagePublication<Int> {
        val events = mutableListOf<String>()
        val bytes = object : ByteArrayOutputStream() { override fun close() { event("close") } }
        fun event(name: String) { events += name; if (fail == name) throw IOException("test") }
        override fun create(): Int { event("create"); return 1 }
        override fun open(item: Int) = bytes.also { event("open") }
        override fun publish(item: Int) { event("publish") }
        override fun remove(item: Int) { event("remove") }
    }
}
