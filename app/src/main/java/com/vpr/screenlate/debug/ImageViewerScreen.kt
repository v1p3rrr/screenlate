package com.vpr.screenlate.debug

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Debug-only full-screen image viewer used as a backdrop for testing the overlay on known images. Opened with
 * `adb shell am start -n <pkg>/com.vpr.screenlate.MainActivity --es debug_image <file>`, where the file lives in
 * the app's internal files directory (copy it there with `adb shell run-as`).
 */
@Composable
fun ImageViewerScreen(path: String) {
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
    }
}
