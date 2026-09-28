package com.vpr.screenlate.overlay.web

import android.content.Context
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu

/**
 * The page's container. In an overlay window nothing can show the system's text selection toolbar, so a WebView's
 * request for one gets no action mode, and Chromium then drops the selection at once. Here such a request gets a
 * [SilentActionMode] instead: the selection stays, and the page offers its own Copy button. Inside an activity the
 * system's toolbar is used as usual.
 *
 * Chromium shows selection handles only while its window has focus, which an overlay window normally never takes:
 * [onSelecting] lets the host make the window focusable for as long as the selection lasts. Back or focus moving to
 * another window ends the selection.
 */
internal class SelectionHost(context: Context) : FrameLayout(context) {

    /** Called with true when a selection starts without a system toolbar, and with false when it ends. */
    var onSelecting: (Boolean) -> Unit = {}

    private var mode: SilentActionMode? = null
    private var focused = false

    override fun startActionModeForChild(originalView: View, callback: ActionMode.Callback, type: Int): ActionMode? {
        super.startActionModeForChild(originalView, callback, type)?.let { return it }
        if (type != ActionMode.TYPE_FLOATING) return null
        mode?.finish()
        val started = SilentActionMode(context, callback) { ended ->
            if (mode === ended) {
                mode = null
                focused = false
                onSelecting(false)
            }
        }
        if (!started.start()) return null
        mode = started
        onSelecting(true)
        return started
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val active = mode
        if (active != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) active.finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // The window only has focus while selecting; losing it means a touch went to another window.
        if (hasWindowFocus) focused = true else if (focused) mode?.finish()
    }
}

/** An action mode without a toolbar; see [SelectionHost]. */
private class SilentActionMode(
    private val context: Context,
    private val callback: ActionMode.Callback,
    private val onFinished: (SilentActionMode) -> Unit,
) : ActionMode() {
    private val menu: Menu = PopupMenu(context, View(context)).menu
    private var finished = false

    init {
        type = TYPE_FLOATING
    }

    fun start(): Boolean {
        if (!callback.onCreateActionMode(this, menu)) return false
        callback.onPrepareActionMode(this, menu)
        return true
    }

    override fun setTitle(title: CharSequence?) = Unit

    override fun setTitle(resId: Int) = Unit

    override fun setSubtitle(subtitle: CharSequence?) = Unit

    override fun setSubtitle(resId: Int) = Unit

    override fun setCustomView(view: View?) = Unit

    override fun invalidate() {
        callback.onPrepareActionMode(this, menu)
    }

    override fun finish() {
        if (finished) return
        finished = true
        callback.onDestroyActionMode(this)
        onFinished(this)
    }

    override fun getMenu(): Menu = menu

    override fun getTitle(): CharSequence? = null

    override fun getSubtitle(): CharSequence? = null

    override fun getCustomView(): View? = null

    override fun getMenuInflater(): MenuInflater = MenuInflater(context)
}
