package com.vpr.screenlate.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Content area of a screen with text fields: Scaffold [padding], plus room for the keyboard so the focused field
 * scrolls above it, and a tap on empty space ends editing.
 */
fun Modifier.formContent(padding: PaddingValues, focusManager: FocusManager): Modifier = this
    .padding(padding)
    .consumeWindowInsets(padding)
    .imePadding()
    .pointerInput(focusManager) { detectTapGestures(onTap = { focusManager.clearFocus() }) }

/** Keyboard "Done" ends editing, which also saves fields that save on focus loss. */
@Composable
fun doneClearsFocus(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return KeyboardActions(onDone = { focusManager.clearFocus() })
}
