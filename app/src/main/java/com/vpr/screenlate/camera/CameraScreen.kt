package com.vpr.screenlate.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vpr.screenlate.R

/** The Camera tab. The live camera, the photo viewer and the permission prompt go into its content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.camera_title)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
