package com.vpr.screenlate.camera

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TapToFocusInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.log.Log
import java.io.File
import java.text.NumberFormat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay

@Composable
internal fun LiveCamera(
    flash: FlashMode,
    busy: Boolean,
    onFlashAvailable: (Boolean) -> Unit,
    onCapture: (suspend (File) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var attempt by remember { mutableIntStateOf(0) }
    val controller = remember(context, attempt) {
        LifecycleCameraController(context.applicationContext).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
            previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
        }
    }
    val preview = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var ready by remember(controller) { mutableStateOf(false) }
    var failed by remember(controller) { mutableStateOf(false) }
    var noRear by remember(controller) { mutableStateOf(false) }
    var zoom by remember(controller) { mutableFloatStateOf(1f) }
    var focus by remember(controller) { mutableStateOf<TapToFocusInfo?>(null) }
    var focusVisible by remember(controller) { mutableStateOf(false) }
    SideEffect { controller.imageCaptureFlashMode = flash.cameraX }

    DisposableEffect(controller, owner) {
        var active = true
        val zoomObserver = Observer<ZoomState> { zoom = it.zoomRatio }
        val focusObserver = Observer<TapToFocusInfo> {
            focus = it
            focusVisible = it.tapPoint != null && it.focusState != CameraController.TAP_TO_FOCUS_NOT_STARTED
        }
        controller.zoomState.observe(owner, zoomObserver)
        controller.tapToFocusInfoState.observe(owner, focusObserver)
        val future = controller.initializationFuture
        future.addListener(
            {
                if (active) {
                    try {
                        future.get()
                        if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            noRear = true
                        } else {
                            preview.controller = controller
                            controller.bindToLifecycle(owner)
                            onFlashAvailable(controller.cameraInfo?.hasFlashUnit() == true)
                            ready = true
                        }
                    } catch (e: Exception) {
                        failed = true
                        Log.w("Camera", "Initialization failed: " + e.javaClass.simpleName)
                    }
                }
            },
            executor,
        )
        onDispose {
            active = false
            ready = false
            onFlashAvailable(false)
            controller.zoomState.removeObserver(zoomObserver)
            controller.tapToFocusInfoState.removeObserver(focusObserver)
            preview.controller = null
            controller.unbind()
        }
    }
    LaunchedEffect(focus) {
        if (focus?.focusState != CameraController.TAP_TO_FOCUS_STARTED) {
            delay(800)
            focusVisible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (failed || noRear) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (noRear) R.string.camera_no_rear else R.string.camera_unavailable),
                    color = Color.White,
                )
                TextButton(onClick = { attempt++ }) {
                    Text(stringResource(R.string.update_retry), color = Color.White)
                }
            }
        } else {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            if (!ready) CircularProgressIndicator()
            if (focusVisible) {
                val point = focus?.tapPoint
                if (point != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White, 26.dp.toPx(), Offset(point.x, point.y), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (ready && kotlin.math.abs(zoom - 1f) > 0.01f) {
                    val resetLabel = stringResource(R.string.camera_zoom_reset)
                    TextButton(
                        onClick = { controller.setZoomRatio(1f) }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = resetLabel },
                    ) {
                        val number = NumberFormat.getNumberInstance().apply {
                            minimumFractionDigits = 1
                            maximumFractionDigits = 1
                        }.format(zoom)
                        Text(number + "×", color = Color.White)
                    }
                }
                FilledIconButton(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    enabled = ready && !busy,
                    onClick = {
                        onCapture { file ->
                            // ImageCapture has no cancellation handle. Wait for its terminal callback before the
                            // ViewModel may discard the file; never unlink a file still being written.
                            suspendCoroutine { continuation ->
                                try {
                                    controller.takePicture(
                                        ImageCapture.OutputFileOptions.Builder(file).build(),
                                        executor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                                continuation.resume(Unit)
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Log.w("Camera", "Capture error " + exception.imageCaptureError)
                                                continuation.resumeWithException(exception)
                                            }
                                        },
                                    )
                                } catch (e: Exception) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(painterResource(R.drawable.ic_photo_camera), stringResource(R.string.camera_take_photo))
                    }
                }
            }
        }
    }
}
