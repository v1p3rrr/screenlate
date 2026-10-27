package com.vpr.screenlate.camera

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.PhotoTiles
import com.vpr.screenlate.core.ocr.model.DeviceOcr
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoRecognitionJobs
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.overlay.install.InstallationStatus
import com.vpr.screenlate.overlay.install.LanguageInstallation
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What keeps a language from recognizing a photo: its dictionary comes first, then the on-device model. */
enum class LanguageGap { DICTIONARY, MODEL }

internal fun InstallationStatus.gap(): LanguageGap? = when {
    !dictionaryReady -> LanguageGap.DICTIONARY
    !scanReady -> LanguageGap.MODEL
    else -> null
}

/** [active] is the app's active language; [flags] and [gaps] cover the selectable [languages]. */
data class CameraRecognitionState(
    val language: Language,
    val languages: List<Language>,
    val settings: PhotoOcrSettings,
    val installation: InstallationStatus,
    val work: PhotoWork?,
    val active: Language = language,
    val flags: Map<Language, String> = emptyMap(),
    val gaps: Map<Language, LanguageGap> = emptyMap(),
) {
    fun flagCountry(language: Language): String = flags[language] ?: language.support.flagCountries.first()

    /**
     * Why the current result no longer matches the selection, or null when it does or there is none. A photo read by
     * tiles stays as it is when High accuracy is off; one read whole is pending when it is on, unless the photo is a
     * single tile anyway.
     */
    fun pendingReason(document: PhotoDocument): CameraPending? {
        val previous = (document.text as? PhotoText.Recognized)?.recognition
        val attempt = document.attempt
        val (language, settings) = when {
            previous != null -> previous.language to previous.settings
            attempt != null -> attempt.language to attempt.settings
            else -> return null
        }
        val single = PhotoTiles.overview(document.width, document.height) == null
        return when {
            language != this.language -> CameraPending.LANGUAGE
            settings.copy(tiled = this.settings.tiled) != this.settings -> CameraPending.SETTINGS
            this.settings.tiled && !settings.tiled && !single -> CameraPending.SETTINGS
            else -> null
        }
    }

    /** Recognize more accurately: the photo was read whole and is larger than one tile. */
    fun canReadByTiles(document: PhotoDocument): Boolean {
        val recognition = (document.text as? PhotoText.Recognized)?.recognition ?: return false
        return !recognition.settings.tiled && PhotoTiles.overview(document.width, document.height) != null
    }

    fun pending(document: PhotoDocument): Boolean = pendingReason(document) != null
}

enum class CameraPending { LANGUAGE, SETTINGS }

data class CameraReadinessBlock(val documentId: Long, val status: InstallationStatus)

interface CameraRecognition {
    val state: StateFlow<CameraRecognitionState?>
    val blocked: StateFlow<CameraReadinessBlock?>
    fun select(language: Language)

    /** Recognizes [document] with the selection; [byTiles] reads it by tiles whatever High accuracy says. */
    fun recognize(document: PhotoDocument, byTiles: Boolean = false)
    fun retry(document: PhotoDocument)
    fun dismissBlock()
}

/** App settings/readiness adapter; storage and work stay independent of overlay and navigation. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CameraRecognitionController @Inject constructor(
    private val preferences: CameraPreferences,
    private val installation: LanguageInstallation,
    settings: OverlaySettingsRepository,
    device: DeviceOcr,
    private val jobs: PhotoRecognitionJobs,
) : CameraRecognition {
    // Reading readiness or a setting can fail; Recognize then does nothing instead of ending the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        Log.w("Camera", "Recognition request failed: " + e.javaClass.simpleName)
    })
    private val mutableBlock = MutableStateFlow<CameraReadinessBlock?>(null)
    override val blocked = mutableBlock.asStateFlow()
    private var action: Job? = null

    override val state: StateFlow<CameraRecognitionState?> = preferences.languages.flatMapLatest { language ->
        val gaps = if (language.enabled.isEmpty()) flowOf(emptyMap()) else {
            combine(language.enabled.map { installation.observe(it, imageSource = true) }) { statuses ->
                statuses.mapNotNull { status -> status.gap()?.let { status.language to it } }.toMap()
            }
        }
        val engines = combine(settings.settings, preferences.highAccuracy) { current, tiled -> current.ocrEngines to tiled }
        combine(engines, device.activeUpdates(language.selected), installation.observe(language.selected, imageSource = true),
            jobs.work, gaps) { (ocrEngines, tiled), model, status, work, gapsByLanguage ->
            CameraRecognitionState(language.selected, language.enabled,
                PhotoOcrSettings(ocrEngines, if (ocrEngines == OcrEngines.CLOUD) null else model?.id, tiled), status, work,
                language.active, language.flags, gapsByLanguage)
        }
    }.retryWhen { error, attempt ->
        if (attempt == 0L) Log.w("Camera", "Recognition settings unavailable: " + error.javaClass.simpleName)
        delay(1000)
        true // Retry only observing local settings/readiness. This never starts OCR.
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun select(language: Language) {
        scope.launch {
            try { preferences.select(language) }
            catch (e: Exception) { Log.w("Camera", "Language change failed: " + e.javaClass.simpleName) }
        }
    }

    @Synchronized
    override fun recognize(document: PhotoDocument, byTiles: Boolean) {
        action?.cancel()
        action = scope.launch {
            val current = state.filterNotNull().first()
            val ready = installation.observe(current.language, imageSource = true, engines = current.settings.engines).first()
            val settings = if (byTiles) current.settings.copy(tiled = true) else current.settings
            if (!ready.scanReady) mutableBlock.value = CameraReadinessBlock(document.id, ready)
            else { mutableBlock.value = null; jobs.recognize(document, current.language, settings) }
        }
    }

    @Synchronized
    override fun retry(document: PhotoDocument) {
        action?.cancel()
        action = scope.launch {
            val attempt = document.attempt
            val previous = (document.text as? PhotoText.Recognized)?.recognition
            val language = attempt?.language ?: previous?.language ?: return@launch
            val engines = attempt?.settings?.engines ?: previous?.settings?.engines ?: return@launch
            val ready = installation.observe(language, imageSource = true, engines = engines).first()
            if (!ready.scanReady) mutableBlock.value = CameraReadinessBlock(document.id, ready)
            else { mutableBlock.value = null; jobs.retry(document) }
        }
    }

    override fun dismissBlock() { mutableBlock.value = null }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraRecognitionModule {
    @Binds abstract fun bindRecognition(controller: CameraRecognitionController): CameraRecognition
}
