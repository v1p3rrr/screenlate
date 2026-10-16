package com.vpr.screenlate.core.ocr.paddle

/** Floats in row-major order with their [shape]. */
class Tensor(val data: FloatArray, val shape: IntArray)

/** One network of the PP-OCR pipeline as an inference runtime runs it: one float input, one float output. */
interface PaddleModel : AutoCloseable {
    fun run(input: Tensor): Tensor
}
