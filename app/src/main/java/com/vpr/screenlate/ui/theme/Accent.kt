package com.vpr.screenlate.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The accent of selection controls, progress and the main button, set apart from the violet of other buttons, links
 * and icons.
 *
 * @property fill large areas: a switch's track, a checked box, the chosen segment, the main button.
 * @property onFill content on [fill].
 * @property line thin marks: a radio dot, a slider, progress bars and rings.
 */
@Immutable
data class Accent(val fill: Color, val onFill: Color, val line: Color)

val LocalAccent = staticCompositionLocalOf { Accent(Color.Unspecified, Color.Unspecified, Color.Unspecified) }

/** The accent colors for Material components. */
object AccentDefaults {
    val current: Accent
        @Composable @ReadOnlyComposable get() = LocalAccent.current

    @Composable
    fun switchColors(): SwitchColors = current.let {
        SwitchDefaults.colors(
            checkedTrackColor = it.fill,
            checkedThumbColor = it.onFill,
            checkedIconColor = it.fill,
            checkedBorderColor = it.fill,
        )
    }

    @Composable
    fun checkboxColors(): CheckboxColors = current.let {
        CheckboxDefaults.colors(checkedColor = it.fill, checkmarkColor = it.onFill)
    }

    @Composable
    fun radioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(selectedColor = current.line)

    @Composable
    fun sliderColors(): SliderColors = current.let {
        SliderDefaults.colors(thumbColor = it.line, activeTrackColor = it.line, activeTickColor = it.fill)
    }

    @Composable
    fun segmentedButtonColors(): SegmentedButtonColors = current.let {
        SegmentedButtonDefaults.colors(activeContainerColor = it.fill, activeContentColor = it.onFill)
    }

    @Composable
    fun buttonColors(): ButtonColors = current.let {
        ButtonDefaults.buttonColors(containerColor = it.fill, contentColor = it.onFill)
    }

    /** Progress bars and rings. */
    val progress: Color
        @Composable @ReadOnlyComposable get() = current.line
}
