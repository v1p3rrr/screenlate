package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.settings.DockSide
import org.junit.Test

class BubbleWindowMoverTest {

    // Status bar 50, navigation bar 50, the gesture areas 70 from the top and 60 from the bottom.
    private val screen = DockPlacement.Screen(
        width = 1000f,
        height = 2000f,
        usable = Box(0f, 50f, 1000f, 1950f),
        gestures = Box(0f, 70f, 1000f, 1940f),
    )
    private val fullscreen = screen.copy(shownBars = emptySet())

    private fun dock(side: DockSide, position: Float = 0.5f, size: Int = SIZE, on: DockPlacement.Screen = screen): BubbleFrame {
        val window = DockPlacement.window(side, position, size, on)
        val (x, y) = DockPlacement.center(window, size, side)
        val shown = Box(window.x.toFloat(), window.y.toFloat(), (window.x + window.width).toFloat(), (window.y + window.height).toFloat())
        return BubbleFrame(window, x, y, shown)
    }

    private fun floating(x: Float, y: Float, size: Int = SIZE) = BubbleFrame(
        DockPlacement.Window(Math.round(x - size / 2f), Math.round(y - size / 2f), size, size),
        x,
        y,
        shown = null,
    )

    /** Every dock, with the bars and in a fullscreen app, and free bubbles near each edge and in the middle. */
    private val frames: List<BubbleFrame> = buildList {
        for (side in DockSide.entries) {
            for (on in listOf(screen, fullscreen)) {
                add(dock(side, 0.3f, on = on))
                add(dock(side, 0.7f, size = 140, on = on))
            }
        }
        for ((x, y) in listOf(500f to 1000f, 60f to 400f, 940f to 1500f, 500f to 120f, 500f to 1880f)) {
            add(floating(x, y))
            // Elsewhere: a frame's look is found by its center.
            add(floating(x + 7f, y + 9f, size = 140))
        }
    }

    private fun DockPlacement.Window.sameSize(other: DockPlacement.Window) = width == other.width && height == other.height
    private fun DockPlacement.Window.samePlace(other: DockPlacement.Window) = x == other.x && y == other.y

    /** The windows in [updates], one after another from [start], each change only the size or only the place. */
    private fun assertNoMoveWithResize(start: BubbleFrame, updates: List<BubbleFrame>) {
        (listOf(start) + updates).zipWithNext().forEach { (before, after) ->
            assertWithMessage("${before.window} -> ${after.window}")
                .that(before.window.sameSize(after.window) || before.window.samePlace(after.window))
                .isTrue()
        }
    }

