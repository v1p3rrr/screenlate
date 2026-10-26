package com.vpr.screenlate.camera

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TapToFocusInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.ui.theme.LocalEInk
import java.io.File
import java.text.NumberFormat
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay

/**
 * The live back camera. The preview shows the whole 4:3 frame a photo gets; around it a blurred copy of the live image
 * fills the rest of the area (plain black on e-ink). [start] and [end] are the round buttons left and right of the
 * shutter; [overlay] draws over the preview, such as the language chip.
 */
@Composable
internal fun LiveCamera(
    flash: FlashMode,
    busy: Boolean,
    onFlashAvailable: (Boolean) -> Unit,
    onCapture: (suspend (File) -> Unit) -> Unit,
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val eInk = LocalEInk.current
    var attempt by remember { mutableIntStateOf(0) }
    val controller = remember(context, attempt, eInk) {
        LifecycleCameraController(context.applicationContext).apply {
            setEnabledUseCases(if (eInk) CameraController.IMAGE_CAPTURE else CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
            previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
            if (!eInk) {
                // Only a few dozen pixels are drawn, blurred; a small stream keeps the analysis cheap.
                imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
            }
        }
    }
    val preview = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    var ready by remember(controller) { mutableStateOf(false) }
    var failed by remember(controller) { mutableStateOf(false) }
    var noRear by remember(controller) { mutableStateOf(false) }
    var zoom by remember(controller) { mutableFloatStateOf(1f) }
    var focus by remember(controller) { mutableStateOf<TapToFocusInfo?>(null) }
    var focusVisible by remember(controller) { mutableStateOf(false) }
    var fill by remember(controller) { mutableStateOf<ImageBitmap?>(null) }
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
        val analysis = if (eInk) null else Executors.newSingleThreadExecutor()
        if (analysis != null) {
            var last = 0L
            controller.setImageAnalysisAnalyzer(analysis) { image ->
                image.use {
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= FILL_INTERVAL_MS) {
                        last = now
                        val frame = runCatching { fillFrame(it) }.getOrNull()
                        if (frame != null && active) fill = frame.asImageBitmap()
                    }
                }
            }
        }
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
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysis?.shutdown()
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
            fill?.let { image ->
                // Bilinear upscaling of the tiny frame already blurs it; the blur effect smooths it further where available.
                Image(
                    image, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            if (!ready) CircularProgressIndicator(color = Color.White)
            if (focusVisible) {
                val point = focus?.tapPoint
                if (point != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White, 26.dp.toPx(), Offset(point.x, point.y), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            overlay()
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.heightIn(min = 32.dp)) {
                    if (ready && kotlin.math.abs(zoom - 1f) > 0.01f) {
                        val resetLabel = stringResource(R.string.camera_zoom_reset)
                        val number = NumberFormat.getNumberInstance().apply {
                            minimumFractionDigits = 1
                            maximumFractionDigits = 1
                        }.format(zoom)
                        Surface(
                            onClick = { controller.setZoomRatio(1f) }, enabled = !busy, shape = CircleShape,
                            color = if (eInk) Color.White else Color.Black.copy(alpha = 0.55f),
                            contentColor = if (eInk) Color.Black else Color.White,
                            modifier = Modifier.semantics { contentDescription = resetLabel },
                        ) {
                            Text(number + "×", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(HISTORY_BUTTON), contentAlignment = Alignment.Center) { start() }
                    Spacer(Modifier.width(SHUTTER_GAP))
                    Shutter(enabled = ready && !busy, busy = busy) {
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
                    }
                    Spacer(Modifier.width(SHUTTER_GAP))
                    Box(Modifier.size(HISTORY_BUTTON), contentAlignment = Alignment.Center) { end() }
                }
            }
        }
    }
}

/** A white ring around a white disc that shrinks while pressed; a spinner replaces the disc while a photo is taken. */
@Composable
private fun Shutter(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.camera_take_photo)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled || busy) 1f else 0.5f)
            .border(4.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
        } else {
            Box(Modifier.size(58.dp).scale(if (pressed) 0.88f else 1f).background(Color.White, CircleShape))
        }
    }
}

private const val FILL_INTERVAL_MS = 100L

/** Side of the blurred fill's longer edge, in pixels. */
private const val FILL_SIDE = 24

/**
 * The sizes a frame goes through on its way to the fill: halved while that keeps it at least twice [side], then
 * [side] on the longer edge. Bilinear filtering samples only 2×2 pixels, so one big step would alias and flicker.
 */
internal fun fillSteps(width: Int, height: Int, side: Int = FILL_SIDE): List<Pair<Int, Int>> {
    require(width > 0 && height > 0 && side > 0)
    val steps = mutableListOf<Pair<Int, Int>>()
    var w = width
    var h = height
    while (maxOf(w, h) / 2 >= side * 2) {
        w = maxOf(1, w / 2)
        h = maxOf(1, h / 2)
        steps += w to h
    }
    val longer = maxOf(w, h).toFloat()
    steps += maxOf(1, Math.round(w * side / longer)) to maxOf(1, Math.round(h * side / longer))
    return steps
}

/** The frame reduced to a few dozen pixels and turned upright. */
private fun fillFrame(image: ImageProxy): Bitmap {
    var frame = image.toBitmap()
    for ((w, h) in fillSteps(frame.width, frame.height)) {
        val next = Bitmap.createScaledBitmap(frame, w, h, true)
        if (next !== frame) frame.recycle()
        frame = next
    }
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return frame
    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true).also { if (it !== frame) frame.recycle() }
}

private val SHUTTER_GAP = 32.dp
