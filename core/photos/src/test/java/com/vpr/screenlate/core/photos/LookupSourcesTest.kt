package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class LookupSourcesTest {
    private val sources = LookupSources()
    private val viewport = PhotoViewport.fit(4000, 3000, Box(0f, 0f, 1080f, 2400f))
    private val recognition = TestPhotos.recognition()

    @Test
    fun `the screen is the source until the Camera page presents`() {
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
        sources.present()
        assertThat(sources.source.value).isInstanceOf(LookupSource.NoText::class.java)
    }

    @Test
    fun `every change of source makes a new generation`() {
        val generations = mutableListOf(sources.source.value.generation)
        val page = sources.present()
        generations += sources.source.value.generation
        page.showPhoto(1, 1, recognition, viewport)
        generations += sources.source.value.generation
        page.showPhoto(1, 2, recognition, viewport)
        generations += sources.source.value.generation
        page.showPhoto(2, 1, recognition, viewport)
        generations += sources.source.value.generation
        page.showNothing()
        generations += sources.source.value.generation
        page.close()
        generations += sources.source.value.generation
        assertThat(generations).containsNoDuplicates()
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
    }

    @Test
    fun `moving the photo keeps its generation`() {
        val page = sources.present()
        page.showPhoto(1, 1, recognition, viewport)
        val shown = sources.source.value
        val moved = viewport.copy(scale = 1f, offsetX = -100f)
        page.moveViewport(moved)
        val after = sources.source.value as LookupSource.Photo
        assertThat(after.generation).isEqualTo(shown.generation)
        assertThat(after.viewport).isEqualTo(moved)
        // Showing the same photo and text again, as a page does after a layout pass, is a move too.
        page.showPhoto(1, 1, recognition, viewport)
        assertThat(sources.source.value.generation).isEqualTo(shown.generation)
    }

    @Test
    fun `moving without a photo changes nothing`() {
        val page = sources.present()
        val before = sources.source.value
        page.moveViewport(viewport)
        assertThat(sources.source.value).isSameInstanceAs(before)
        page.showNothing()
        assertThat(sources.source.value).isSameInstanceAs(before)
    }

    @Test
    fun `an older presentation can no longer change the source`() {
        val old = sources.present()
        val new = sources.present()
        new.showPhoto(1, 1, recognition, viewport)
        val shown = sources.source.value
        old.showNothing()
        old.moveViewport(viewport.copy(scale = 2f))
        old.close()
        assertThat(sources.source.value).isSameInstanceAs(shown)
        new.close()
        assertThat(sources.source.value).isInstanceOf(LookupSource.Screen::class.java)
    }

    @Test
    fun `a closed presentation stays closed`() {
        val page = sources.present()
        page.close()
        val screen = sources.source.value
        page.showPhoto(1, 1, recognition, viewport)
        page.close()
        assertThat(sources.source.value).isSameInstanceAs(screen)
    }
}
