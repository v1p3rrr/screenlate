package com.vpr.screenlate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.theme.ColorMath
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.theme.AccentDefaults
import kotlin.math.roundToInt

/**
 * Dialog for choosing an arbitrary color with interactive HSL sliders, a hex input field, and live preview.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsl = remember(initialColor) { ColorMath.rgbToHsl(initialColor) }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsl[0]) }
    var saturation by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsl[1]) }
    var lightness by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsl[2]) }
    var hexInput by rememberSaveable(initialColor) {
        mutableStateOf(ColorMath.toHex(initialColor).removePrefix("#"))
    }
    var hexError by rememberSaveable { mutableStateOf(false) }

    fun syncHexFromHsl() {
        val rgb = ColorMath.hslToRgb(hue, saturation, lightness)
        hexInput = ColorMath.toHex(rgb).removePrefix("#")
        hexError = false
    }

    val currentColor = remember(hue, saturation, lightness) {
        ColorMath.hslToRgb(hue, saturation, lightness)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val isLight = remember(currentColor) { ColorMath.isLightColor(currentColor) }
                val contentColor = if (isLight) Color(0xFF1C1B20) else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ColorMath.toHex(currentColor),
                        color = contentColor,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.color_picker_hue), style = MaterialTheme.typography.bodyMedium)
                        Text("${hue.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            syncHexFromHsl()
                        },
                        valueRange = 0f..360f,
                        colors = AccentDefaults.sliderColors(),
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.color_picker_saturation), style = MaterialTheme.typography.bodyMedium)
                        Text("${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = saturation,
                        onValueChange = {
                            saturation = it
                            syncHexFromHsl()
                        },
                        valueRange = 0f..1f,
                        colors = AccentDefaults.sliderColors(),
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.color_picker_lightness), style = MaterialTheme.typography.bodyMedium)
                        Text("${(lightness * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = lightness,
                        onValueChange = {
                            lightness = it
                            syncHexFromHsl()
                        },
                        valueRange = 0f..1f,
                        colors = AccentDefaults.sliderColors(),
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
                            val hsl = ColorMath.rgbToHsl(parsed)
                            hue = hsl[0]
                            saturation = hsl[1]
                            lightness = hsl[2]
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
                    modifier = Modifier.fillMaxWidth(),
                )
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
