package com.vpr.screenlate.overlay.translate

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.translate.TranslationService
import org.junit.Test

class PopupTranslationSessionTest {

    private val session = PopupTranslationSession()

    @Test
    fun `default session has no chosen service and no cached translations`() {
        assertThat(session.chosenService).isNull()
        assertThat(session.getTranslation("Hello")).isNull()
    }

    @Test
    fun `chosen service persists and updates`() {
        session.chosenService = TranslationService.BING
        assertThat(session.chosenService).isEqualTo(TranslationService.BING)

        session.chosenService = TranslationService.GOOGLE
        assertThat(session.chosenService).isEqualTo(TranslationService.GOOGLE)
    }

    @Test
    fun `stored translation is retrieved for matching sentence`() {
        session.setTranslation("今日は。", "Hello.")
        assertThat(session.getTranslation("今日は。")).isEqualTo("Hello.")
        assertThat(session.getTranslation("さようなら。")).isNull()
    }

    @Test
    fun `distinct sentences maintain distinct translations`() {
        session.setTranslation("一", "One")
        session.setTranslation("二", "Two")

        assertThat(session.getTranslation("一")).isEqualTo("One")
        assertThat(session.getTranslation("二")).isEqualTo("Two")
    }

    @Test
    fun `clear resets chosen service and clears all translations`() {
        session.chosenService = TranslationService.EDGE
        session.setTranslation("テスト", "Test")

        session.clear()

        assertThat(session.chosenService).isNull()
        assertThat(session.getTranslation("テスト")).isNull()
    }
}
