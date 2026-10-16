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
