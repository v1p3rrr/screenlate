package com.vpr.screenlate.settings

import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.EInkEnlargement
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The bubble and popup text sizes that e-ink mode's "Make larger" changes, and their way back when the mode goes off,
 * whether by the switch or by a restored backup.
 */
@Singleton
class EInkSizes @Inject constructor(
    private val settings: AppSettingsRepository,
    private val overlay: OverlaySettingsRepository,
    private val popup: PopupAppearanceRepository,
) {
    /** Raises the bubble and popup text sizes to the e-ink suggestions and remembers the change; larger sizes stay. */
    suspend fun enlarge() {
        val enlargement = EInkEnlargement.of(
            bubbleNow = overlay.settings.first().bubbleSizeDp,
            fontNow = popup.appearance.first().fontSize,
            minBubble = BUBBLE_DP,
            fontStep = FONT_STEP,
            maxFont = PopupAppearance.MAX_FONT_SIZE,
        )
        enlargement.bubble?.let { overlay.setBubbleSize(it.after) }
        enlargement.font?.let { popup.setFontSize(it.after) }
        settings.setEInkEnlargement(enlargement)
    }

    /** Brings back the sizes [enlarge] changed, unless they were changed since, and forgets the change. */
    suspend fun restore() {
        val enlargement = settings.eInkEnlargement.first() ?: return
        val restore = enlargement.restore(overlay.settings.first().bubbleSizeDp, popup.appearance.first().fontSize)
        restore.bubble?.let { overlay.setBubbleSize(it) }
        restore.font?.let { popup.setFontSize(it) }
        settings.setEInkEnlargement(null)
    }

    /** Forgets an earlier change, so only an enlargement made in this e-ink session is undone. */
    suspend fun forget() {
        settings.setEInkEnlargement(null)
    }

    companion object {
        const val BUBBLE_DP = 56
        const val FONT_STEP = 2
    }
}
