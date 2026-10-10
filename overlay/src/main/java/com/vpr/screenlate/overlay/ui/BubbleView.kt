package com.vpr.screenlate.overlay.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import com.vpr.screenlate.overlay.settings.DockSide
import java.util.Locale

/**
 * The draggable bubble. Draws a translucent disc, an optional center dot, a loading arc and, while docked, a letter of
 * the lookup language in the part that shows past the screen edge.
 *
 * The disc goes where [place] puts it; a dock's window holds only the part that shows, and a window on its way between
 * the dock and the finger may be larger than the disc.
 */
class BubbleView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BUBBLE_COLOR }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val arcBounds = RectF()
    private val discBounds = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var shown: RectF? = null
    private val exclusionRect = Rect()
    private val exclusionRects = listOf(exclusionRect)
    private var arcStart = 0f
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            arcStart = it.animatedValue as Float
            invalidate()
        }
    }

    var docked: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            alpha = restingAlpha()
            invalidate()
        }

    /** Solid black and a still loading arc: e-ink screens show translucency poorly and refresh on every frame. */
    var eInk: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            fill.color = if (value) Color.BLACK else BUBBLE_COLOR
            alpha = restingAlpha()
            if (loading) {
                if (value) spinner.cancel() else spinner.start()
            }
            invalidate()
        }

    /** The alpha when the bubble is shown (it is 0 while a screenshot is taken on older Android versions). */
    fun restingAlpha(): Float = if (docked && !eInk) DOCKED_ALPHA else 1f

    /** The edge the docked bubble sits at; the glyph goes into the part that stays on screen. */
    var dockSide: DockSide = DockSide.RIGHT
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** A characteristic letter of the lookup language (あ for Japanese); empty for none. */
    var glyph: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Language of [glyph], for region-specific glyph forms. */
    var glyphLanguage: String = ""
        set(value) {
            field = value
            glyphPaint.textLocale = if (value.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(value)
            invalidate()
        }

    var showCenterDot: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var loading: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value && !eInk) spinner.start() else spinner.cancel()
            invalidate()
        }

    /** The disc's size in pixels. */
    var diameter: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        alpha = DOCKED_ALPHA
    }

    /** Puts the disc's center at ([x], [y]) in the view; only [shown] of it is drawn, or the whole disc when null. */
    fun place(x: Float, y: Float, shown: RectF?) {
        if (x == centerX && y == centerY && shown == this.shown) return
        centerX = x
        centerY = y
        this.shown = shown
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // The docked bubble sits in the edge area that gesture navigation uses for Back.
        exclusionRect.set(0, 0, width, height)
        systemGestureExclusionRects = exclusionRects
    }

    override fun onDraw(canvas: Canvas) {
        val cx = centerX
        val cy = centerY
        val half = diameter / 2f
        discBounds.set(cx - half, cy - half, cx + half, cy + half)
        val part = shown ?: discBounds
        canvas.save()
        canvas.clipRect(part)
        val radius = half - ring.strokeWidth
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, ring)
        if (showCenterDot) canvas.drawCircle(cx, cy, 3.5f * density, dot)
        if (docked && glyph.isNotEmpty()) drawGlyph(canvas, part)
        if (loading) {
            val inset = radius - 6f * density
            arcBounds.set(cx - inset, cy - inset, cx + inset, cy + inset)
            if (eInk) {
                canvas.drawArc(arcBounds, -90f, E_INK_ARC, false, arc)
            } else {
                canvas.drawArc(arcBounds, arcStart, 100f, false, arc)
            }
        }
        canvas.restore()
    }

    /** [part] is the part of the disc that shows. */
    private fun drawGlyph(canvas: Canvas, part: RectF) {
        val x = part.centerX()
        val y: Float
        if (dockSide.horizontal) {
            // The part that shows is a disc segment narrowing away from the screen edge: the glyph sits nearer to the
            // edge, where the segment is wide, or at the disc's center when that shows.
            val fromEdge = maxOf(part.height() * CAP_GLYPH_CENTER, part.height() - diameter / 2f)
            y = if (dockSide == DockSide.TOP) part.top + fromEdge else part.bottom - fromEdge
            glyphPaint.textSize = minOf(part.height() * CAP_GLYPH_SIZE, diameter * CAP_GLYPH_MAX)
        } else {
            y = centerY
            glyphPaint.textSize = minOf(part.width(), part.height()) * GLYPH_SIZE
        }
        val baseline = y - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText(glyph, x, baseline, glyphPaint)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val BUBBLE_COLOR = 0x737C5CFF
        private const val DOCKED_ALPHA = 0.55f

        /** The still arc of the e-ink loading indicator, in degrees. */
        private const val E_INK_ARC = 270f

        /** Glyph size relative to the visible part of the docked bubble. */
        private const val GLYPH_SIZE = 0.8f

        /** At the top and bottom: the glyph's size and center relative to the cap's height, from the screen edge. */
        private const val CAP_GLYPH_SIZE = 0.68f
        private const val CAP_GLYPH_CENTER = 0.42f

        /** The glyph's largest size relative to the disc, when much of it shows. */
        private const val CAP_GLYPH_MAX = 0.45f
    }
}
