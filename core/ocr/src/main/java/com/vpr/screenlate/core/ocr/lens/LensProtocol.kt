package com.vpr.screenlate.core.ocr.lens

import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.ocr.isVerticalLine
import kotlin.math.cos
import kotlin.math.sin

/**
 * Encoding and decoding of the Lens `crupload` protobuf messages.
 *
 * Field numbers follow chrome-lens-ocr's `proto.rs`; see `ai/notes/lens-protocol.md` for the full map.
 */
internal object LensProtocol {
    private const val PLATFORM_WEB = 3L
    private const val SURFACE_CHROMIUM = 4L

    fun buildRequest(image: ByteArray, width: Int, height: Int, language: String, requestId: Long): ByteArray =
        ProtoWriter().message(1) { // objects_request
            message(1) { // request_context
                message(3) { // request_id
                    varint(1, requestId)
                    varint(2, 1)
                    varint(3, 1)
                }
                message(4) { // client_context
                    varint(1, PLATFORM_WEB)
                    varint(2, SURFACE_CHROMIUM)
                    message(4) { // locale_context
                        string(1, language)
                        string(2, "US")
                        string(3, "America/New_York")
                    }
                }
            }
            message(3) { // image_data
                message(1) { bytes(1, image) } // payload
                message(3) { // image_metadata
                    varint(1, width.toLong())
                    varint(2, height.toLong())
                }
            }
        }.toByteArray()

    /**
     * Parses a response into an [OcrPage] sized [width] x [height]. Lens returns coordinates normalized to the
     * uploaded image, so any downscaling before upload does not matter as long as the aspect ratio is kept.
     */
    fun parseResponse(response: ByteArray, width: Int, height: Int): OcrPage {
        val paragraphs = mutableListOf<OcrParagraph>()
        ProtoReader(response).forEachField { field ->
            if (field.number == 2) { // objects_response
                field.message().forEachField { objects ->
                    if (objects.number == 3) { // text
                        objects.message().forEachField { text ->
                            if (text.number == 1) { // text_layout
                                text.message().forEachField { layout ->
                                    if (layout.number == 1) {
                                        paragraphs += parseParagraph(layout.message(), width, height)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return OcrPage(width, height, paragraphs.filter { it.lines.isNotEmpty() }, OcrEngineType.LENS)
    }

    private fun parseParagraph(reader: ProtoReader, width: Int, height: Int): OcrParagraph {
        val lines = mutableListOf<OcrLine>()
        reader.forEachField { field ->
            if (field.number == 2) parseLine(field.message(), width, height)?.let(lines::add)
        }
        return OcrParagraph(lines)
    }

    private fun parseLine(reader: ProtoReader, width: Int, height: Int): OcrLine? {
        val words = mutableListOf<OcrWord>()
        var lineBox: Box? = null
        reader.forEachField { field ->
            when (field.number) {
                1 -> parseWord(field.message(), width, height)?.let(words::add)
                2 -> lineBox = parseGeometry(field.message(), width, height)
            }
        }
        if (words.isEmpty()) return null
        val box = lineBox ?: Box.unionOf(words.map { it.box }) ?: return null
        val text = words.joinToString("") { it.text }
        return OcrLine(words, box, isVerticalLine(box, text))
    }

    private fun parseWord(reader: ProtoReader, width: Int, height: Int): OcrWord? {
        var text = ""
        var separator = ""
        var box: Box? = null
        reader.forEachField { field ->
            when (field.number) {
                2 -> text = field.string()
                3 -> separator = field.string()
                4 -> box = parseGeometry(field.message(), width, height)
            }
        }
        val wordBox = box ?: return null
        if (text.isEmpty()) return null
        return OcrWord(text, separator, wordBox)
    }

    /** Converts a normalized center-rotated box into an axis-aligned box in pixels. */
    private fun parseGeometry(reader: ProtoReader, width: Int, height: Int): Box? {
        var result: Box? = null
        reader.forEachField { geometry ->
            if (geometry.number == 1) {
                var cx = 0f
                var cy = 0f
                var w = 0f
                var h = 0f
                var rotation = 0f
                geometry.message().forEachField { box ->
                    when (box.number) {
                        1 -> cx = box.float()
                        2 -> cy = box.float()
                        3 -> w = box.float()
                        4 -> h = box.float()
                        5 -> rotation = box.float()
                    }
                }
                result = rotatedToAxisAligned(cx * width, cy * height, w * width, h * height, rotation)
            }
        }
        return result
    }

    private fun rotatedToAxisAligned(cx: Float, cy: Float, w: Float, h: Float, rotation: Float): Box {
        if (rotation == 0f) return Box.fromCenter(cx, cy, w, h)
        val cos = cos(rotation)
        val sin = sin(rotation)
        val halfW = w / 2f
        val halfH = h / 2f
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        listOf(-halfW to -halfH, halfW to -halfH, halfW to halfH, -halfW to halfH).forEachIndexed { i, (dx, dy) ->
            xs[i] = dx * cos - dy * sin + cx
            ys[i] = dx * sin + dy * cos + cy
        }
        return Box(xs.min(), ys.min(), xs.max(), ys.max())
    }
}
