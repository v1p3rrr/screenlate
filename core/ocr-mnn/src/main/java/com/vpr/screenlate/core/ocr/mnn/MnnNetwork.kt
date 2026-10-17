package com.vpr.screenlate.core.ocr.mnn

import java.io.IOException

/** Floats in row-major order with their [shape]. */
class MnnTensor(val data: FloatArray, val shape: IntArray)

/**
 * A network in MNN's format with one float input and one float output, run on the CPU at fp32 by [threads] threads.
 * Not for concurrent use.
 *
 * @throws IOException when MNN cannot load [path].
 */
class MnnNetwork(path: String, threads: Int) : AutoCloseable {
    private var handle = nativeLoad(path, threads).also { if (it == 0L) throw IOException("MNN could not load the model") }

    /** Runs the network on [input]; a new input shape resizes it first. */
    fun run(input: MnnTensor): MnnTensor {
        check(handle != 0L) { "The network is closed" }
        val data = nativeRun(handle, input.data, input.shape)
        return MnnTensor(data, nativeOutputShape(handle))
    }

    override fun close() {
        if (handle != 0L) nativeRelease(handle)
        handle = 0L
    }

    private companion object {
        init {
            System.loadLibrary("screenlate_mnn")
        }

        @JvmStatic external fun nativeLoad(path: String, threads: Int): Long

        @JvmStatic external fun nativeRun(handle: Long, data: FloatArray, shape: IntArray): FloatArray

        @JvmStatic external fun nativeOutputShape(handle: Long): IntArray

        @JvmStatic external fun nativeRelease(handle: Long)
    }
}
