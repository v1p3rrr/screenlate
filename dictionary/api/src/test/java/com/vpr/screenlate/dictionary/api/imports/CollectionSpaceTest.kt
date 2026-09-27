package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionSpaceTest {
    private val mb = 1L shl 20

    private fun size(textMb: Long, mediaMb: Long = 0, rows: Long = 0) = CollectionSize("d", rows, textMb * mb, mediaMb * mb)

    @Test
    fun `archives shrink when compressed except for media`() {
        val size = size(textMb = 100, mediaMb = 50)
        assertThat(CollectionSpace.archiveBytes(size, compressed = false)).isEqualTo(150 * mb)
        assertThat(CollectionSpace.archiveBytes(size, compressed = true)).isEqualTo(90 * mb)
    }

    @Test
    fun `installed size grows with text, rows and media`() {
        val base = CollectionSpace.installedBytes(size(textMb = 10))
        assertThat(CollectionSpace.installedBytes(size(textMb = 20))).isGreaterThan(base)
        assertThat(CollectionSpace.installedBytes(size(textMb = 10, rows = 1000))).isGreaterThan(base)
        assertThat(CollectionSpace.installedBytes(size(textMb = 10, mediaMb = 5))).isEqualTo(base + 5 * mb)
    }

    /** All archives exist before the first import; each is deleted after its dictionary is installed. */
    @Test
    fun `peak follows the import order`() {
        val sizes = listOf(size(textMb = 100), size(textMb = 10), size(textMb = 1))
        val archives = sizes.map { CollectionSpace.archiveBytes(it, compressed = false) }
        val installed = sizes.map { CollectionSpace.installedBytes(it) }
        val expected = listOf(
            archives.sum() + installed[0],
            archives[1] + archives[2] + installed[0] + installed[1],
            archives[2] + installed.sum(),
        ).max()
        assertThat(CollectionSpace.peakBytes(sizes, compressed = false)).isEqualTo(expected)
        assertThat(CollectionSpace.peakBytes(emptyList(), compressed = false)).isEqualTo(0)
    }

    @Test
    fun `plans uncompressed archives when they fit, compressed ones when only those fit`() {
        val sizes = listOf(size(textMb = 1000, rows = 100_000), size(textMb = 200, mediaMb = 300, rows = 50_000))
        val uncompressed = CollectionSpace.peakBytes(sizes, compressed = false) + CollectionSpace.RESERVE_BYTES
        val compressed = CollectionSpace.peakBytes(sizes, compressed = true) + CollectionSpace.RESERVE_BYTES
        assertThat(compressed).isLessThan(uncompressed)

        assertThat(CollectionSpace.plan(sizes, uncompressed)).isEqualTo(CollectionSpacePlan.Uncompressed)
        assertThat(CollectionSpace.plan(sizes, uncompressed - 1)).isEqualTo(CollectionSpacePlan.Compressed)
        assertThat(CollectionSpace.plan(sizes, compressed)).isEqualTo(CollectionSpacePlan.Compressed)
        assertThat(CollectionSpace.plan(sizes, compressed - 1)).isEqualTo(CollectionSpacePlan.NotEnough(compressed, compressed - 1))
    }

    @Test
    fun `nothing to import still keeps the reserve`() {
        assertThat(CollectionSpace.plan(emptyList(), CollectionSpace.RESERVE_BYTES)).isEqualTo(CollectionSpacePlan.Uncompressed)
        assertThat(CollectionSpace.plan(emptyList(), 0)).isEqualTo(CollectionSpacePlan.NotEnough(CollectionSpace.RESERVE_BYTES, 0))
    }
}
