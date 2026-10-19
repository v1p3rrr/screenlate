package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.sync.Mutex
import org.junit.Test

class DeferredReleaseTest {
    private val mutex = Mutex()
    private var closes = 0
    private val release = DeferredRelease(mutex) { closes++ }

    @Test
    fun `a release with the mutex free closes at once`() {
        release.request()
        assertThat(closes).isEqualTo(1)
        assertThat(mutex.isLocked).isFalse()
        // Nothing is left to close.
        release.afterHold()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun `a release while the mutex is held closes once the holder lets it go`() {
        assertThat(mutex.tryLock()).isTrue()
        release.request()
        assertThat(closes).isEqualTo(0)

        mutex.unlock()
        release.afterHold()

        assertThat(closes).isEqualTo(1)
        release.afterHold()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun `a holder that ends without a release closes nothing`() {
        assertThat(mutex.tryLock()).isTrue()
        mutex.unlock()
        release.afterHold()
        assertThat(closes).isEqualTo(0)
    }
}
