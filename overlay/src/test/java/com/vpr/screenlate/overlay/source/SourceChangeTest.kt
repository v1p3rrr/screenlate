package com.vpr.screenlate.overlay.source

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.photos.LookupSource
import com.vpr.screenlate.core.photos.LookupSources
import org.junit.Test

class SourceChangeTest {
    private val sources = LookupSources()
    private val current: LookupSource get() = sources.source.value

    @Test
    fun theSameSourceChangesNothing() {
        val screen = current
        assertThat(sourceChange(screen, current)).isEqualTo(SourceChange.NONE)
    }

    @Test
    fun movingOrZoomingThePhotoKeepsItsText() {
        val camera = sources.present()
        camera.showPhoto(1, 1, recognition(), viewport())
        val shown = current
        camera.moveViewport(viewport(scale = 2f, offsetX = -100f))
        assertThat(sourceChange(shown, current)).isEqualTo(SourceChange.MOVED)
        // The page shows the same photo again, as after the viewer laid it out anew.
        val moved = current
        camera.showPhoto(1, 1, recognition(), viewport())
        assertThat(current.generation).isEqualTo(moved.generation)
        assertThat(sourceChange(moved, current)).isEqualTo(SourceChange.MOVED)
    }

    @Test
    fun leavingThePhotoDocksTheBubble() {
        val camera = sources.present()
        camera.showPhoto(1, 1, recognition(), viewport())
        val shown = current
        camera.showNothing()
        assertThat(sourceChange(shown, current)).isEqualTo(SourceChange.DOCK)
        camera.close()
        assertThat(sourceChange(shown, current)).isEqualTo(SourceChange.DOCK)
    }

    @Test
    fun anotherPhotoOrItsNewTextTakesTheOldOnesPlace() {
        val camera = sources.present()
        camera.showPhoto(1, 1, recognition(), viewport())
        val first = current
        camera.showPhoto(1, 2, recognition(Language.JAPANESE), viewport())
        assertThat(sourceChange(first, current)).isEqualTo(SourceChange.ATTACH)
        val recognizedAgain = current
        camera.showPhoto(2, 1, recognition(), viewport())
        assertThat(sourceChange(recognizedAgain, current)).isEqualTo(SourceChange.ATTACH)
    }

    @Test
    fun aPhotoShownUnderABubbleOutOverTheScreenIsAttached() {
        val screen = current
        sources.present().showPhoto(1, 1, recognition(), viewport())
        assertThat(sourceChange(screen, current)).isEqualTo(SourceChange.ATTACH)
    }

    @Test
    fun theScreenAndTheCameraPageWithoutTextCloseEachOthersScan() {
        val screen = current
        val camera = sources.present()
        val nothing = current
        assertThat(sourceChange(screen, nothing)).isEqualTo(SourceChange.CLOSE)
        camera.close()
        assertThat(sourceChange(nothing, current)).isEqualTo(SourceChange.CLOSE)
        // A collector that missed the Camera page sees one screen after another.
        assertThat(sourceChange(screen, current)).isEqualTo(SourceChange.CLOSE)
    }

    @Test
    fun aClosedPresentationChangesNothing() {
        val old = sources.present()
        val newer = sources.present()
        old.showPhoto(1, 1, recognition(), viewport())
        old.close()
        assertThat(current).isInstanceOf(LookupSource.NoText::class.java)
        newer.close()
        assertThat(current).isInstanceOf(LookupSource.Screen::class.java)
    }

    @Test
    fun theBubbleShowsThePhotosLanguage() {
        assertThat(glyphLanguage(current, Language.JAPANESE)).isEqualTo(Language.JAPANESE)
        val camera = sources.present()
        assertThat(glyphLanguage(current, Language.JAPANESE)).isEqualTo(Language.JAPANESE)
        camera.showPhoto(1, 1, recognition(Language.CHINESE), viewport())
        assertThat(glyphLanguage(current, Language.JAPANESE)).isEqualTo(Language.CHINESE)
        camera.close()
        assertThat(glyphLanguage(current, Language.KOREAN)).isEqualTo(Language.KOREAN)
    }
}
