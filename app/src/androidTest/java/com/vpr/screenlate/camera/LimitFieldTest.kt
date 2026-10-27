package com.vpr.screenlate.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on API 30: Espresso's input injector does not support API 37. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class LimitFieldTest {
    @get:Rule val compose = createComposeRule()

    /** Cancel in the question asked on leaving the page: the field keeps the focus, and Back must leave next time. */
    @Test
    fun aResetDropsALimitTypedInTheFocusedField() {
        val typed = mutableListOf<Int?>()
        var resets by mutableIntStateOf(0)
        compose.setContent {
            ScreenlateTheme { LimitField(0, resets, "Photo limit", { typed += it }) {} }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextReplacement("1")
        compose.runOnIdle {
            assertThat(typed).containsExactly(1)
            resets++
        }
        compose.waitForIdle()
        field.assertIsFocused()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("0")))
        compose.runOnIdle { assertThat(typed).containsExactly(1, null).inOrder() }
    }
}
