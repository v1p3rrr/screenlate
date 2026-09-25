package com.vpr.screenlate.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.ocr.OcrEngineType

/** Debug screen: runs OCR on a picked image, draws the recognized lines and hit-tests taps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTestScreen(onBack: () -> Unit, viewModel: OcrTestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_test_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ocr_test_pick_image))
            }

            if (state.running) Text(stringResource(R.string.ocr_test_running))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.runs.forEach { run ->
                val lines = run.layout.page.paragraphs.sumOf { it.lines.size }
                val label = buildString {
                    append(run.engine.name)
                    append(if (run.final) " · final" else " · draft")
                    append(" · ${run.elapsedMillis} ms · $lines lines")
                    run.lensError?.let { append(" · $it") }
                }
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            state.hitText?.let { Text(stringResource(R.string.ocr_test_hit, it)) }

            state.image?.let { image ->
                val latest = state.runs.lastOrNull()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.width.toFloat() / image.height)
                        .pointerInput(image) {
                            detectTapGestures { offset ->
                                val scale = image.width / size.width.toFloat()
                                viewModel.onImageTapped(offset.x * scale, offset.y * scale)
                            }
                        },
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (latest != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scale = size.width / image.width
                            val color = if (latest.engine == OcrEngineType.LENS) Color(0xFF7C5CFF) else Color(0xFFFF8A00)
                            latest.layout.lineBoxes().forEach { box ->
                                drawRect(
                                    color = color,
                                    topLeft = Offset(box.left * scale, box.top * scale),
                                    size = Size(box.width * scale, box.height * scale),
                                    style = Stroke(width = 2f),
                                )
                            }
                            state.hitPosition?.let { position ->
                                latest.layout.boxesFor(position, 1).forEach { box ->
                                    drawRect(
                                        color = Color(0x88FFC83D),
                                        topLeft = Offset(box.left * scale, box.top * scale),
                                        size = Size(box.width * scale, box.height * scale),
                                    )
                                }
                            }
                        }
                    }
                }
                latest?.let { run ->
                    SelectionContainer { Text(run.layout.page.text) }
                }
            }
        }
    }
}
