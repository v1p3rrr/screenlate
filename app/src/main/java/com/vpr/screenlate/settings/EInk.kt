package com.vpr.screenlate.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EInkViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val overlay: OverlaySettingsRepository,
    private val popup: PopupAppearanceRepository,
) : ViewModel() {
    val eInk: StateFlow<Boolean> = settings.eInk.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The one-time offer on a device that looks like an e-ink reader. */
    val hintVisible: StateFlow<Boolean> = combine(settings.eInk, settings.eInkHintSeen) { on, seen ->
        !on && !seen && EInkDevices.isLikely(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEInk(enabled: Boolean) {
        viewModelScope.launch { settings.setEInk(enabled) }
    }

    fun dismissHint() {
        viewModelScope.launch { settings.setEInkHintSeen() }
    }

    /** Raises the bubble and popup text sizes to the e-ink suggestions; larger sizes stay. */
    fun enlarge() {
        viewModelScope.launch {
            if (overlay.settings.first().bubbleSizeDp < E_INK_BUBBLE_DP) overlay.setBubbleSize(E_INK_BUBBLE_DP)
            val fontSize = popup.appearance.first().fontSize
            val larger = (fontSize + E_INK_FONT_STEP).coerceAtMost(PopupAppearance.MAX_FONT_SIZE)
            popup.setFontSize(larger)
        }
    }

    companion object {
        const val E_INK_BUBBLE_DP = 56
        const val E_INK_FONT_STEP = 2
    }
}

/** Makers of Android e-ink readers and phones; a match only offers e-ink mode, it never turns it on. */
object EInkDevices {
    private val makers = listOf(
        "onyx", "boox", "pocketbook", "bigme", "meebook", "boyue", "likebook", "dasung", "hyread", "mooink", "tolino",
        "moaan", "inkpalm",
    )

    /** Hisense makes both kinds of phones; its e-ink ones are the A series (A5, A7, A9). */
    private val hisenseEInk = Regex("""^(Hisense\s*)?A[579]\b.*""", RegexOption.IGNORE_CASE)

    fun isLikely(manufacturer: String, brand: String, model: String): Boolean {
        val names = listOf(manufacturer, brand, model).map { it.lowercase() }
        if (names.any { name -> makers.any { it in name } }) return true
        val hisense = manufacturer.equals("hisense", ignoreCase = true) || brand.equals("hisense", ignoreCase = true)
        return hisense && hisenseEInk.matches(model.trim())
    }
}

/** The e-ink switch in Appearance; turning it on offers larger sizes. */
@Composable
fun EInkSection(viewModel: EInkViewModel = hiltViewModel()) {
    val eInk by viewModel.eInk.collectAsStateWithLifecycle()
    var offerSizes by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.eink_title)) {
        SwitchRow(
            stringResource(R.string.eink_switch),
            eInk,
            { enabled ->
                viewModel.setEInk(enabled)
                if (enabled) offerSizes = true
            },
            hint = stringResource(R.string.eink_hint),
        )
    }
    if (offerSizes) EnlargeDialog(viewModel) { offerSizes = false }
}

/**
 * Home screen card offered once on devices that look like e-ink readers.
 *
 * @param onOpenAppText opens the bubble settings at "App text only".
 */
@Composable
fun EInkHint(onOpenAppText: () -> Unit, viewModel: EInkViewModel = hiltViewModel()) {
    val visible by viewModel.hintVisible.collectAsStateWithLifecycle()
    var offerSizes by rememberSaveable { mutableStateOf(false) }
    if (visible) {
        SectionCard(title = stringResource(R.string.eink_home_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.eink_home_message))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.setEInk(true)
                        offerSizes = true
                    }) { Text(stringResource(R.string.eink_home_turn_on)) }
                    TextButton(onClick = onOpenAppText) { Text(stringResource(R.string.eink_home_app_text)) }
                    TextButton(onClick = viewModel::dismissHint) { Text(stringResource(R.string.action_dismiss)) }
                }
            }
        }
    }
    if (offerSizes) EnlargeDialog(viewModel) { offerSizes = false }
}

@Composable
private fun EnlargeDialog(viewModel: EInkViewModel, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.eink_enlarge_title)) },
        text = { Text(stringResource(R.string.eink_enlarge_message, EInkViewModel.E_INK_BUBBLE_DP, EInkViewModel.E_INK_FONT_STEP)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.enlarge()
                onDone()
            }) { Text(stringResource(R.string.eink_enlarge_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.eink_enlarge_keep)) } },
    )
}
