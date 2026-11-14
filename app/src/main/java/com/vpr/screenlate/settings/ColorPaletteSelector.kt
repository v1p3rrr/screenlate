package com.vpr.screenlate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.theme.ColorMath
import com.vpr.screenlate.core.common.theme.ColorPreset

fun ColorPreset.displayNameRes(): Int = when (this) {
    ColorPreset.VIOLET -> R.string.color_preset_violet
    ColorPreset.OCEAN -> R.string.color_preset_ocean
    ColorPreset.EMERALD -> R.string.color_preset_emerald
    ColorPreset.AMBER -> R.string.color_preset_amber
    ColorPreset.CORAL -> R.string.color_preset_coral
    ColorPreset.SUNSET -> R.string.color_preset_sunset
    ColorPreset.LAVENDER -> R.string.color_preset_lavender
    ColorPreset.TEAL -> R.string.color_preset_teal
}

/**
 * Row of circular color swatches: the 8 curated presets, optional system wallpaper chip (Android 12+),
 * and a custom color button that opens [ColorPickerDialog].
 */
@Composable
fun ColorPaletteSelector(
    selectedColor: Int?,
    isSystemSelected: Boolean = false,
    showSystemOption: Boolean = false,
    onSelectPreset: (ColorPreset) -> Unit,
    onSelectSystem: (() -> Unit)? = null,
    onSelectCustom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val isCustomSelected = !isSystemSelected && selectedColor != null &&
        ColorPreset.entries.none { it.primarySeed == selectedColor }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 8 Presets
        ColorPreset.entries.forEach { preset ->
            val isPresetSelected = !isSystemSelected && selectedColor == preset.primarySeed
            val isLight = remember(preset.primarySeed) { ColorMath.isLightColor(preset.primarySeed) }
            val checkTint = if (isLight) Color(0xFF1C1B20) else Color.White
            val name = stringResource(preset.displayNameRes())

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(preset.primarySeed))
                    .border(
                        width = if (isPresetSelected) 2.5.dp else 1.dp,
                        color = if (isPresetSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelectPreset(preset) }
                    .semantics {
                        this.contentDescription = name
                        this.selected = isPresetSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isPresetSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Optional System option (Android 12+)
        if (showSystemOption && onSelectSystem != null) {
            val systemDesc = stringResource(R.string.settings_colors_system)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSystemSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .border(
                        width = if (isSystemSelected) 2.5.dp else 1.dp,
                        color = if (isSystemSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelectSystem() }
                    .semantics {
                        this.contentDescription = systemDesc
                        this.selected = isSystemSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_style),
                    contentDescription = null,
                    tint = if (isSystemSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Custom color option
        val customDesc = stringResource(R.string.color_custom)
        val customBgColor = if (isCustomSelected) {
            Color(selectedColor!!)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val customIconTint = if (isCustomSelected) {
            val isLight = remember(selectedColor) { ColorMath.isLightColor(selectedColor!!) }
            if (isLight) Color(0xFF1C1B20) else Color.White
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(customBgColor)
                .border(
                    width = if (isCustomSelected) 2.5.dp else 1.dp,
                    color = if (isCustomSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable { showCustomPicker = true }
                .semantics {
                    this.contentDescription = customDesc
                    this.selected = isCustomSelected
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_palette),
                contentDescription = null,
                tint = customIconTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (showCustomPicker) {
        val initialColor = if (isCustomSelected) {
            selectedColor!!
        } else {
            selectedColor ?: ColorPreset.VIOLET.primarySeed
        }
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { showCustomPicker = false },
            onConfirm = { color ->
                showCustomPicker = false
                onSelectCustom(color)
            },
        )
    }
}
