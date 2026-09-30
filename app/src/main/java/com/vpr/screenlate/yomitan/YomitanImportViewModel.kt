package com.vpr.screenlate.yomitan

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsImportState {
    data object Idle : SettingsImportState

    data object Reading : SettingsImportState

    data object NotSettings : SettingsImportState

    /** Applying stopped with an error; sections applied before it stay. */
    data object Failed : SettingsImportState

    data class Loaded(
        val settings: YomitanSettings,
        val profile: Int,
        val sections: Set<YomitanSection> = YomitanSection.entries.toSet(),
        val applying: Boolean = false,
    ) : SettingsImportState

    data class Done(val profile: String, val summary: ImportSummary) : SettingsImportState
}

@HiltViewModel
class YomitanImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: YomitanSettingsImporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SettingsImportState>(SettingsImportState.Idle)
    val state: StateFlow<SettingsImportState> = mutableState
    private var loadJob: Job? = null

    fun load(uri: Uri) {
        loadJob?.cancel()
        mutableState.value = SettingsImportState.Reading
        loadJob = viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use(::readLimited)
                    text?.let(YomitanSettings::parse)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            mutableState.value = settings?.let { SettingsImportState.Loaded(it, it.currentProfile) }
                ?: SettingsImportState.NotSettings
        }
    }

    fun selectProfile(index: Int) = updateLoaded { it.copy(profile = index) }

    fun toggleSection(section: YomitanSection) = updateLoaded {
        it.copy(sections = if (section in it.sections) it.sections - section else it.sections + section)
    }

    fun apply() {
        val loaded = mutableState.value as? SettingsImportState.Loaded ?: return
        if (loaded.applying || loaded.sections.isEmpty()) return
        mutableState.value = loaded.copy(applying = true)
        val profile = loaded.settings.profiles[loaded.profile]
        viewModelScope.launch {
            mutableState.value = try {
                SettingsImportState.Done(profile.name, importer.apply(profile, loaded.sections))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Importing Yomitan settings failed", e.redacted())
                SettingsImportState.Failed
            }
        }
    }

    /** The file's text; null when it is far larger than any settings export (a dictionary picked by mistake). */
    private fun readLimited(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_SETTINGS_BYTES) return null
        }
        return out.toByteArray().decodeToString()
    }

    fun reset() {
        mutableState.value = SettingsImportState.Idle
    }

    private fun updateLoaded(transform: (SettingsImportState.Loaded) -> SettingsImportState.Loaded) {
        (mutableState.value as? SettingsImportState.Loaded)?.let { mutableState.value = transform(it) }
    }
}

private const val TAG = "YomitanImport"
private const val MAX_SETTINGS_BYTES = 16 * 1024 * 1024
