package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.app.UiAutomation
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import java.io.File
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Real local TTS and MediaPlayer in the isolated library package; run on a host-silent emulator. */
@RunWith(AndroidJUnit4::class)
class AudioPlayerDeviceTest {
    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun stoppingSynthesizedPronunciationRemovesItsPrivateFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Requires an isolated emulator" }
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val previousEngine = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        fun setEngine(value: String?) {
            automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
            try { check(Settings.Secure.putString(context.contentResolver, "tts_default_synth", value)) }
            finally { automation.dropShellPermissionIdentity() }
        }
        setEngine(context.packageName)
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val playback = PlaybackCoordinator()
        val player = AudioPlayer(context, AudioSettingsRepository(store), playback)
        val capture = checkNotNull(playback.beginCapture())
        val directory = File(context.cacheDir, "tts_playback")
        val liveReady = File(context.cacheDir, SilentTtsService.LIVE_READY)
        val observing = AtomicBoolean(true)
        val observedSizes = mutableListOf<Long>()
        val observer = launch(Dispatchers.IO) {
            var previous = -1L
            while (observing.get()) {
                val size = directory.listFiles()?.maxOfOrNull { it.length() } ?: 0L
                if (size > 0 && size != previous) {
                    observedSizes += size
                    previous = size
                }
                delay(1)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                assertThat(player.play(Pronunciation.Speech(
                    "Sample for the isolated playback test. ".repeat(5), Language.ENGLISH,
                ))).isTrue()
                observing.set(false)
                observer.join()
                val finalBytes = withContext(Dispatchers.IO) {
                    directory.listFiles()?.maxOfOrNull { it.length() } ?: 0L
                }
                val intermediateBytes = checkNotNull(observedSizes.lastOrNull { it > 44 && it < finalBytes })
                Properties().apply {
                    setProperty("intermediateBytes", intermediateBytes.toString())
                    setProperty("finalBytes", finalBytes.toString())
                    setProperty("observedSizes", observedSizes.joinToString(","))
                }.let { metrics ->
                    File(context.filesDir, "tts-transient-metrics.properties").outputStream().use {
                        metrics.store(it, "Observed external-engine WAV writes")
                    }
                }
                player.stop()
            }
            assertThat(withContext(Dispatchers.IO) { directory.listFiles()?.toList().orEmpty() }).isEmpty()
            capture.close()
            withContext(Dispatchers.IO) { liveReady.delete() }
            assertThat(player.play(Pronunciation.Speech("Silent transition fixture", Language.ENGLISH))).isTrue()
            withTimeout(5000) {
                while (!withContext(Dispatchers.IO) { liveReady.exists() }) delay(1)
            }
            val nextCapture = checkNotNull(playback.beginCapture())
            assertThat(playback.requiresPrivateSpeech).isTrue()
            nextCapture.close()
        } finally {
            observing.set(false)
            observer.join()
            try { withContext(Dispatchers.Main) { player.stop() } }
            finally {
                capture.close()
                setEngine(previousEngine)
                withContext(Dispatchers.IO) {
                    directory.listFiles()?.forEach { it.delete() }
                    directory.delete()
                    liveReady.delete()
                }
            }
        }
    }
}
