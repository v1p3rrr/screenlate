package com.vpr.screenlate.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.CompositeOcr
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrUpdate
import com.vpr.screenlate.core.ocr.TextLayout
import com.vpr.screenlate.core.ocr.TextPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One OCR result shown on the debug screen. */
data class OcrRun(
    val engine: OcrEngineType,
    val final: Boolean,
    val elapsedMillis: Long,
    val layout: TextLayout,
    val lensError: String?,
)

data class OcrTestState(
    val image: Bitmap? = null,
    val running: Boolean = false,
    val runs: List<OcrRun> = emptyList(),
    val error: String? = null,
    val hitText: String? = null,
    val hitPosition: TextPosition? = null,
)

@HiltViewModel
class OcrTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocr: CompositeOcr,
) : ViewModel() {

    private val _state = MutableStateFlow(OcrTestState())
    val state: StateFlow<OcrTestState> = _state.asStateFlow()
    private var job: Job? = null

    fun onImagePicked(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            val image = try {
                withContext(Dispatchers.IO) { decode(uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = OcrTestState(error = e.message ?: e.toString())
                return@launch
            }
            _state.value = OcrTestState(image = image, running = true)
            val start = SystemClock.elapsedRealtime()
            ocr.recognize(image, Language.JAPANESE)
                .catch { error -> _state.update { it.copy(running = false, error = error.toString()) } }
                .collect { update ->
                    val run = OcrRun(
                        engine = update.page.engine,
                        final = update is OcrUpdate.Final,
                        elapsedMillis = SystemClock.elapsedRealtime() - start,
                        layout = TextLayout(update.page),
                        lensError = (update as? OcrUpdate.Final)?.lensError?.toString(),
                    )
                    _state.update { it.copy(runs = it.runs + run, running = !run.final) }
                }
        }
    }

    /** Hit-tests the latest result at image coordinates ([x], [y]). */
    fun onImageTapped(x: Float, y: Float) {
        val layout = _state.value.runs.lastOrNull()?.layout ?: return
        val position = layout.hitTest(x, y, tolerance = 16f)
        _state.update {
            it.copy(
                hitPosition = position,
                hitText = position?.let { p -> layout.textFrom(p, 16) },
            )
        }
    }

    /** A camera photo is scaled down to about screen size: at full size it does not fit in memory or on a canvas. */
    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) decoder.setTargetSampleSize((longest + MAX_SIDE - 1) / MAX_SIDE)
        }.copy(Bitmap.Config.ARGB_8888, false)

    private companion object {
        const val MAX_SIDE = 4096
    }
}
