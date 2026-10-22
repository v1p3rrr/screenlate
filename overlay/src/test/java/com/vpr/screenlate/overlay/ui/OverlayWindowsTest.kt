package com.vpr.screenlate.overlay.ui

import android.view.View
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import org.junit.Assert.assertThrows
import org.junit.Test

class OverlayWindowsTest {

    /** Stands in for the window manager: the windows on screen and the service's window token. */
    private class Screen {
        val windows = mutableListOf<View>()

        /** The system takes the token when it unbinds the service, before the service hears of it. */
        var tokenTaken = false

        /** Thrown by the next add instead of the token check. */
        var failure: RuntimeException? = null

        val windowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java),
        ) { _, method, args ->
            val view = args?.firstOrNull() as? View
            when (method.name) {
                "addView" -> {
                    failure?.let { throw it }
                    if (tokenTaken) throw WindowManager.BadTokenException("Unable to add window -- token is not valid")
                    windows += view!!
                }
                "removeView" -> windows -= view!!
            }
            null
        } as WindowManager

        fun entry(view: View, beforeAdd: () -> Unit = {}) = OverlayWindows.Entry(
            add = {
                beforeAdd()
                OverlayWindows.add(windowManager, view, WindowManager.LayoutParams())
            },
            remove = { windowManager.removeView(view) },
        )
    }

    private val screen = Screen()
    private val layer = View(null)
    private val popup = View(null)
    private val bubble = View(null)

    @Test
    fun `adds every window, the bottom one first`() {
        val added = OverlayWindows.addAll(screen.entry(layer), screen.entry(popup), screen.entry(bubble))

        assertThat(added).isTrue()
        assertThat(screen.windows).containsExactly(layer, popup, bubble).inOrder()
    }

    @Test
    fun `adds nothing once the system has taken the token`() {
        screen.tokenTaken = true

        val added = OverlayWindows.addAll(screen.entry(layer), screen.entry(popup), screen.entry(bubble))

        assertThat(added).isFalse()
        assertThat(screen.windows).isEmpty()
    }

    @Test
    fun `removes the windows added before the token was taken`() {
        val added = OverlayWindows.addAll(
            screen.entry(layer),
            screen.entry(popup),
            screen.entry(bubble, beforeAdd = { screen.tokenTaken = true }),
        )

        assertThat(added).isFalse()
        assertThat(screen.windows).isEmpty()
    }

    @Test
    fun `removes the windows added before, the newest first`() {
        val removed = mutableListOf<String>()
        val added = OverlayWindows.addAll(
            OverlayWindows.Entry({ true }, { removed += "layer" }),
            OverlayWindows.Entry({ true }, { removed += "popup" }),
            OverlayWindows.Entry({ false }, { removed += "bubble" }),
        )

        assertThat(added).isFalse()
        assertThat(removed).containsExactly("popup", "layer").inOrder()
    }

    @Test
    fun `passes on other failures`() {
        screen.failure = IllegalStateException("View has already been added to the window manager")

        assertThrows(IllegalStateException::class.java) {
            OverlayWindows.add(screen.windowManager, layer, WindowManager.LayoutParams())
        }
    }
}
