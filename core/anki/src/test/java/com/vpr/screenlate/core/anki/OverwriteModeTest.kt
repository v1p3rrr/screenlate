package com.vpr.screenlate.core.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import org.junit.Test

class OverwriteModeTest {
    @Test
    fun `coalesce modes preserve or take non-empty values`() {
        assertThat(OverwriteMode.COALESCE.apply("old", "new")).isEqualTo("old")
        assertThat(OverwriteMode.COALESCE.apply("", "new")).isEqualTo("new")
        assertThat(OverwriteMode.COALESCE_NEW.apply("old", "new")).isEqualTo("new")
        assertThat(OverwriteMode.COALESCE_NEW.apply("old", "")).isEqualTo("old")
    }

    @Test
    fun `overwrite and skip behave directly`() {
        assertThat(OverwriteMode.OVERWRITE.apply("old", "new")).isEqualTo("new")
        assertThat(OverwriteMode.SKIP.apply("old", "new")).isEqualTo("old")
    }

    @Test
    fun `append joins text with br separator`() {
        assertThat(OverwriteMode.APPEND.apply("First sentence.", "Second sentence."))
            .isEqualTo("First sentence.<br>Second sentence.")
        assertThat(OverwriteMode.APPEND.apply("", "Second sentence."))
            .isEqualTo("Second sentence.")
        assertThat(OverwriteMode.APPEND.apply("First sentence.", ""))
            .isEqualTo("First sentence.")
    }

    @Test
    fun `append does not duplicate identical sentences`() {
        val existing = "First sentence.<br>Second sentence."
        assertThat(OverwriteMode.APPEND.apply(existing, "Second sentence.")).isEqualTo(existing)
        assertThat(OverwriteMode.APPEND.apply(existing, "  Second sentence.  ")).isEqualTo(existing)
        assertThat(OverwriteMode.APPEND.apply(existing, "Third sentence."))
            .isEqualTo("First sentence.<br>Second sentence.<br>Third sentence.")

        // Recognizes XHTML br tags (<br/>, <br />) for idempotency
        val xhtmlExisting = "First sentence.<br/>Second sentence.<br />Third sentence."
        assertThat(OverwriteMode.APPEND.apply(xhtmlExisting, "Second sentence.")).isEqualTo(xhtmlExisting)
        assertThat(OverwriteMode.APPEND.apply(xhtmlExisting, "Third sentence.")).isEqualTo(xhtmlExisting)
    }

    @Test
    fun `append joins media and scene groups without extra br`() {
        val img1 = "<img src=\"shot1.png\">"
        val img2 = "<img src=\"shot2.png\">"
        assertThat(OverwriteMode.APPEND.apply(img1, img2)).isEqualTo("<img src=\"shot1.png\"><img src=\"shot2.png\">")

        val audio1 = "[sound:1.mp3]"
        val audio2 = "[sound:2.mp3]"
        assertThat(OverwriteMode.APPEND.apply(audio1, audio2)).isEqualTo("[sound:1.mp3][sound:2.mp3]")

        val group1 = "<span class=\"group\">Scene 1</span>"
        val group2 = "<span class=\"group\">Scene 2</span>"
        assertThat(OverwriteMode.APPEND.apply(group1, group2)).isEqualTo("<span class=\"group\">Scene 1</span><span class=\"group\">Scene 2</span>")
        assertThat(OverwriteMode.APPEND.apply(group1, group1)).isEqualTo(group1) // identical group not repeated
    }

    @Test
    fun `prepend places new value before existing with br for text and directly for media`() {
        assertThat(OverwriteMode.PREPEND.apply("Existing sentence.", "New sentence."))
            .isEqualTo("New sentence.<br>Existing sentence.")
        assertThat(OverwriteMode.PREPEND.apply("<img src=\"old.png\">", "<img src=\"new.png\">"))
            .isEqualTo("<img src=\"new.png\"><img src=\"old.png\">")
        assertThat(OverwriteMode.PREPEND.apply("Sentence.", "Sentence.")).isEqualTo("Sentence.")
    }
}
