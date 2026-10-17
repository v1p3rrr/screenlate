package com.vpr.screenlate.experiments.ocrcompare

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.vpr.screenlate.core.ocr.paddle.PaddleModel
import com.vpr.screenlate.core.ocr.paddle.Tensor
import java.nio.FloatBuffer

/** A PP-OCR network on ONNX Runtime's CPU provider. */
class OrtModel(model: ByteArray, threads: Int) : PaddleModel {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val session = environment.createSession(model, options)

    override fun run(input: Tensor): Tensor {
        val shape = LongArray(input.shape.size) { input.shape[it].toLong() }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), shape).use { tensor ->
            session.run(mapOf("x" to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val buffer = output.floatBuffer
                val data = FloatArray(buffer.remaining()).also { buffer.get(it) }
                return Tensor(data, output.info.shape.map { it.toInt() }.toIntArray())
            }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }
}

/**
 * A text detector whose probabilities are mapped linearly so that the pipeline's fixed thresholds (0.3 for a pixel,
 * 0.6 for a line's mean) stand for [threshold] and [boxThreshold], the values the detector was trained for.
 */
class RescaledDetector(private val model: PaddleModel, threshold: Float, boxThreshold: Float) : PaddleModel {
    private val scale = (PIPELINE_BOX_THRESHOLD - PIPELINE_THRESHOLD) / (boxThreshold - threshold)
    private val offset = PIPELINE_THRESHOLD - threshold * scale

    override fun run(input: Tensor): Tensor {
        val output = model.run(input)
        val data = output.data
        for (i in data.indices) data[i] = data[i] * scale + offset
        return output
    }

    override fun close() = model.close()

    private companion object {
        /** core:ocr's TextDetection.THRESHOLD and BOX_THRESHOLD. */
        const val PIPELINE_THRESHOLD = 0.3f
        const val PIPELINE_BOX_THRESHOLD = 0.6f
    }
}

/** A PP-OCR network on MNN's CPU backend, at fp32. */
class MnnModel(path: String, threads: Int) : PaddleModel {
    private var handle = nativeLoad(path, threads).also { check(it != 0L) { "MNN could not load $path" } }

    override fun run(input: Tensor): Tensor {
        val data = nativeRun(handle, input.data, input.shape)
        return Tensor(data, nativeOutputShape(handle))
    }

    override fun close() {
        if (handle != 0L) nativeRelease(handle)
        handle = 0L
    }

    private companion object {
        init {
            System.loadLibrary("mnnocr")
        }

        @JvmStatic external fun nativeLoad(path: String, threads: Int): Long

        @JvmStatic external fun nativeRun(handle: Long, data: FloatArray, shape: IntArray): FloatArray

        @JvmStatic external fun nativeOutputShape(handle: Long): IntArray

        @JvmStatic external fun nativeRelease(handle: Long)
    }
}
