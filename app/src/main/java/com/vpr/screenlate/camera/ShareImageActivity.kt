package com.vpr.screenlate.camera

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.core.content.IntentSanitizer
import com.vpr.screenlate.overlay.OverlayIntents

/**
 * "Share → Screenlate" for an image. The share sheet starts it in the sending app's task; it passes the image, with
 * read access, to the main screen in the app's own task, which opens it on the Camera page, and closes.
 */
class ShareImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Any app can start this activity, so only another app's content link goes on: a link to the app's own
        // providers or files would make it read its own data for the sender.
        val shared = IntentSanitizer.Builder()
            .allowComponent(ComponentName(this, ShareImageActivity::class.java))
            .allowAction(Intent.ACTION_SEND)
            .allowType { it.startsWith("image/") }
            .allowFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // The sanitizer keeps a stream only with this flag.
            .allowExtraStream { sharedFromAnotherApp(it.scheme, it.authority, packageName) }
            .allowClipDataUri { sharedFromAnotherApp(it.scheme, it.authority, packageName) }
            .build()
            .sanitizeByFiltering(intent)
        val uri = IntentCompat.getParcelableExtra(shared, Intent.EXTRA_STREAM, Uri::class.java)
            ?: shared.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            // Without a grant of its own the image cannot be passed on with one; the Camera page then says it
            // cannot open it, unless the app may read it anyway.
            val granted = checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
            OverlayIntents.openScreen(this, OPEN_SHARED_IMAGE) {
                clipData = ClipData.newRawUri(null, uri)
                if (granted) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        finish()
    }

    companion object {
        /** The `OverlayIntents.EXTRA_OPEN` value of an image shared to the app, carried in the intent's clip data. */
        const val OPEN_SHARED_IMAGE = "shared-image"
    }
}

/** A shared link is a `content:` link of a provider outside the app [packageName]. */
internal fun sharedFromAnotherApp(scheme: String?, authority: String?, packageName: String): Boolean =
    scheme == ContentResolver.SCHEME_CONTENT && authority != null &&
        authority != packageName && !authority.startsWith("$packageName.")
