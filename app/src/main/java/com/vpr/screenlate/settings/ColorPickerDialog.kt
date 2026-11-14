package com.vpr.screenlate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.theme.ColorMath
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.theme.AccentDefaults

/**
 * Dialog for choosing an arbitrary color with an interactive 2D saturation/brightness gradient canvas,
 * a rainbow hue spectrum bar, a live preview, and a HEX input field.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialColor) { ColorMath.rgbToHsv(initialColor) }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by rememberSaveable(initialColor) {
        mutableStateOf(ColorMath.toHex(initialColor).removePrefix("#"))
    }
    var hexError by rememberSaveable { mutableStateOf(false) }

    fun syncHexFromHsv() {
        val rgb = ColorMath.hsvToRgb(hue, saturation, value)
        hexInput = ColorMath.toHex(rgb).removePrefix("#")
        hexError = false
    }

    val currentColor = remember(hue, saturation, value) {
        ColorMath.hsvToRgb(hue, saturation, value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 2D Saturation-Value interactive gradient canvas
                SaturationValueBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    currentColor = currentColor,
                    onColorChanged = { s, v ->
                        saturation = s
                        value = v
                        syncHexFromHsv()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )

                // Rainbow Hue spectrum slider
                HueBar(
                    hue = hue,
                    onHueChanged = { h ->
                        hue = h
                        syncHexFromHsv()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                // Color preview & HEX input field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLight = remember(currentColor) { ColorMath.isLightColor(currentColor) }
                    val contentColor = if (isLight) Color(0xFF1C1B20) else Color.White
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isLight) "Aa" else "Aa",
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val cleaned = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hexInput = cleaned
                            val parsed = ColorMath.parseColor("#$cleaned")
                            if (parsed != null) {
                                hexError = false
                                val hsv = ColorMath.rgbToHsv(parsed)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            } else {
                                hexError = cleaned.length == 6
                            }
                        },
                        label = { Text(stringResource(R.string.color_picker_hex)) },
                        prefix = { Text("#") },
                        isError = hexError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = doneClearsFocus(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentColor) },
                colors = AccentDefaults.buttonColors(),
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 2D color canvas representing Saturation on the X axis and Brightness/Value on the Y axis.
 */
@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    currentColor: Int,
    onColorChanged: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pureHueColor = remember(hue) {
        Color(ColorMath.hsvToRgb(hue, 1f, 1f))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = (down.position.x / size.width).coerceIn(0f, 1f)
                    val v = (1f - down.position.y / size.height).coerceIn(0f, 1f)
                    onColorChanged(s, v)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val newS = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newV = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onColorChanged(newS, newV)
                    }
                }
            }
            .drawBehind {
                // 1. Horizontal gradient: White -> Pure Hue Color
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, pureHueColor),
                    ),
                )
                // 2. Vertical gradient: Transparent -> Black
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                    ),
                )

                // 3. Circular selector thumb
                val thumbX = (saturation * size.width).coerceIn(0f, size.width)
                val thumbY = ((1f - value) * size.height).coerceIn(0f, size.height)
                val radius = 10.dp.toPx()
                val center = Offset(thumbX, thumbY)

                drawCircle(
                    color = Color(0x99000000),
                    radius = radius + 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                drawCircle(
                    color = Color(currentColor),
                    radius = (radius - 2.5.dp.toPx()).coerceAtLeast(1f),
                    center = center,
                )
            },
    )
}

/**
 * Rainbow hue spectrum slider bar across [0°, 360°].
 */
@Composable
private fun HueBar(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbowColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
    }
    val pureHueColor = remember(hue) {
        Color(ColorMath.hsvToRgb(hue, 1f, 1f))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val h = (down.position.x / size.width).coerceIn(0f, 1f) * 360f
                    onHueChanged(h.coerceIn(0f, 360f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val newH = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        onHueChanged(newH.coerceIn(0f, 360f))
                    }
                }
            }
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(rainbowColors),
                )

                val thumbX = (hue / 360f).coerceIn(0f, 1f) * size.width
                val thumbRadius = size.height / 2f - 2.dp.toPx()
                val center = Offset(thumbX, size.height / 2f)

                drawCircle(
                    color = Color(0x99000000),
                    radius = thumbRadius + 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                drawCircle(
                    color = pureHueColor,
                    radius = (thumbRadius - 2.5.dp.toPx()).coerceAtLeast(1f),
                    center = center,
                )
            },
    )
}
