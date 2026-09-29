package com.vpr.screenlate.overlay.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.vpr.screenlate.core.common.geometry.Box

/**
 * Full-screen, non-touchable layer drawn in screen coordinates: the aim dot, the highlight of the word under the aim
 * and the temporary highlight of everything that was recognized.
 *
 * While there is nothing to draw (the bubble is docked) the view is GONE, which takes its window off the screen: the
 * system then neither composes an empty full-screen layer into every frame of every app nor keeps its buffers.
 */
class LayerView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val corner = 4f * density

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AIM_COLOR }
    private val aimOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WORD_COLOR }
    private val allLinesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ALL_LINES_COLOR }
    private val rect = RectF()
    private val endFlash = Runnable {
        allLineBoxes = emptyList()
        contentChanged()
    }

    /**
     * Black frames instead of translucent fills, and the flash of all lines ends at once instead of fading: e-ink screens
     * show few shades and refresh on every frame of a fade.
     */
    var eInk: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            aimPaint.color = if (value) Color.BLACK else AIM_COLOR
            wordPaint.set(if (value) frame(E_INK_WORD_STROKE_DP) else fill(WORD_COLOR))
            allLinesPaint.set(if (value) frame(E_INK_LINES_STROKE_DP) else fill(ALL_LINES_COLOR))
            invalidate()
        }

    private var aim: Pair<Float, Float>? = null
    private var wordBoxes: List<Box> = emptyList()
    private var allLineBoxes: List<Box> = emptyList()
    private var allLinesAlpha = 0f
    private var fade: ValueAnimator? = null
    private val hide = Runnable { visibility = GONE }

    init {
        visibility = GONE
    }

    fun setAim(x: Float, y: Float) {
        aim = x to y
        contentChanged()
    }

    fun clearAim() {
        aim = null
        contentChanged()
    }

    fun setWordBoxes(boxes: List<Box>) {
        wordBoxes = boxes
        contentChanged()
    }

    /** Shows [boxes] and fades them out after [holdMillis]. */
    fun flashLines(boxes: List<Box>, holdMillis: Long) {
        fade?.cancel()
        removeCallbacks(endFlash)
        allLineBoxes = boxes
        allLinesAlpha = 1f
        if (eInk) {
            postDelayed(endFlash, holdMillis)
            contentChanged()
            return
        }
        fade = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = holdMillis
            duration = FADE_MILLIS
            addUpdateListener {
                allLinesAlpha = it.animatedValue as Float
                contentChanged()
            }
            start()
        }
        contentChanged()
    }

    fun clearAll() {
        fade?.cancel()
        removeCallbacks(endFlash)
        aim = null
        wordBoxes = emptyList()
        allLineBoxes = emptyList()
        contentChanged()
    }

    /** Shows the window at once; hides it a moment after it empties, so a gap while dragging keeps its surface. */
    private fun contentChanged() {
        val empty = aim == null && wordBoxes.isEmpty() && (allLineBoxes.isEmpty() || allLinesAlpha <= 0f)
        removeCallbacks(hide)
        if (empty) postDelayed(hide, HIDE_DELAY_MS) else visibility = VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (allLinesAlpha > 0f) {
            allLinesPaint.alpha = if (eInk) 255 else (ALL_LINES_ALPHA * allLinesAlpha).toInt()
            allLineBoxes.forEach { drawBox(canvas, it, allLinesPaint) }
        }
        wordBoxes.forEach { drawBox(canvas, it, wordPaint) }
        aim?.let { (x, y) ->
            canvas.drawCircle(x, y, AIM_RADIUS_DP * density, aimPaint)
            canvas.drawCircle(x, y, AIM_RADIUS_DP * density, aimOutline)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        removeCallbacks(endFlash)
        fade?.cancel()
        super.onDetachedFromWindow()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun frame(strokeDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeDp * density
        color = Color.BLACK
    }

    private fun drawBox(canvas: Canvas, box: Box, paint: Paint) {
        val pad = 2f * density
        rect.set(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
        canvas.drawRoundRect(rect, corner, corner, paint)
    }

    private companion object {
        const val AIM_COLOR = 0xFF7C5CFF.toInt()
        const val WORD_COLOR = 0x557C5CFF
        const val ALL_LINES_COLOR = 0xFFFFC83D.toInt()
        const val ALL_LINES_ALPHA = 90
        const val AIM_RADIUS_DP = 5f
        const val FADE_MILLIS = 400L
        const val HIDE_DELAY_MS = 1000L
        const val E_INK_WORD_STROKE_DP = 2.5f
        const val E_INK_LINES_STROKE_DP = 1f
    }
}
