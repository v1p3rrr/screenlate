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

    private var aim: Pair<Float, Float>? = null
    private var wordBoxes: List<Box> = emptyList()
    private var allLineBoxes: List<Box> = emptyList()
    private var allLinesAlpha = 0f
    private var fade: ValueAnimator? = null

    fun setAim(x: Float, y: Float) {
        aim = x to y
        invalidate()
    }

    fun clearAim() {
        aim = null
        invalidate()
    }

    fun setWordBoxes(boxes: List<Box>) {
        wordBoxes = boxes
        invalidate()
    }

    /** Shows [boxes] and fades them out after [holdMillis]. */
    fun flashLines(boxes: List<Box>, holdMillis: Long) {
        fade?.cancel()
        allLineBoxes = boxes
        allLinesAlpha = 1f
        fade = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = holdMillis
            duration = FADE_MILLIS
            addUpdateListener {
                allLinesAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    fun clearAll() {
        fade?.cancel()
        aim = null
        wordBoxes = emptyList()
        allLineBoxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (allLinesAlpha > 0f) {
            allLinesPaint.alpha = (ALL_LINES_ALPHA * allLinesAlpha).toInt()
            allLineBoxes.forEach { drawBox(canvas, it, allLinesPaint) }
        }
        wordBoxes.forEach { drawBox(canvas, it, wordPaint) }
        aim?.let { (x, y) ->
            canvas.drawCircle(x, y, AIM_RADIUS_DP * density, aimPaint)
            canvas.drawCircle(x, y, AIM_RADIUS_DP * density, aimOutline)
        }
    }

    override fun onDetachedFromWindow() {
        fade?.cancel()
        super.onDetachedFromWindow()
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
    }
}