    /**
     * What [step] shows lies in its window: the part of the disc that shows in the one of [frames] it draws, or that
     * frame's whole window for a free bubble.
     */
    private fun assertHoldsWhatShows(step: BubbleFrame, frames: List<BubbleFrame>) {
        val drawn = frames.first { it.centerX == step.centerX && it.centerY == step.centerY && it.shown == step.shown }
        val shows = drawn.shown ?: drawn.window.let {
            Box(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
        }
        val window = step.window
        val inside = shows.left >= window.x && shows.top >= window.y &&
            shows.right <= window.x + window.width && shows.bottom <= window.y + window.height
        assertWithMessage("$shows in $window").that(inside).isTrue()
    }

    @Test
    fun `no step changes the window's size and place at once`() {
        for (from in frames) {
            for (to in frames) {
                val steps = BubbleWindowMover.steps(from, to)
                assertNoMoveWithResize(from, steps)
                assertThat(steps.last()).isEqualTo(to)
                assertThat(steps.size).isAtMost(3)
            }
        }
    }

    @Test
    fun `every step's window holds what it shows`() {
        for (from in frames) {
            for (to in frames) {
                BubbleWindowMover.steps(from, to).forEach { assertHoldsWhatShows(it, listOf(from, to)) }
            }
        }
    }

    @Test
    fun `a move or a resize alone is one update`() {
        val free = floating(500f, 1000f)
        assertThat(BubbleWindowMover.steps(free, floating(300f, 700f))).containsExactly(floating(300f, 700f))
        val right = dock(DockSide.RIGHT)
        val grown = right.copy(window = right.window.copy(width = SIZE))
        assertThat(BubbleWindowMover.steps(right, grown)).containsExactly(grown)
    }

    @Test
    fun `pulling out grows the window at the dock, then moves it to the finger`() {
        for (side in DockSide.entries) {
            val docked = dock(side)
            val pulled = floating(500f, 1000f)
            val steps = BubbleWindowMover.steps(docked, pulled)

            assertThat(steps).hasSize(2)
            // Still the dock's look, in a window as large as the disc at the dock's corner.
            assertThat(steps[0]).isEqualTo(docked.copy(window = docked.window.copy(width = SIZE, height = SIZE)))
            assertThat(steps[1]).isEqualTo(pulled)
        }
    }

    @Test
    fun `dropping into the dock moves the disc there whole, then shrinks the window`() {
        for (side in DockSide.entries) {
            val dropped = floating(500f, 1000f)
            val docked = dock(side)
            val steps = BubbleWindowMover.steps(dropped, docked)

            assertThat(steps).hasSize(2)
            // The dock's look at once, in a window as large as the disc at the dock window's corner.
            assertThat(steps[0]).isEqualTo(docked.copy(window = docked.window.copy(width = SIZE, height = SIZE)))
            assertThat(steps[1]).isEqualTo(docked)
        }
    }

    @Test
    fun `a dock moving to another edge grows, moves and shrinks`() {
        val steps = BubbleWindowMover.steps(dock(DockSide.RIGHT), dock(DockSide.TOP))

        assertThat(steps.map { it.window.width to it.window.height }).containsExactly(SIZE to SIZE, SIZE to SIZE, SIZE to 40)
            .inOrder()
    }

    @Test
    fun `a gesture through the mover never moves and resizes the window in one update`() {
        val updates = mutableListOf<BubbleFrame>()
        val pending = ArrayDeque<() -> Unit>()
        val start = dock(DockSide.RIGHT)
        val targets = mutableListOf(start)
        val mover = BubbleWindowMover(start, update = { updates += it }, afterUpdate = { pending.addLast(it) })
        fun laidOut() = pending.removeFirst()()
        fun moveTo(frame: BubbleFrame) {
            targets += frame
            mover.moveTo(frame)
        }

        // Pulled out: the window grows at the dock; the finger moves on before the window manager has laid it out.
        moveTo(floating(930f, 1000f))
        moveTo(floating(900f, 1000f))
        moveTo(floating(860f, 1000f))
        assertThat(updates).hasSize(1)
        assertThat(mover.target).isEqualTo(floating(860f, 1000f))
        laidOut()
        // Then each move is one update at once.
        moveTo(floating(800f, 990f))
        moveTo(floating(700f, 980f))
        assertThat(pending).isEmpty()
        // Dropped at the left edge, then moved along it while the dock is laid out.
        moveTo(dock(DockSide.LEFT, 0.49f))
        moveTo(dock(DockSide.LEFT, 0.48f))
        while (pending.isNotEmpty()) laidOut()

        assertNoMoveWithResize(start, updates)
        updates.forEach { assertHoldsWhatShows(it, targets) }
        assertThat(mover.current).isEqualTo(dock(DockSide.LEFT, 0.48f))
        assertThat(updates.last()).isEqualTo(dock(DockSide.LEFT, 0.48f))
        assertThat(updates.map { it.window }).containsExactly(
            start.window.copy(width = SIZE),
            floating(860f, 1000f).window,
            floating(800f, 990f).window,
            floating(700f, 980f).window,
            dock(DockSide.LEFT, 0.49f).window.copy(width = SIZE),
            dock(DockSide.LEFT, 0.48f).window.copy(width = SIZE),
            dock(DockSide.LEFT, 0.48f).window,
        ).inOrder()
    }

    private companion object {
        const val SIZE = 100
    }
}
