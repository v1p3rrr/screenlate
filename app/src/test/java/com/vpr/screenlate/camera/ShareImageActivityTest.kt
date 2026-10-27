package com.vpr.screenlate.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareImageActivityTest {
    private val app = "com.vpr.screenlate.debug"

    @Test
    fun anotherAppsContentLinkIsAccepted() {
        assertThat(sharedFromAnotherApp("content", "media", app)).isTrue()
        assertThat(sharedFromAnotherApp("content", "com.android.externalstorage.documents", app)).isTrue()
        assertThat(sharedFromAnotherApp("content", "com.vpr.screenlate.debugger", app)).isTrue()
    }

    @Test
    fun theAppsOwnProvidersAreRejected() {
        assertThat(sharedFromAnotherApp("content", "$app.logs", app)).isFalse()
        assertThat(sharedFromAnotherApp("content", "$app.anki.media", app)).isFalse()
        assertThat(sharedFromAnotherApp("content", app, app)).isFalse()
    }

    @Test
    fun aUserBeforeTheProviderDoesNotHideTheApp() {
        assertThat(sharedFromAnotherApp("content", "0@$app.anki.media", app)).isFalse()
        assertThat(sharedFromAnotherApp("content", "10@$app", app)).isFalse()
        assertThat(sharedFromAnotherApp("content", "a@0@$app.logs", app)).isFalse()
        assertThat(sharedFromAnotherApp("content", "0@", app)).isFalse()
        // An image shared from another user profile, such as a work profile.
        assertThat(sharedFromAnotherApp("content", "10@media", app)).isTrue()
    }

    @Test
    fun fileLinksAndLinksWithoutAProviderAreRejected() {
        assertThat(sharedFromAnotherApp("file", null, app)).isFalse()
        assertThat(sharedFromAnotherApp("content", null, app)).isFalse()
        assertThat(sharedFromAnotherApp(null, "media", app)).isFalse()
    }
}
