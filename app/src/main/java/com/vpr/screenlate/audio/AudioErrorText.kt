package com.vpr.screenlate.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.audio.AudioError
import com.vpr.screenlate.core.anki.audio.AudioSource

/**
 * A readable phrase for an error of [source], followed by its type, e.g. "The server refused the connection or is not
 * running (ConnectException)". A source on the local network without the permission for it says so instead.
 */
@Composable
fun audioErrorText(error: AudioError, source: AudioSource): String {
    val phrase = when {
        localNetworkMissing(LocalContext.current, source.url) -> R.string.audio_error_local_network
        else -> when (error.kind) {
            AudioError.Kind.HTTP_STATUS -> R.string.audio_error_http
            AudioError.Kind.NOT_FOUND -> R.string.audio_error_not_found
            AudioError.Kind.REFUSED -> R.string.audio_error_refused
            AudioError.Kind.TIMEOUT -> R.string.audio_error_timeout
            AudioError.Kind.SECURE_CONNECTION -> R.string.audio_error_secure
            AudioError.Kind.NOT_A_LIST -> R.string.audio_error_not_a_list
            AudioError.Kind.SOURCE_LIST -> R.string.audio_error_source_list
            AudioError.Kind.OTHER -> R.string.audio_error_other
        }
    }
    return stringResource(R.string.audio_error_format, stringResource(phrase), error.type)
}
