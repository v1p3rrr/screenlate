package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.settings.DockSide
import org.junit.Test

class DockPlacementTest {

    // Status bar 50, navigation bar 50, the gesture areas 70 from the top and 60 from the bottom.
    private val screen = DockPlacement.Screen(
        width = 1000f,
        height = 2000f,
        usable = Box(0f, 50f, 1000f, 1950f),
        gestures = Box(0f, 70f, 1000f, 1940f),
    )

    @Test
    fun `the top dock hangs under the status bar and the gesture area`() {
        assertThat(DockPlacement.topLine(screen)).isEqualTo(70f)
        assertThat(DockPlacement.topLine(screen.copy(usable = Box(0f, 90f, 1000f, 1950f)))).isEqualTo(90f)
    }

    @Test
    fun `the bottom dock stands above the bar and the gesture area`() {
        assertThat(DockPlacement.bottomLine(screen)).isEqualTo(1940f)
        assertThat(DockPlacement.bottomLine(screen.copy(usable = Box(0f, 50f, 1000f, 1900f)))).isEqualTo(1900f)
    }

    @Test
    fun `an open keyboard lifts the bottom dock`() {
        assertThat(DockPlacement.bottomLine(screen.copy(keyboardTop = 1200f))).isEqualTo(1200f)
        assertThat(DockPlacement.topLine(screen.copy(keyboardTop = 1200f))).isEqualTo(70f)
    }

    @Test
    fun `a top or bottom dock's window is only the part that shows`() {
        assertThat(DockPlacement.window(DockSide.TOP, 0.5f, 100, screen)).isEqualTo(DockPlacement.Window(450, 70, 100, 40))
        assertThat(DockPlacement.window(DockSide.BOTTOM, 0.5f, 100, screen)).isEqualTo(DockPlacement.Window(450, 1900, 100, 40))
    }

    @Test
    fun `the disc's center comes back from a dock's window`() {
        for (side in DockSide.entries) {
            for (bars in listOf(DockSide.entries.toSet(), emptySet())) {
                val shown = screen.copy(shownBars = bars)
                val window = DockPlacement.window(side, 0.3f, 100, shown)
                val (x, y) = DockPlacement.center(window, 100, side)
                assertThat(DockPlacement.position(side, x, y, shown)).isWithin(0.001f).of(0.3f)
            }
        }
        // A left dock's window holds the disc's right 40 px at the edge.
        assertThat(DockPlacement.center(DockPlacement.Window(0, 850, 40, 100), 100, DockSide.LEFT)).isEqualTo(-10f to 900f)
        // The top window holds the disc's lower 40 px under the line at 70, the bottom one its upper 40 px.
        assertThat(DockPlacement.center(DockPlacement.Window(450, 70, 100, 40), 100, DockSide.TOP)).isEqualTo(500f to 60f)
        assertThat(DockPlacement.center(DockPlacement.Window(450, 1900, 100, 40), 100, DockSide.BOTTOM)).isEqualTo(500f to 1950f)
        assertThat(DockPlacement.center(DockPlacement.Window(200, 300, 100, 100), 100, DockSide.TOP)).isEqualTo(250f to 350f)
    }

    @Test
    fun `a dock keeps a bubble's size away from the corners`() {
        assertThat(DockPlacement.window(DockSide.TOP, 0f, 100, screen).x).isEqualTo(50)
        assertThat(DockPlacement.window(DockSide.BOTTOM, 1f, 100, screen).x).isEqualTo(850)
        assertThat(DockPlacement.window(DockSide.RIGHT, 1f, 100, screen).y).isEqualTo(1800)
    }

    @Test
    fun `a side dock's window is the part at the screen edge`() {
        assertThat(DockPlacement.window(DockSide.RIGHT, 0.45f, 100, screen)).isEqualTo(DockPlacement.Window(960, 850, 40, 100))
        assertThat(DockPlacement.window(DockSide.LEFT, 0.45f, 100, screen)).isEqualTo(DockPlacement.Window(0, 850, 40, 100))
    }

