package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainThreadWatchTest {
    private var now = 0L
    private val slow = mutableListOf<Pair<Long, String>>()
    private val watch = MainThreadWatch(thresholdMs = 48, clock = { now }) { ms, what -> slow += ms to what }

    private val dollar = '$'
    private val dispatch =
        ">>>>> Dispatching to Handler (android.view.Choreographer${dollar}FrameHandler) {1a2b3c} " +
            "android.view.Choreographer${dollar}FrameDisplayEventReceiver@4d5e6f: 0"
    private val finished = "<<<<< Finished to Handler (android.view.Choreographer${dollar}FrameHandler) {1a2b3c} null"

    @Test
    fun `reports a message that held the thread past the threshold, without hashes`() {
        watch.println(dispatch)
        now += 60
        watch.println(finished)
        assertThat(slow).containsExactly(
            60L to "android.view.Choreographer${dollar}FrameHandler android.view.Choreographer${dollar}FrameDisplayEventReceiver: 0",
        )
    }

    @Test
    fun `ignores short messages and a finish without a dispatch`() {
        watch.println(dispatch)
        now += 47
        watch.println(finished)
        now += 500
        watch.println(finished)
        assertThat(slow).isEmpty()
    }
}
