package com.vpr.screenlate.overlay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.vpr.screenlate.core.common.diagnostics.ProcessDiagnostics
import com.vpr.screenlate.overlay.R
import kotlin.math.roundToInt

/**
 * Floating, draggable diagnostics HUD displaying real-time process memory (PSS, Heap, Native),
 * thread/process CPU time, service lifecycle state, and system memory trimming events.
 */
class DiagnosticsHud(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClose: (() -> Unit)? = null,
) {
    private var containerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateMetrics()
            if (attached) {
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    private var metricsText: TextView? = null
    private var stateText: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (attached) return
        val density = context.resources.displayMetrics.density
        val paddingHorizontal = (10 * density).roundToInt()
        val paddingVertical = (6 * density).roundToInt()
        val cornerRadius = 8 * density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            background = GradientDrawable().apply {
                setColor(0xDD18181E.toInt())
                setCornerRadius(cornerRadius)
                setStroke((1 * density).roundToInt(), 0x33FFFFFF.toInt())
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.diagnostics_hud_header)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(0xFF999999.toInt())
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(title)

        if (onClose != null) {
            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0xFFCCCCCC.toInt())
                setPadding((6 * density).roundToInt(), 0, (6 * density).roundToInt(), 0)
                setOnClickListener { onClose.invoke() }
            }
            headerRow.addView(closeBtn)
        }
        container.addView(headerRow)

        val metrics = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.MONOSPACE
            setLineSpacing(2f * density, 1f)
        }
        container.addView(metrics)
        metricsText = metrics

        val state = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFF6CD080.toInt())
            typeface = Typeface.MONOSPACE
        }
        container.addView(state)
        stateText = state

        val params = OverlayWindows.hudParams().apply {
            x = (16 * density).roundToInt()
            y = (96 * density).roundToInt()
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        params.x = startX + dx
                        params.y = startY + dy
                        if (attached && containerView != null) {
                            runCatching { windowManager.updateViewLayout(container, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return false
            }
        })

        containerView = container
        if (OverlayWindows.add(windowManager, container, params)) {
            attached = true
            updateMetrics()
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
        }
    }

    fun hide() {
        if (!attached) return
        attached = false
        handler.removeCallbacks(refreshRunnable)
        containerView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        containerView = null
        metricsText = null
        stateText = null
    }

    private fun updateMetrics() {
        val snapshot = ProcessDiagnostics.snapshot()
        metricsText?.text = "PSS: ${snapshot.totalPssMb} MB | Heap: ${snapshot.heapUsedMb}/${snapshot.heapMaxMb} MB"
        val stateName = when (snapshot.state) {
            com.vpr.screenlate.core.common.diagnostics.OverlayLifecycleState.OCR -> "OCR"
            else -> snapshot.state.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        stateText?.text = "CPU: ~${snapshot.cpuTimeMs} ms | State: $stateName"
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1000L
    }
}