    @Test
    fun `a navigation bar at the side keeps the dock off it`() {
        // Three-button navigation in landscape: the bar is at the right.
        val landscape = screen.copy(usable = Box(0f, 50f, 900f, 2000f), gestures = Box(0f, 70f, 900f, 2000f))
        assertThat(DockPlacement.window(DockSide.RIGHT, 0.45f, 100, landscape).x).isEqualTo(860)
        assertThat(DockPlacement.window(DockSide.BOTTOM, 0.5f, 100, landscape).y).isEqualTo(1960)
    }

    @Test
    fun `in fullscreen the bubble goes to the edge and stands a third past the gesture strip`() {
        val fullscreen = screen.copy(shownBars = emptySet())
        // The bottom strip is 60: 60 + 34 of the 100 px disc show from the very edge.
        assertThat(DockPlacement.window(DockSide.BOTTOM, 0.5f, 100, fullscreen)).isEqualTo(DockPlacement.Window(450, 1906, 100, 94))
        // The top strip of 70 leaves no third of the disc: the line, as with the bars shown.
        assertThat(DockPlacement.window(DockSide.TOP, 0.5f, 100, fullscreen)).isEqualTo(DockPlacement.Window(450, 70, 100, 40))
        // A narrow strip: at least 60% shows.
        val narrow = fullscreen.copy(gestures = Box(0f, 10f, 1000f, 1990f))
        assertThat(DockPlacement.window(DockSide.TOP, 0.5f, 100, narrow)).isEqualTo(DockPlacement.Window(450, 0, 100, 60))
        // No strip at the sides: the very edge, 40%.
        assertThat(DockPlacement.window(DockSide.LEFT, 0.45f, 100, fullscreen)).isEqualTo(DockPlacement.Window(0, 850, 40, 100))
    }

    @Test
    fun `a keyboard keeps the bottom dock above it in fullscreen too`() {
        val typing = screen.copy(shownBars = emptySet(), keyboardTop = 1200f)
        assertThat(DockPlacement.window(DockSide.BOTTOM, 0.5f, 100, typing)).isEqualTo(DockPlacement.Window(450, 1160, 100, 40))
    }

    @Test
    fun `the place along the edge is a fraction of the screen`() {
        assertThat(DockPlacement.position(DockSide.TOP, 250f, 30f, screen)).isEqualTo(0.25f)
        assertThat(DockPlacement.position(DockSide.LEFT, 10f, 500f, screen)).isEqualTo(0.25f)
    }

    private fun edge(fingerX: Float, fingerY: Float, centerX: Float = fingerX, centerY: Float = fingerY, topBottom: Boolean = true) =
        DockPlacement.edgeAt(fingerX, fingerY, centerX, centerY, 1000f, 2000f, zone = 36f, topBottom = topBottom)

    @Test
    fun `a bubble dropped at an edge docks there`() {
        assertThat(edge(995f, 1000f)).isEqualTo(DockSide.RIGHT)
        assertThat(edge(5f, 1000f)).isEqualTo(DockSide.LEFT)
        assertThat(edge(500f, 5f)).isEqualTo(DockSide.TOP)
        assertThat(edge(500f, 1990f)).isEqualTo(DockSide.BOTTOM)
        assertThat(edge(500f, 1000f)).isNull()
    }

    @Test
    fun `the center past an edge docks too`() {
        assertThat(edge(500f, 1950f, centerX = 500f, centerY = 2010f)).isEqualTo(DockSide.BOTTOM)
        assertThat(edge(900f, 1000f, centerX = 1001f, centerY = 1000f)).isEqualTo(DockSide.RIGHT)
    }

    @Test
    fun `top and bottom only when allowed`() {
        assertThat(edge(500f, 5f, topBottom = false)).isNull()
        assertThat(edge(500f, 1950f, centerX = 500f, centerY = 2010f, topBottom = false)).isNull()
    }

    @Test
    fun `at a corner the edge nearer to the finger wins`() {
        assertThat(edge(995f, 3f)).isEqualTo(DockSide.TOP)
        assertThat(edge(998f, 10f)).isEqualTo(DockSide.RIGHT)
        assertThat(edge(998f, 10f, topBottom = false)).isEqualTo(DockSide.RIGHT)
    }
}
