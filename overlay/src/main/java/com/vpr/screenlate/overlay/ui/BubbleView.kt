package com.vpr.screenlate.overlay.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** The draggable bubble. Draws a translucent disc, an optional center dot and a loading arc. */
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
    private val arcBounds = RectF()
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
            field = value
            alpha = if (value) DOCKED_ALPHA else 1f
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
            if (value) spinner.start() else spinner.cancel()
            invalidate()
        }

    init {
        alpha = DOCKED_ALPHA
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // The docked bubble sits in the edge area that gesture navigation uses for Back.
        exclusionRect.set(0, 0, width, height)
        systemGestureExclusionRects = exclusionRects
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ring.strokeWidth
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, ring)
        if (showCenterDot) canvas.drawCircle(cx, cy, 3.5f * density, dot)
        if (loading) {
            val inset = radius - 6f * density
            arcBounds.set(cx - inset, cy - inset, cx + inset, cy + inset)
            canvas.drawArc(arcBounds, arcStart, 100f, false, arc)
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val BUBBLE_COLOR = 0x737C5CFF
        const val DOCKED_ALPHA = 0.55f
    }
}
