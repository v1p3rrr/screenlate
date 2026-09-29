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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
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
    private val ocr = CompositeOcr(lens, mlKit, { online }, { now }, copyOf = { it }, release = {}, worker = EmptyCoroutineContext)

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

    /** A 2000-row image whose bands the fake engines read as the whole image. */
    private val focused = CompositeOcr(
        lens,
        mlKit,
        { online },
        { now },
        heightOf = { 2000 },
        cropRows = { image, _ -> image },
        copyOf = { it },
        release = {},
        worker = EmptyCoroutineContext,
    )

    @Test
    fun `the band around the aim comes before the whole image`() = runTest {
        lens.latency = 2.seconds
        val updates = focused.recognize(image, Language.JAPANESE, focus = { 1000f }).toList()
        // Band, whole image, then Lens.
        assertThat(updates.engines).containsExactly(
            false to OcrEngineType.ML_KIT,
            false to OcrEngineType.ML_KIT,
            true to OcrEngineType.LENS,
        ).inOrder()
        assertThat(mlKit.calls).isEqualTo(2)
        assertThat(updates.first().page.height).isEqualTo(2000)
    }

    @Test
    fun `an aim that moved away gets its own band`() = runTest {
        lens.latency = 2.seconds
        val aims = ArrayDeque(listOf(100f, 1500f, 1500f))
        focused.recognize(image, Language.JAPANESE, focus = { aims.removeFirstOrNull() ?: 1500f }).toList()
        // Two bands, then the whole image.
        assertThat(mlKit.calls).isEqualTo(3)
    }

    @Test
    fun `lens stops the on-device reading`() = runTest {
        lens.latency = 300.milliseconds
        val updates = focused.recognize(image, Language.JAPANESE, focus = { 1000f }).toList()
        assertThat(updates.engines).containsExactly(false to OcrEngineType.ML_KIT, true to OcrEngineType.LENS).inOrder()
        assertThat(testScheduler.currentTime).isEqualTo(300)
    }

    @Test
    fun `offline shows the band first and ends with the whole image`() = runTest {
        online = false
        val updates = focused.recognize(image, Language.JAPANESE, focus = { 1000f }).toList()
        assertThat(updates.engines).containsExactly(false to OcrEngineType.ML_KIT, true to OcrEngineType.ML_KIT).inOrder()
    }

    @Test
    fun `regions go to lens only`() = runTest {
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)?.engine).isEqualTo(OcrEngineType.LENS)
        assertThat(mlKit.calls).isEqualTo(0)
        lens.error = IOException("down")
        assertThat(ocr.recognizeRegion(image, Language.JAPANESE)).isNull()
    }

    @Test
    fun `the device reads a copy that is freed only once it is done, after lens`() = runTest {
        val copy = unsafe().allocateInstance(Bitmap::class.java) as Bitmap
        val read = mutableListOf<Bitmap>()
        var freedAt: Long? = null
        // Like ML Kit, which keeps reading after its caller is cancelled.
        val stubborn = object : OcrEngine {
            override val type = OcrEngineType.ML_KIT

            override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
                read += image
                withContext(NonCancellable) { delay(2.seconds) }
                return OcrPage(100, 100, emptyList(), type)
            }
        }
        lens.latency = 300.milliseconds
        val ocr = CompositeOcr(
            lens,
            stubborn,
            { online },
            { now },
            copyOf = { copy },
            release = { if (it === copy) freedAt = testScheduler.currentTime },
            worker = EmptyCoroutineContext,
        )

        val updates = ocr.recognize(image, Language.JAPANESE).toList()
        assertThat(updates.engines).containsExactly(true to OcrEngineType.LENS)
        assertThat(testScheduler.currentTime).isEqualTo(300)
        assertThat(freedAt).isNull()

        advanceUntilIdle()
        assertThat(freedAt).isEqualTo(2000)
        assertThat(read).containsExactly(copy)
    }

    private val saving = OcrOptions(deferWholeImage = true)

    @Test
    fun `saving, lens in time leaves the whole image unread`() = runTest {
        lens.latency = 2.seconds
        val updates = focused.recognize(image, Language.JAPANESE, saving, focus = { 1000f }).toList()
        assertThat(updates.engines).containsExactly(false to OcrEngineType.ML_KIT, true to OcrEngineType.LENS).inOrder()
        assertThat(mlKit.calls).isEqualTo(1)
    }

    @Test
    fun `saving, the whole image is read when lens is late`() = runTest {
        lens.latency = 5.seconds
        val updates = focused.recognize(image, Language.JAPANESE, saving, focus = { 1000f }).toList()
        // Band, whole image after three seconds, then Lens.
        assertThat(updates.engines).containsExactly(
            false to OcrEngineType.ML_KIT,
            false to OcrEngineType.ML_KIT,
            true to OcrEngineType.LENS,
        ).inOrder()
        assertThat(mlKit.calls).isEqualTo(2)
    }

    @Test
    fun `saving, a lens failure starts the whole image at once`() = runTest {
        lens.latency = 1.seconds
        lens.error = IOException("down")
        val updates = focused.recognize(image, Language.JAPANESE, saving, focus = { 1000f }).toList()
        assertThat(updates.last().page.engine).isEqualTo(OcrEngineType.ML_KIT)
        assertThat(testScheduler.currentTime).isEqualTo(1200)
    }

    @Test
    fun `saving, an aim that moves while waiting gets its band`() = runTest {
        lens.latency = 2.seconds
        focused.recognize(
            image,
            Language.JAPANESE,
            saving,
            focus = { if (testScheduler.currentTime < 500) 100f else 1500f },
        ).toList()
        assertThat(mlKit.calls).isEqualTo(2)
    }

    @Test
    fun `cloud only never uses the device`() = runTest {
        val updates = ocr.recognize(image, Language.JAPANESE, OcrOptions(OcrEngines.CLOUD)).toList()
        assertThat(updates.engines).containsExactly(true to OcrEngineType.LENS)
        assertThat(mlKit.calls).isEqualTo(0)
    }

    @Test
    fun `cloud only fails without network`() = runTest {
        online = false
        val error = runCatching { ocr.recognize(image, Language.JAPANESE, OcrOptions(OcrEngines.CLOUD)).toList() }
        assertThat(error.exceptionOrNull()).isInstanceOf(OfflineException::class.java)
        assertThat(lens.calls).isEqualTo(0)
    }

    @Test
    fun `cloud only reports a timeout as a failure, not a cancellation`() = runTest {
        lens.latency = 20.seconds
        val error = runCatching { ocr.recognize(image, Language.JAPANESE, OcrOptions(OcrEngines.CLOUD)).toList() }
        assertThat(error.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `device only never asks lens`() = runTest {
        val final = ocr.recognize(image, Language.JAPANESE, OcrOptions(OcrEngines.DEVICE)).toList().single()
        assertThat(final.page.engine).isEqualTo(OcrEngineType.ML_KIT)
        assertThat((final as OcrUpdate.Final).lensError).isNull()
        assertThat(lens.calls).isEqualTo(0)
    }
}
