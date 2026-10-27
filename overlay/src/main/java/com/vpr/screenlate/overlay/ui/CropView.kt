package com.vpr.screenlate.overlay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shows an image where [place] puts it, scaled to fit there, with a movable, resizable crop frame. Corners resize,
 * anything inside the frame moves it. Coordinates are screen coordinates (the view fills the screen); the editor maps
 * the frame back to the image's own pixels.
 */
@SuppressLint("ViewConstructor")
class CropView(context: Context, private val image: Bitmap) : View(context) {
    private val density = resources.displayMetrics.density
    private val handleRadius = HANDLE_DP * density
    private val minSize = MIN_SIZE_DP * density
    private val imageBounds = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())

    /** The crop frame in screen coordinates. */
    val frame = RectF(imageBounds)

    /** Draws the image in [bounds] and selects all of it. */
    fun place(bounds: RectF) {
        imageBounds.set(bounds)
        frame.set(bounds)
        invalidate()
    }

    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dimPath = Path()

    private enum class Drag { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private var drag = Drag.NONE
    private var lastX = 0f
    private var lastY = 0f

    fun setFrame(rect: RectF) {
        frame.set(rect)
        // A frame outside the image is kept as is by intersect(); the whole image is the safe choice.
        if (!frame.intersect(imageBounds)) frame.set(imageBounds)
        invalidate()
    }

    fun selectAll() = setFrame(imageBounds)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(image, null, imageBounds, null)
        dimPath.reset()
        dimPath.fillType = Path.FillType.EVEN_ODD
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRect(frame, Path.Direction.CW)
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawRect(frame, framePaint)
        val radius = handleRadius / 2
        canvas.drawCircle(frame.left, frame.top, radius, handlePaint)
        canvas.drawCircle(frame.right, frame.top, radius, handlePaint)
        canvas.drawCircle(frame.left, frame.bottom, radius, handlePaint)
        canvas.drawCircle(frame.right, frame.bottom, radius, handlePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                drag = pick(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                apply(dx, dy)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = Drag.NONE
        }
        return true
    }

    private fun corners(): List<Pair<Float, Float>> = listOf(
        frame.left to frame.top,
        frame.right to frame.top,
        frame.left to frame.bottom,
        frame.right to frame.bottom,
    )

    private fun pick(x: Float, y: Float): Drag {
        val grab = handleRadius * 1.5f
        val corner = listOf(Drag.TOP_LEFT, Drag.TOP_RIGHT, Drag.BOTTOM_LEFT, Drag.BOTTOM_RIGHT)
            .zip(corners())
            .minByOrNull { (_, point) -> hypot(point.first - x, point.second - y) }
        if (corner != null && hypot(corner.second.first - x, corner.second.second - y) <= grab) return corner.first
        return if (frame.contains(x, y)) Drag.MOVE else Drag.NONE
    }

    private fun apply(dx: Float, dy: Float) {
        when (drag) {
            Drag.NONE -> Unit
            Drag.MOVE -> {
                val moveX = dx.coerceIn(imageBounds.left - frame.left, imageBounds.right - frame.right)
                val moveY = dy.coerceIn(imageBounds.top - frame.top, imageBounds.bottom - frame.bottom)
                frame.offset(moveX, moveY)
            }
            Drag.TOP_LEFT -> {
                frame.left = (frame.left + dx).coerceIn(imageBounds.left, frame.right - minOf(minSize, frame.width()))
                frame.top = (frame.top + dy).coerceIn(imageBounds.top, frame.bottom - minOf(minSize, frame.height()))
            }
            Drag.TOP_RIGHT -> {
                frame.right = (frame.right + dx).coerceIn(frame.left + minOf(minSize, frame.width()), imageBounds.right)
                frame.top = (frame.top + dy).coerceIn(imageBounds.top, frame.bottom - minOf(minSize, frame.height()))
            }
            Drag.BOTTOM_LEFT -> {
                frame.left = (frame.left + dx).coerceIn(imageBounds.left, frame.right - minOf(minSize, frame.width()))
                frame.bottom = (frame.bottom + dy).coerceIn(frame.top + minOf(minSize, frame.height()), imageBounds.bottom)
            }
            Drag.BOTTOM_RIGHT -> {
                frame.right = (frame.right + dx).coerceIn(frame.left + minOf(minSize, frame.width()), imageBounds.right)
                frame.bottom = (frame.bottom + dy).coerceIn(frame.top + minOf(minSize, frame.height()), imageBounds.bottom)
            }
        }
        if (abs(frame.width()) < minSize || abs(frame.height()) < minSize) frame.sort()
    }

    private companion object {
        const val HANDLE_DP = 24f
        const val MIN_SIZE_DP = 48f
    }
}
