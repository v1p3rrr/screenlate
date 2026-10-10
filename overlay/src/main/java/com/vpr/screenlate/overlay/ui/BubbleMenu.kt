package com.vpr.screenlate.overlay.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.vpr.screenlate.core.common.geometry.Box
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The menu of the held bubble, in its own overlay window. The system popup menu cannot place itself here: overlay
 * windows are laid out without screen limits, so it believes there is room on every side and runs off the screen.
 * While open, the window takes focus and every touch on the screen, like a menu inside an app: Back or a tap anywhere
 * outside closes it, and that tap does nothing else. Closes on an item and when the screen turns off too. A row of chips
 * above the items picks the active language while several are turned on.
 */
class BubbleMenu(private val context: Context, private val windowManager: WindowManager) {

    class Item(val title: String, val onClick: () -> Unit)

    class Chip(val title: String, val selected: Boolean, val onClick: () -> Unit)

    /** Made again for each menu: a theme keeps the day or night look it was made with. */
    private var themed: Context = context
    private val density = context.resources.displayMetrics.density
    private var window: View? = null

    /**
     * Overlays stay above the lock screen, where the menu would take the first touch of the unlock. The broadcast
     * also comes when an always-on display takes over, which a view sees as the screen staying on.
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = dismiss()
    }

    /**
     * Shows [items] beside [anchor] (the bubble) where [bounds] (the usable screen) has room for them, with [chips] in a
     * row above them; the row scrolls when it is wider than the menu.
     */
    fun show(items: List<Item>, anchor: Box, bounds: Box, chips: List<Chip> = emptyList()) {
        dismiss()
        themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        val margin = MARGIN_DP * density
        val area = Box(bounds.left + margin, bounds.top + margin, bounds.right - margin, bounds.bottom - margin)
        val card = card(items, chips)
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
        ContextCompat.registerReceiver(
            context,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Back reaches a window as a key press only for apps without predictive back.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            root.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { dismiss() },
            )
        }
    }

    fun dismiss() {
        val view = window ?: return
        window = null
        context.unregisterReceiver(screenOff)
        runCatching { windowManager.removeView(view) }
    }

    private fun card(items: List<Item>, chips: List<Chip>) = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        background = popupBackground()
        elevation = ELEVATION_DP * density
        clipToOutline = true
        minimumWidth = (MIN_WIDTH_DP * density).roundToInt()
        val padding = (LIST_PADDING_DP * density).roundToInt()
        setPadding(0, padding, 0, padding)
        if (chips.isNotEmpty()) addView(chipRow(chips))
        items.forEach { addView(itemView(it)) }
    }

    private fun chipRow(chips: List<Chip>) = HorizontalScrollView(themed).apply {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            val horizontal = (CHIP_ROW_PADDING_DP * density).roundToInt()
            setPaddingRelative(horizontal, 0, horizontal, (CHIP_ROW_PADDING_DP * density).roundToInt())
            chips.forEach { addView(chipView(it)) }
        }
        addView(row)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun chipView(chip: Chip) = TextView(themed).apply {
        text = chip.title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SP)
        isSingleLine = true
        isSelected = chip.selected
        gravity = Gravity.CENTER
        minHeight = (CHIP_HEIGHT_DP * density).roundToInt()
        val horizontal = (CHIP_PADDING_DP * density).roundToInt()
        setPaddingRelative(horizontal, 0, horizontal, 0)
        setTextColor(color(android.R.attr.textColorPrimary))
        val accent = color(android.R.attr.colorAccent)
        val outline = color(android.R.attr.textColorSecondary)
        background = GradientDrawable().apply {
            cornerRadius = CHIP_CORNER_DP * density
            if (chip.selected) {
                setColor(ColorUtils.setAlphaComponent(accent, SELECTED_ALPHA))
                setStroke(density.roundToInt(), accent)
            } else {
                setStroke(density.roundToInt(), ColorUtils.setAlphaComponent(outline, OUTLINE_ALPHA))
            }
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (CHIP_GAP_DP * density).roundToInt()
        }
        setOnClickListener {
            dismiss()
            chip.onClick()
        }
    }

    private fun root(card: View, shadow: Int) = Root(themed).apply {
        setPadding(shadow, shadow, shadow, shadow)
        // The card's shadow is drawn in the padding.
        clipToPadding = false
        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Gets every touch outside the card, on the shadow margin and beyond the window, which closes the menu when the
     * finger lifts. A system gesture (Back or Home swiped from the edge) cancels the touch instead and is not taken
     * for a tap; Back then closes the menu itself.
     */
    private inner class Root(context: Context) : FrameLayout(context) {
        private var outside = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> dismiss()
                MotionEvent.ACTION_DOWN -> outside = getChildAt(0)?.let { card ->
                    event.x < card.left || event.x >= card.right || event.y < card.top || event.y >= card.bottom
                } ?: true
                MotionEvent.ACTION_UP -> if (outside) dismiss()
                MotionEvent.ACTION_CANCEL -> outside = false
            }
            return true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dismiss()
            return true
        }
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

    /** A theme color, also when the theme gives it as a color state list. */
    private fun color(id: Int): Int =
        attr(id).let { value -> if (value.resourceId != 0) themed.getColorStateList(value.resourceId).defaultColor else value.data }

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
        const val CHIP_ROW_PADDING_DP = 12f
        const val CHIP_HEIGHT_DP = 32f
        const val CHIP_PADDING_DP = 12f
        const val CHIP_GAP_DP = 8f
        const val CHIP_CORNER_DP = 8f
        const val CHIP_TEXT_SP = 14f
        const val SELECTED_ALPHA = 0x3D
        const val OUTLINE_ALPHA = 0x80
    }
}

/** Where the bubble menu goes, in screen coordinates. */
internal object MenuPlacement {

    /**
     * Top-left corner of a [width] × [height] menu next to [anchor] inside [bounds]: above the anchor when it fits
     * there, so the finger holding the bubble does not cover it, otherwise below; when neither fits, on the side with
     * more room. Horizontally it grows from the anchor toward the middle of the screen, so a bubble near the right edge
     * gets a menu that ends at its right side. The menu never leaves [bounds] when it fits in them.
     */
    fun position(width: Float, height: Float, anchor: Box, bounds: Box, gap: Float): Pair<Float, Float> {
        val x = if (anchor.centerX > bounds.centerX) anchor.right - width else anchor.left
        val below = anchor.bottom + gap
        val above = anchor.top - gap - height
        val y = when {
            above >= bounds.top -> above
            below + height <= bounds.bottom -> below
            anchor.top - bounds.top >= bounds.bottom - anchor.bottom -> above
            else -> below
        }
        return x.coerceIn(bounds.left, max(bounds.left, bounds.right - width)) to
            y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - height))
    }
}
