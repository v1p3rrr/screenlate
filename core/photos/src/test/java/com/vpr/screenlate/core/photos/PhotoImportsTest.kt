package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoImportsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aReadableImageKeepsItsOriginalBytes() = runTest {
        val target = folder.newFile()
        val formats = Formats(readable = true)
        importImage(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target, formats)
        assertThat(target.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(formats.converted).isFalse()
    }

    @Test
    fun anotherFormatIsConverted() = runTest {
        val target = folder.newFile()
        val formats = Formats(readable = false, converts = true)
        importImage(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target, formats)
        assertThat(formats.converted).isTrue()
        assertThat(target.readBytes()).isEqualTo(byteArrayOf(9))
    }

    @Test
    fun aFileThePhoneCannotDecodeIsRejectedAndDeleted() = runTest {
        val target = folder.newFile()
        try {
            importImage(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target, Formats(readable = false, converts = false))
            error("Expected failure")
        } catch (_: InvalidPhotoException) { }
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun aFailedOrCancelledCopyLeavesNoPartialFile() = runTest {
        for (failure in listOf(IOException("test"), CancellationException("test"))) {
            val target = folder.newFile()
            val input = object : InputStream() {
                var served = false
                override fun read(): Int = error("Unused")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (served) throw failure
                    served = true
                    b[off] = 1
                    return 1
                }
            }
            try { importImage(input, target, Formats(readable = true)); error("Expected failure") }
            catch (e: Exception) { assertThat(e).isSameInstanceAs(failure) }
            assertThat(target.exists()).isFalse()
        }
    }

    private class Formats(private val readable: Boolean, private val converts: Boolean = false) : ImageFormats {
        var converted = false
        override fun readable(file: File) = readable
        override fun convert(file: File): Boolean {
            if (!converts) return false
            converted = true
            file.writeBytes(byteArrayOf(9))
            return true
        }
    }
}
