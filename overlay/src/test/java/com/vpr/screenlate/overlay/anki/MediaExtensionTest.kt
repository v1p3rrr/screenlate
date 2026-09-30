package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaExtensionTest {
    @Test
    fun takesTheFileNamesExtension() {
        assertThat(mediaExtension("img/word.PNG")).isEqualTo("png")
        assertThat(mediaExtension("a.b/c.webp")).isEqualTo("webp")
    }

    @Test
    fun aDotInAFolderOrNoExtensionGivesPng() {
        assertThat(mediaExtension("img.v2/word")).isEqualTo("png")
        assertThat(mediaExtension("word")).isEqualTo("png")
        assertThat(mediaExtension("word.")).isEqualTo("png")
    }
}
