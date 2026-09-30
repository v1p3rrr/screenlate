package com.vpr.screenlate.overlay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The menu of the held bubble, in its own overlay window. The system popup menu cannot place itself here: overlay
 * windows are laid out without screen limits, so it believes there is room on every side and runs off the screen.
 * Closes on a tap outside it or on an item.
 */
class BubbleMenu(private val context: Context, private val windowManager: WindowManager) {

    class Item(val title: String, val onClick: () -> Unit)

    /** Made again for each menu: a theme keeps the day or night look it was made with. */
    private var themed: Context = context
    private val density = context.resources.displayMetrics.density
    private var window: View? = null

    /** Shows [items] beside [anchor] (the bubble) where [bounds] (the usable screen) has room for them. */
    fun show(items: List<Item>, anchor: Box, bounds: Box) {
        dismiss()
        themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        val margin = MARGIN_DP * density
        val area = Box(bounds.left + margin, bounds.top + margin, bounds.right - margin, bounds.bottom - margin)
        val card = card(items)
        val maxWidth = min(area.width, MAX_WIDTH_DP * density).roundToInt().coerceAtLeast(1)
        card.measure(
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(area.height.roundToInt().coerceAtLeast(1), MeasureSpec.AT_MOST),
        )
        val (x, y) = MenuPlacement.position(
            card.measuredWidth.toFloat(),
            card.measuredHeight.toFloat(),
            anchor,
            area,
            GAP_DP * density,
        )
        // Room around the card for its shadow.
        val shadow = (SHADOW_DP * density).roundToInt()
        val root = root(card, shadow)
        val params = OverlayWindows.menuParams().apply {
            this.x = x.roundToInt() - shadow
            this.y = y.roundToInt() - shadow
            width = card.measuredWidth + 2 * shadow
            height = card.measuredHeight + 2 * shadow
        }
        windowManager.addView(root, params)
        window = root
    }

    fun dismiss() {
        val view = window ?: return
        window = null
        runCatching { windowManager.removeView(view) }
    }

    private fun card(items: List<Item>) = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        background = popupBackground()
        elevation = ELEVATION_DP * density
        clipToOutline = true
        minimumWidth = (MIN_WIDTH_DP * density).roundToInt()
        val padding = (LIST_PADDING_DP * density).roundToInt()
        setPadding(0, padding, 0, padding)
        items.forEach { addView(itemView(it)) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun root(card: View, shadow: Int) = FrameLayout(themed).apply {
        setPadding(shadow, shadow, shadow, shadow)
        // The card's shadow is drawn in the padding.
        clipToPadding = false
        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) dismiss()
            false
        }
        // A tap on the shadow margin counts as outside.
        setOnClickListener { dismiss() }
    }

    private fun itemView(item: Item) = TextView(themed).apply {
        text = item.title
        attr(android.R.attr.textAppearanceLargePopupMenu).resourceId.takeIf { it != 0 }?.let(::setTextAppearance)
        minHeight = attr(android.R.attr.listPreferredItemHeightSmall).getDimension(resources.displayMetrics).roundToInt()
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = (ITEM_PADDING_DP * density).roundToInt()
        val vertical = (ITEM_VERTICAL_PADDING_DP * density).roundToInt()
        setPaddingRelative(horizontal, vertical, horizontal, vertical)
        attr(android.R.attr.selectableItemBackground).resourceId.takeIf { it != 0 }?.let { background = themed.getDrawable(it) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener {
            dismiss()
            item.onClick()
        }
    }

    /** The system popup menu's background, so the menu looks like one; a plain rounded card if the theme has none. */
    private fun popupBackground(): Drawable {
        val styled = themed.obtainStyledAttributes(null, intArrayOf(android.R.attr.popupBackground), android.R.attr.popupMenuStyle, 0)
        val drawable = styled.getDrawable(0)
        styled.recycle()
        return drawable ?: GradientDrawable().apply {
            setColor(attr(android.R.attr.colorBackgroundFloating).data)
            cornerRadius = CORNER_DP * density
        }
    }

    private fun attr(id: Int) = TypedValue().also { themed.theme.resolveAttribute(id, it, true) }

    private companion object {
        const val MARGIN_DP = 8f
        const val GAP_DP = 4f
        const val SHADOW_DP = 8f
        const val ELEVATION_DP = 6f
        const val MIN_WIDTH_DP = 112f
        const val MAX_WIDTH_DP = 320f
        const val LIST_PADDING_DP = 8f
        const val ITEM_PADDING_DP = 16f
        const val ITEM_VERTICAL_PADDING_DP = 8f
        const val CORNER_DP = 8f
    }
}

/** Where the bubble menu goes, in screen coordinates. */
internal object MenuPlacement {

    /**
     * Top-left corner of a [width] × [height] menu next to [anchor] inside [bounds]: below the anchor when it fits
     * there, otherwise above; when neither fits, on the side with more room. Horizontally it grows from the anchor
     * toward the middle of the screen, so a bubble near the right edge gets a menu that ends at its right side.
     */
    fun position(width: Float, height: Float, anchor: Box, bounds: Box, gap: Float): Pair<Float, Float> {
        val x = if (anchor.centerX > bounds.centerX) anchor.right - width else anchor.left
        val below = anchor.bottom + gap
        val above = anchor.top - gap - height
        val y = when {
            below + height <= bounds.bottom -> below
            above >= bounds.top -> above
            bounds.bottom - anchor.bottom >= anchor.top - bounds.top -> below
            else -> above
        }
        return x.coerceIn(bounds.left, max(bounds.left, bounds.right - width)) to
            y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - height))
    }
}
