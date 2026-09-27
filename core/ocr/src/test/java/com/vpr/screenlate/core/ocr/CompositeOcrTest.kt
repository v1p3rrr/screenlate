package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class CompositeOcrTest {

    /** Answers after [latency] with a one-line page, or fails with [error]. */
    private class FakeEngine(override val type: OcrEngineType, var latency: Duration, var error: Throwable? = null) : OcrEngine {
        var calls = 0

        override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
            calls++
            delay(latency)
            error?.let { throw it }
            return OcrPage(100, 100, listOf(OcrParagraph(emptyList())), type)
        }
    }

    private val lens = FakeEngine(OcrEngineType.LENS, 800.milliseconds)
    private val mlKit = FakeEngine(OcrEngineType.ML_KIT, 200.milliseconds)
    private var online = true
    private var now = 0L
    private val ocr = CompositeOcr(lens, mlKit, { online }, { now })

    /** The engines are fakes and never look at the image; the Android stub cannot be constructed normally. */
    private val image: Bitmap = unsafe().allocateInstance(Bitmap::class.java) as Bitmap

    private fun unsafe(): sun.misc.Unsafe =
        sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe

    private suspend fun updates() = ocr.recognize(image, Language.JAPANESE).toList()

    private val List<OcrUpdate>.engines get() = map { (it is OcrUpdate.Final) to it.page.engine }

    @Test
    fun `the on-device draft comes first, then lens`() = runTest {
        val updates = updates()
        assertThat(updates.engines).containsExactly(false to OcrEngineType.ML_KIT, true to OcrEngineType.LENS).inOrder()
        assertThat((updates.last() as OcrUpdate.Final).lensError).isNull()
    }

    @Test
    fun `no draft after a faster lens`() = runTest {
        mlKit.latency = 2.seconds
        assertThat(updates().engines).containsExactly(true to OcrEngineType.LENS)
    }

    @Test
    fun `a lens failure makes the draft final`() = runTest {
        val error = IOException("down")
        lens.error = error
        val final = updates().last() as OcrUpdate.Final
        assertThat(final.page.engine).isEqualTo(OcrEngineType.ML_KIT)
        // Coroutines may hand over a copy of the exception with a recovered stack trace.
        assertThat(final.lensError).isInstanceOf(IOException::class.java)
        assertThat(final.lensError).hasMessageThat().isEqualTo(error.message)
    }

    @Test
    fun `lens times out after fifteen seconds`() = runTest {
        lens.latency = 20.seconds
        val final = updates().last() as OcrUpdate.Final
        assertThat(final.page.engine).isEqualTo(OcrEngineType.ML_KIT)
        assertThat(final.lensError).isInstanceOf(kotlinx.coroutines.TimeoutCancellationException::class.java)
        assertThat(testScheduler.currentTime).isEqualTo(15_000)
    }

    @Test
    fun `offline uses only the device`() = runTest {
        online = false
        val updates = updates()
        assertThat(updates.engines).containsExactly(true to OcrEngineType.ML_KIT)
        assertThat((updates.single() as OcrUpdate.Final).lensError).isInstanceOf(OfflineException::class.java)
        assertThat(lens.calls).isEqualTo(0)
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)).isNull()
    }

    @Test
    fun `both engines failing fails the scan`() = runTest {
        lens.error = IOException("lens")
        mlKit.error = IllegalStateException("device")
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { updates() } }
    }

    @Test
    fun `refused requests pause lens for a while`() = runTest {
        lens.error = LensHttpException(429)
        updates()
        lens.error = null
        val paused = updates().single() as OcrUpdate.Final
        assertThat(paused.lensError).isInstanceOf(LensPausedException::class.java)
        assertThat(lens.calls).isEqualTo(1)
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)).isNull()

        now += 5.minutes.inWholeMilliseconds
        assertThat(updates().last().page.engine).isEqualTo(OcrEngineType.LENS)
    }

    @Test
    fun `other lens errors do not pause it`() = runTest {
        lens.error = LensHttpException(500)
        updates()
        lens.error = null
        assertThat(updates().last().page.engine).isEqualTo(OcrEngineType.LENS)
    }

    @Test
    fun `regions go to lens only`() = runTest {
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)?.engine).isEqualTo(OcrEngineType.LENS)
        assertThat(mlKit.calls).isEqualTo(0)
        lens.error = IOException("down")
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)).isNull()
    }
}
