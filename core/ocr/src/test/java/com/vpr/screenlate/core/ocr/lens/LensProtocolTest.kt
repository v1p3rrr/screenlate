package com.vpr.screenlate.core.ocr.lens

import com.google.common.truth.Truth.assertThat
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
}
