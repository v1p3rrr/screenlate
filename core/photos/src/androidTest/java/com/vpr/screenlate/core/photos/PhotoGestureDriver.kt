package com.vpr.screenlate.core.photos

import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in manual overlay check: a separate test process injects gestures without restarting the app or its service. */
@RunWith(AndroidJUnit4::class)
class PhotoGestureDriver {
    @Test
    fun drivePhotoGesture() {
        val args = InstrumentationRegistry.getArguments()
        val gesture = args.getString("photoGesture")
        assumeTrue("Explicit emulator-only gesture arguments required", gesture in listOf("pinch", "pan") &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val centerX = args.getString("centerX")!!.toFloat()
        val centerY = args.getString("centerY")!!.toFloat()
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, points: List<Pair<Float, Float>>) {
            val properties = Array(points.size) { index -> MotionEvent.PointerProperties().apply {
                id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = Array(points.size) { index -> MotionEvent.PointerCoords().apply {
                x = points[index].first; y = points[index].second; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points.size, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        if (gesture == "pinch") {
            val start = args.getString("startSpan")!!.toFloat()
            val end = args.getString("endSpan")!!.toFloat()
            send(MotionEvent.ACTION_DOWN, listOf(centerX - start to centerY))
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(centerX - start to centerY, centerX + start to centerY))
            for (step in 1..20) {
                SystemClock.sleep(25)
                val span = start + (end - start) * step / 20
                send(MotionEvent.ACTION_MOVE, listOf(centerX - span to centerY, centerX + span to centerY))
            }
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(centerX - end to centerY, centerX + end to centerY))
            send(MotionEvent.ACTION_UP, listOf(centerX - end to centerY))
        } else {
            val dx = args.getString("panX")!!.toFloat()
            val dy = args.getString("panY")!!.toFloat()
            send(MotionEvent.ACTION_DOWN, listOf(centerX to centerY))
            for (step in 1..20) {
                SystemClock.sleep(25)
                send(MotionEvent.ACTION_MOVE, listOf(centerX + dx * step / 20 to centerY + dy * step / 20))
            }
            send(MotionEvent.ACTION_UP, listOf(centerX + dx to centerY + dy))
        }
    }
}
