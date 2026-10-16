package com.vpr.screenlate.experiments.ocrcompare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LensReaderTest {
    private fun field(number: Int, content: ByteArray) =
        byteArrayOf((number shl 3 or 2).toByte(), content.size.toByte()) + content

    private fun field(number: Int, text: String) = field(number, text.toByteArray())

    private fun varint(number: Int, value: Int) = byteArrayOf((number shl 3).toByte(), value.toByte())

    private fun request(language: String) = field(
        1,
        field(1, varint(2, 1) + field(4, varint(1, 3) + field(4, field(1, language) + field(2, "US")))) +
            field(3, field(1, byteArrayOf(1, 2, 3))),
    )

    @Test
    fun `the nested field gets the new value and every enclosing length grows with it`() {
        val replaced = replaceField(request("en"), listOf(1, 1, 4, 4, 1), "zh-TW".toByteArray())
        assertThat(replaced).isEqualTo(request("zh-TW"))
    }

    @Test
    fun `a message without the path stays as it was`() {
        val other = field(2, varint(1, 5) + field(3, "x"))
        assertThat(replaceField(other, listOf(1, 1), "ja".toByteArray())).isEqualTo(other)
    }
}
