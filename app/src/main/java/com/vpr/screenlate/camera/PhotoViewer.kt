package com.vpr.screenlate.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.geometry.Box as PhotoBox
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoViewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A fit-to-area view; recognition coordinates remain in the original, oriented image. */
@Composable
internal fun PhotoViewer(document: PhotoDocument, onViewport: (Long, PhotoViewport?) -> Unit) {
    var area by remember(document.id) { mutableStateOf<PhotoBox?>(null) }
    val viewport = area?.takeIf { it.width > 0f && it.height > 0f }?.let {
        PhotoViewport.fit(document.width, document.height, it)
    }
    val sample = viewport?.let { displaySampleSize(it.scale) }
    var bitmap by remember(document.id, sample) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(document.id, sample) { mutableStateOf(false) }
    LaunchedEffect(document.id, sample) {
        if (sample == null) return@LaunchedEffect
        var decoded: Bitmap? = null
        var published = false
        try {
            decoded = withContext(Dispatchers.IO + NonCancellable) { decodeForDisplay(document, sample) }
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
    LaunchedEffect(document.id, viewport, bitmap) {
        onViewport(document.id, viewport.takeIf { bitmap != null })
    }
    DisposableEffect(document.id) {
        onDispose { onViewport(document.id, null) }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { coordinates ->
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
                }
            }
        } else if (failed) {
            Text(
                stringResource(R.string.camera_photo_unreadable), color = Color.White,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
