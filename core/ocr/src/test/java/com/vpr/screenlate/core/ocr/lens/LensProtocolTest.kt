package com.vpr.screenlate.core.ocr.lens

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Assert.assertThrows
import org.junit.Test

class LensProtocolTest {

    @Test
    fun `request round-trips through the reader`() {
        val image = byteArrayOf(1, 2, 3, 4)
        val request = LensProtocol.buildRequest(image, width = 674, height = 1500, language = "ja", requestId = 1234567890123L)

        var parsedImage: ByteArray? = null
        var width = 0L
        var height = 0L
        var language = ""
        var platform = 0L
        ProtoReader(request).forEachField { root ->
            root.message().forEachField { objects ->
                when (objects.number) {
                    1 -> objects.message().forEachField { context ->
                        if (context.number == 4) {
                            context.message().forEachField { client ->
                                when (client.number) {
                                    1 -> platform = client.varint()
                                    4 -> client.message().forEachField { locale ->
                                        if (locale.number == 1) language = locale.string()
                                    }
                                }
                            }
                        }
                    }
                    3 -> objects.message().forEachField { data ->
                        when (data.number) {
                            1 -> data.message().forEachField { payload ->
                                if (payload.number == 1) parsedImage = payload.bytes()
                            }
                            3 -> data.message().forEachField { meta ->
                                when (meta.number) {
                                    1 -> width = meta.varint()
                                    2 -> height = meta.varint()
                                }
                            }
                        }
                    }
                }
            }
        }

        assertThat(platform).isEqualTo(3)
        assertThat(language).isEqualTo("ja")
        assertThat(width).isEqualTo(674)
        assertThat(height).isEqualTo(1500)
        assertThat(parsedImage).isEqualTo(image)
    }

    @Test
    fun `writer encodes multi-byte varints`() {
        val bytes = ProtoWriter().varint(1, 300).toByteArray()
        assertThat(bytes.toList()).containsExactly(0x08.toByte(), 0xAC.toByte(), 0x02.toByte()).inOrder()
    }

    @Test
    fun `reader rejects lengths that leave the message`() {
        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        val garbled = listOf(
            // Field 1 with the length 2^32 - 6, -6 as an Int: back to the tag, which skipping read forever.
            bytes(0x0A, 0xFA, 0xFF, 0xFF, 0xFF, 0x0F, 0x41),
            // 2^32 + 1, 1 as an Int.
            bytes(0x0A, 0x81, 0x80, 0x80, 0x80, 0x10, 0x41),
            bytes(0x0A, 0x05, 0x41),
            // A fixed32 float with two of its bytes.
            bytes(0x0D, 0x00, 0x00),
        )
        for (message in garbled) {
            assertThrows(IllegalStateException::class.java) { ProtoReader(message).forEachField {} }
            assertThrows(IllegalStateException::class.java) {
                ProtoReader(message).forEachField { if (it.wireType == 5) it.float() else it.bytes() }
            }
        }
    }

    @Test
    fun `parses a recorded response`() {
        val response = javaClass.classLoader!!.getResourceAsStream("lens_response_synthetic.bin")!!.readBytes()

        val page = LensProtocol.parseResponse(response, width = 1344, height = 2992)
        val lines = page.paragraphs.flatMap { it.lines }

        assertThat(lines.map { it.text }).containsAtLeast(
            "吾輩は猫である。名前はまだ無い。",
            "食べさせられなかったのは残念だ。",
            "縦書きの文章も正しく認識できるか",
        )
        val horizontal = lines.first { it.text.startsWith("吾輩") }
        assertThat(horizontal.vertical).isFalse()
        assertThat(horizontal.box.left).isWithin(20f).of(60f)
        assertThat(horizontal.box.top).isWithin(20f).of(120f)

        val vertical = lines.first { it.text.startsWith("縦書き") }
        assertThat(vertical.vertical).isTrue()
        assertThat(vertical.box.centerX).isGreaterThan(1200f)
    }

