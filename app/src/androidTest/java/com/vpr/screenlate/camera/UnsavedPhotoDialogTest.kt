package com.vpr.screenlate.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on API 30: Espresso's input injector does not support API 37. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class UnsavedPhotoDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun saveReachesTheCallerWhileTheQuestionIsOpen() {
        assertThat(click(R.string.camera_save_history)).containsExactly("save:true", "dismiss").inOrder()
    }

    @Test
    fun deleteReachesTheCallerWhileTheQuestionIsOpen() {
        assertThat(click(R.string.action_delete)).containsExactly("delete:true", "dismiss").inOrder()
    }

    @Test
    fun cancelOnlyDismisses() {
        assertThat(click(R.string.action_cancel)).containsExactly("dismiss")
    }

    /** The history page clears its question on dismiss, so an answer must arrive while it is still asked. */
    private fun click(button: Int): List<String> {
        val calls = mutableListOf<String>()
        compose.setContent {
            ScreenlateTheme {
                var asking by remember { mutableStateOf(true) }
                if (asking) {
                    UnsavedPhotoDialog(
                        onSave = { calls += "save:$asking" },
                        onDelete = { calls += "delete:$asking" },
                        onDismiss = {
                            calls += "dismiss"
                            asking = false
                        },
                    )
                }
            }
        }
        compose.onNodeWithText(context.getString(button)).performClick()
        compose.waitForIdle()
        return calls
    }
}
