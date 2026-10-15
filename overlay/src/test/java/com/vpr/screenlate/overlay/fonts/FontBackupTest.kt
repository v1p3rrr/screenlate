package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FontBackupTest {
    private val serif = InstalledFont("noto-serif-jp", "Noto Serif JP", listOf(FontFile("noto-serif-jp-0-a.otf")), "noto-serif-jp")
    private val own = InstalledFont("file-1", "My Font", listOf(FontFile("file-1-a.ttf")))

    @Test
    fun `a restored font replaces the same one and keeps the others`() {
        val restored = serif.copy(files = listOf(FontFile("noto-serif-jp-0-b.otf")))
        val (fonts, unused) = FontBackup.merge(listOf(serif, own), listOf(restored))
        assertThat(fonts).containsExactly(own, restored)
        assertThat(unused).containsExactly("noto-serif-jp-0-a.otf")
    }

    @Test
    fun `an own font replaces the own font of the same family`() {
        val other = InstalledFont("file-2", "My Font", listOf(FontFile("file-2-a.ttf")))
        val (fonts, unused) = FontBackup.merge(listOf(serif, own), listOf(other))
        assertThat(fonts).containsExactly(serif, other)
        assertThat(unused).containsExactly("file-1-a.ttf")
    }

    @Test
    fun `restoring the same files again deletes none of them`() {
        val (fonts, unused) = FontBackup.merge(listOf(serif), listOf(serif))
        assertThat(fonts).containsExactly(serif)
        assertThat(unused).isEmpty()
    }

    @Test
    fun `the list reads back`() {
        assertThat(FontBackup.decode(FontBackup.encode(listOf(serif, own)))).containsExactly(serif, own).inOrder()
    }
}
