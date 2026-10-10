package com.vpr.screenlate.languages

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TurnOffLanguageDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pendingDownloadsWithoutInstalledFilesAreDeletedByDefault() {
        var deleteFiles: Boolean? = null
        compose.setContent {
            ScreenlateTheme { TurnOffLanguageDialog(Language.ENGLISH, 0L, { deleteFiles = it }, {}) }
        }
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.home_language_delete_files,
            android.text.format.Formatter.formatShortFileSize(context, 0L)))).assertIsOn()
        compose.onNodeWithText(context.getString(R.string.home_language_turn_off_confirm)).performClick()
        compose.runOnIdle { assertThat(deleteFiles).isTrue() }
    }

    @Test
    fun theUserCanKeepThePendingDownloads() {
        var deleteFiles: Boolean? = null
        compose.setContent {
            ScreenlateTheme { TurnOffLanguageDialog(Language.ENGLISH, 0L, { deleteFiles = it }, {}) }
        }
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.home_language_delete_files,
            android.text.format.Formatter.formatShortFileSize(context, 0L)))).performClick()
        compose.onNodeWithText(context.getString(R.string.home_language_turn_off_confirm)).performClick()
        compose.runOnIdle { assertThat(deleteFiles).isFalse() }
    }
}
