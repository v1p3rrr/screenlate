package com.vpr.screenlate.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StopFollowUpTest {

    @Test
    fun `notifies when the service has not connected again`() {
        assertThat(StopFollowUp.after(connectionsAtStop = 1, connectionsNow = 1, notificationsAllowed = true))
            .isEqualTo(StopFollowUp.NOTIFY)
    }

    @Test
    fun `stays silent when the service connected again within the delay`() {
        assertThat(StopFollowUp.after(connectionsAtStop = 1, connectionsNow = 2, notificationsAllowed = true))
            .isEqualTo(StopFollowUp.BACK)
    }

    @Test
    fun `stays silent when the service came back and stopped again, leaving it to the later check`() {
        // Two dumps in a row: the second stop runs its own check, which sees no new connection if none comes.
        assertThat(StopFollowUp.after(connectionsAtStop = 3, connectionsNow = 4, notificationsAllowed = true))
            .isEqualTo(StopFollowUp.BACK)
        assertThat(StopFollowUp.after(connectionsAtStop = 4, connectionsNow = 4, notificationsAllowed = true))
            .isEqualTo(StopFollowUp.NOTIFY)
    }

    @Test
    fun `only logs when notifications are off`() {
        assertThat(StopFollowUp.after(connectionsAtStop = 1, connectionsNow = 1, notificationsAllowed = false))
            .isEqualTo(StopFollowUp.LOG_ONLY)
    }

    @Test
    fun `does not log a missing notification when the service is back`() {
        assertThat(StopFollowUp.after(connectionsAtStop = 1, connectionsNow = 2, notificationsAllowed = false))
            .isEqualTo(StopFollowUp.BACK)
    }
}
