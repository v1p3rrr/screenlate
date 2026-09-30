package com.vpr.screenlate.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BubbleStateTest {
    @Test
    fun `a running service shows or hides the bubble as the setting says`() {
        assertThat(bubbleState(serviceRunning = true, visible = true)).isEqualTo(BubbleState.SHOWN)
        assertThat(bubbleState(serviceRunning = true, visible = false)).isEqualTo(BubbleState.HIDDEN)
    }

    @Test
    fun `a stopped service leaves no bubble, whatever the setting says`() {
        // The setting stays on while the phone stops the service, and the button then cannot do more than open the
        // accessibility settings, so the two states must not be told apart by the setting alone.
        assertThat(bubbleState(serviceRunning = false, visible = true)).isEqualTo(BubbleState.STOPPED)
        assertThat(bubbleState(serviceRunning = false, visible = false)).isEqualTo(BubbleState.STOPPED)
    }
}