    @Test
    fun `a Latin run in a right-to-left line comes back left to right`() {
        fun word(text: String, left: Float, separator: String = " ") =
            OcrWord(text, separator, Box(left, 0f, left + 10f * text.length, 20f))
        // "קניתי iPhone 15 Pro, עכשיו" given from right to left by the boxes.
        val words = listOf(
            word("קניתי", 300f),
            word("Pro", 250f),
            word("15", 220f),
            word("iPhone", 150f, separator = ""),
            word(",", 145f),
            word("עכשיו", 50f, separator = ""),
        )

        val ordered = LensProtocol.leftToRightRuns(words)

        assertThat(ordered.map { it.text }).containsExactly("קניתי", "iPhone", "15", "Pro", ",", "עכשיו").inOrder()
        assertThat(ordered.map { it.separator }).containsExactly(" ", " ", " ", "", " ", "").inOrder()
        assertThat(ordered[1].box.left).isEqualTo(150f)
        // A line in reading order already, as Lens gives Arabic ones, and numbers alone stay as they are.
        val arabic = listOf(word("اشتريت", 300f), word("iPhone", 200f), word("15", 270f, separator = ""))
        assertThat(LensProtocol.leftToRightRuns(arabic).map { it.text }).containsExactly("اشتريت", "iPhone", "15").inOrder()
        val numbers = listOf(word("عام", 300f), word("2024", 250f), word("2025", 200f, separator = ""))
        assertThat(LensProtocol.leftToRightRuns(numbers)).isEqualTo(numbers)
    }

    @Test
    fun `a right-to-left line Lens gives out of order is put in order by its boxes`() {
        fun word(text: String, left: Float, right: Float, separator: String = " ") =
            OcrWord(text, separator, Box(left, 0f, right, 80f))
        // "קניתי iPhone 15 ב־3,500 ₪ היום." as cloud recognition gave it, the period joined to ב־.
        val words = listOf(
            word(".ב־", 300f, 740f, separator = ""),
            word("3,500", 521f, 681f),
            word("₪", 460f, 500f),
            word("היום", 314f, 432f),
            word("iPhone", 758f, 961f),
            word("15", 990f, 1051f),
            word("קניתי", 1074f, 1215f, separator = ""),
        )

        val ordered = LensProtocol.inReadingOrder(words, 80f)

        assertThat(ordered.joinToString("") { it.text + it.separator }).isEqualTo("קניתי iPhone 15 .ב־3,500 ₪ היום")
        // Lines in reading order keep the separators Lens gives, a Latin run put back left to right.
        val hebrew = listOf(
            word("קניתי", 300f, 350f),
            word("15", 220f, 240f),
            word("iPhone", 150f, 210f, separator = ""),
            word(",", 145f, 155f),
            word("עכשיו", 50f, 100f, separator = ""),
        )
        assertThat(LensProtocol.inReadingOrder(hebrew, 80f).joinToString("") { it.text + it.separator })
            .isEqualTo("קניתי iPhone 15, עכשיו")
        val arabic = listOf(word("اشتريت", 300f, 360f), word("iPhone", 200f, 260f), word("15", 270f, 290f, separator = ""))
        assertThat(LensProtocol.inReadingOrder(arabic, 80f)).isEqualTo(LensProtocol.leftToRightRuns(arabic))
    }

    @Test
    fun `elements without a size are dropped instead of placed at the corner`() {
        fun ProtoWriter.geometry(field: Int, cx: Float, cy: Float, w: Float, h: Float) = message(field) {
            message(1) {
                float(1, cx)
                float(2, cy)
                float(3, w)
                float(4, h)
            }
        }
        val response = ProtoWriter().message(2) {
            message(3) {
                message(1) {
                    message(1) {
                        message(2) {
                            message(1) {
                                string(2, "猫")
                                geometry(4, 0.5f, 0.5f, 0.1f, 0.05f)
                            }
                            message(1) {
                                string(2, "犬")
                                message(4) { message(1) {} }
                            }
                            // A line geometry without a size falls back to its words.
                            message(2) { message(1) {} }
                        }
                    }
                }
            }
        }.toByteArray()

        val line = LensProtocol.parseResponse(response, width = 1000, height = 2000).paragraphs.single().lines.single()

        assertThat(line.text).isEqualTo("猫")
        assertThat(line.box.left).isWithin(0.5f).of(450f)
        assertThat(line.box.top).isWithin(0.5f).of(950f)
    }
}
