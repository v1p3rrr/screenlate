package com.vpr.screenlate.debug

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug-only full-screen image viewer used as a backdrop for testing the overlay on known images. Opened with
 * `adb shell am start -n <pkg>/com.vpr.screenlate.MainActivity --es debug_image <file>`, where the file lives in
 * the app's internal files directory (copy it there with `adb shell run-as`). `--es debug_caption <text>` puts real
 * text above the image, for testing app text together with OCR.
 */
@Composable
fun ImageViewerScreen(path: String, caption: String = "") {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Cannot decode $path", color = Color.White)
        }
        if (caption.isNotEmpty()) {
            Text(
                caption,
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}
