package com.vpr.screenlate.anki

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiProfileEditorsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun switchingLanguagesWhileTagsAreFocusedDoesNotCopyThePreviousTags() {
        val language = mutableStateOf(Language.JAPANESE)
        val tags = mutableMapOf(Language.JAPANESE to "JapaneseTags", Language.ENGLISH to "EnglishTags")
        compose.setContent {
            ScreenlateTheme {
                val shown = language.value
                AnkiProfileEditors(shown, 1L) {
                    EditableText("tags", tags.getValue(shown), "Tags") { tags[shown] = it }
                }
            }
        }
        compose.onNodeWithText("Tags").performClick()
        compose.runOnIdle { language.value = Language.ENGLISH }
        compose.onNodeWithText("Tags").assertTextContains("EnglishTags")
        compose.onNodeWithText("Tags").performClick().performTextInput("New")
        compose.runOnIdle {
            assertThat(tags.getValue(Language.JAPANESE)).isEqualTo("JapaneseTags")
            assertThat(tags.getValue(Language.ENGLISH)).contains("EnglishTags")
            assertThat(tags.getValue(Language.ENGLISH)).doesNotContain("JapaneseTags")
        }
    }

    @Test
    fun matchingFieldNamesGetTheNewLanguagesTemplateWhileFocused() {
        val language = mutableStateOf(Language.JAPANESE)
        val templates = mutableMapOf(Language.JAPANESE to "JapaneseTemplate", Language.ENGLISH to "EnglishTemplate")
        compose.setContent {
            ScreenlateTheme {
                val shown = language.value
                AnkiProfileEditors(shown, 1L) {
                    Column {
                        TemplateField("Meaning", templates.getValue(shown), emptyList()) { templates[shown] = it }
                    }
                }
            }
        }
        compose.onNodeWithText("Meaning").performClick()
        compose.runOnIdle { language.value = Language.ENGLISH }
        compose.onNodeWithText("Meaning").assertTextContains("EnglishTemplate")
        compose.onNodeWithText("Meaning").performClick().performTextInput("New")
        compose.runOnIdle {
            assertThat(templates.getValue(Language.JAPANESE)).isEqualTo("JapaneseTemplate")
            assertThat(templates.getValue(Language.ENGLISH)).contains("EnglishTemplate")
            assertThat(templates.getValue(Language.ENGLISH)).doesNotContain("JapaneseTemplate")
        }
    }
}
