package com.vpr.screenlate.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.geometry.Box as PhotoBox
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoViewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

/** Pan/zoom changes only the viewport. A small fitted bitmap and a visible-region detail keep display memory bounded. */
@Composable
internal fun PhotoViewer(document: PhotoDocument, onViewport: (Long, PhotoViewport?) -> Unit) {
    var area by remember(document.id) { mutableStateOf<PhotoBox?>(null) }
    val photoLabel = stringResource(R.string.camera_photo_description)
    val fit = area?.takeIf { it.width > 0f && it.height > 0f }?.let {
        PhotoViewport.fit(document.width, document.height, it)
    }
    var moved by remember(document.id) { mutableStateOf<PhotoViewport?>(null) }
    // The status strip above may change height; the photo keeps its zoom and center instead of fitting again.
    val viewport = fit?.let { moved?.resized(it.area) } ?: fit
    val sample = fit?.let { displaySampleSize(it.scale) }
    var bitmap by remember(document.id, sample) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(document.id, sample) { mutableStateOf(false) }
    var detail by remember(document.id) { mutableStateOf<PhotoDetail?>(null) }
    val detailSample = viewport?.let { displaySampleSize(it.scale) }
    val detailRegion = if (detailSample != null && sample != null && detailSample < sample) visibleRawRegion(document, viewport) else null
    LaunchedEffect(document.id, sample) {
        if (sample == null) return@LaunchedEffect
        var decoded: Bitmap? = null
        var published = false
        try {
            withContext(Dispatchers.IO + NonCancellable) { decoded = decodeForDisplay(document, sample) }
            coroutineContext.ensureActive()
            bitmap = decoded
            failed = decoded == null
            published = true
        } catch (_: java.io.IOException) {
            failed = true
        } finally {
            // Once published, Compose may still draw it; collection of the disposed view releases its bitmap.
            if (!published) decoded?.recycle()
        }
    }
    LaunchedEffect(document.id, detailRegion, detailSample) {
        if (detailRegion == null || detailSample == null) { detail = null; return@LaunchedEffect }
        delay(150)
        var decoded: PhotoDetail? = null
        var published = false
        try {
            withContext(Dispatchers.IO + NonCancellable) { decoded = decodeDetail(document, detailRegion, detailSample) }
            coroutineContext.ensureActive()
            detail = decoded
            published = true
        } catch (e: CancellationException) { throw e }
        catch (_: java.io.IOException) { /* The fitted image remains usable while a detail patch fails. */ }
        finally { if (!published) decoded?.bitmap?.recycle() }
    }
    LaunchedEffect(document.id, viewport, bitmap) {
        onViewport(document.id, viewport.takeIf { bitmap != null })
    }
    DisposableEffect(document.id) {
        onDispose { onViewport(document.id, null) }
    }
    Box(
        Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHigh).semantics {
            contentDescription = photoLabel
            if (viewport != null && fit != null) stateDescription = java.text.NumberFormat.getPercentInstance().format(viewport.scale / fit.scale)
        }.pointerInput(document.id, fit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val initial = fit?.let { moved?.resized(it.area) } ?: fit
                val bounds = area
                if (initial != null && bounds != null) {
                    moved = initial.transformed(centroid.x + bounds.left, centroid.y + bounds.top, pan.x, pan.y, zoom)
                }
            }
        }.onGloballyPositioned { coordinates ->
            val origin = coordinates.positionOnScreen()
            area = PhotoBox(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
        },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        val frame = viewport
        val bounds = area
        if (image != null && frame != null && bounds != null) {
            val composed = remember(image) { image.asImageBitmap() }
            val transform = imageToViewer(document, frame, bounds.left, bounds.top)
            Canvas(Modifier.fillMaxSize()) {
                withTransform({ transform(transform) }) {
                    drawImage(composed, dstSize = IntSize(document.rawWidth, document.rawHeight))
                    detail?.let { patch ->
                        drawImage(patch.bitmap.asImageBitmap(), dstOffset = IntOffset(patch.raw.left, patch.raw.top),
                            dstSize = IntSize(patch.raw.width(), patch.raw.height()))
                    }
                }
            }
        } else if (failed) {
            Text(
                stringResource(R.string.camera_photo_unreadable), color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
