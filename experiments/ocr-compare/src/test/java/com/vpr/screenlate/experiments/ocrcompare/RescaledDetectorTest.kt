package com.vpr.screenlate.experiments.ocrcompare

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.ocr.paddle.PaddleModel
import com.vpr.screenlate.core.ocr.paddle.Tensor
import org.junit.Test

class RescaledDetectorTest {
    private class Fixed(private val values: FloatArray) : PaddleModel {
        var closed = false

        override fun run(input: Tensor) = Tensor(values.copyOf(), intArrayOf(1, 1, 1, values.size))

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `the detector's own thresholds land on the pipeline's`() {
        val detector = RescaledDetector(Fixed(floatArrayOf(0.2f, 0.45f, 0f, 1f)), threshold = 0.2f, boxThreshold = 0.45f)
        val output = detector.run(Tensor(FloatArray(1), intArrayOf(1)))
        assertThat(output.data).usingTolerance(1e-6).containsExactly(0.3f, 0.6f, 0.06f, 1.26f).inOrder()
        assertThat(output.shape.toList()).containsExactly(1, 1, 1, 4).inOrder()
    }

    @Test
    fun `closing closes the wrapped model`() {
        val model = Fixed(FloatArray(1))
        RescaledDetector(model, 0.2f, 0.45f).close()
        assertThat(model.closed).isTrue()
    }
}
