package com.vpr.screenlate.camera

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real PreviewView gestures; run on API 30 because Espresso's input injector does not support API 37. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class LiveCameraTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun previewFocusAndPinchZoomWithOneTapReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Grant CAMERA to the debug app before this hardware test",
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        compose.setContent { ScreenlateTheme { LiveCamera(FlashMode.OFF, false, {}, {}) } }
        val shutter = context.getString(R.string.camera_take_photo)
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription(shutter) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(shutter).assertIsEnabled()
        lateinit var controller: LifecycleCameraController
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            controller = preview(activity.window.decorView)!!.controller as LifecycleCameraController
            assumeTrue("The test camera must support zoom", controller.zoomState.value!!.maxZoomRatio > 1.5f)
        }
        compose.onRoot().performTouchInput { click(Offset(center.x, height * 0.4f)) }
        compose.waitUntil(5000) {
            var focused = false
            compose.runOnIdle { focused = controller.tapToFocusInfoState.value?.tapPoint != null }
            focused
        }
        compose.onRoot().performTouchInput {
            pinch(
                Offset(center.x - width * 0.08f, center.y), Offset(center.x + width * 0.08f, center.y),
                Offset(center.x - width * 0.3f, center.y), Offset(center.x + width * 0.3f, center.y),
                durationMillis = 600,
            )
        }
        compose.waitUntil(5000) {
            var zoomed = false
            compose.runOnIdle { zoomed = controller.zoomState.value!!.zoomRatio > 1.1f }
            zoomed
        }
        compose.onNodeWithContentDescription(context.getString(R.string.camera_zoom_reset)).performClick()
        compose.waitUntil(5000) {
            var reset = false
            compose.runOnIdle { reset = kotlin.math.abs(controller.zoomState.value!!.zoomRatio - 1f) < 0.01f }
            reset
        }
        compose.runOnIdle { assertThat(controller.zoomState.value!!.zoomRatio).isWithin(0.01f).of(1f) }
    }

    private fun preview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) preview(view.getChildAt(index))?.let { return it }
        return null
    }
}
