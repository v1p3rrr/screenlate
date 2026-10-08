package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import com.vpr.screenlate.core.common.settings.AppColors
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.overlay.settings.AimMode
import com.vpr.screenlate.overlay.settings.DefinitionCopyMode
import com.vpr.screenlate.overlay.settings.DockSide
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import org.junit.Test

/**
 * Settings store these values by name, and released versions wrote them: renaming one would quietly reset the setting
 * to its default when a newer version is installed over an older one. Add names here; never change or remove them.
 */
class StoredNamesTest {

    private fun <E : Enum<E>> assertStillKnown(entries: List<E>, vararg stored: String) {
        assertThat(entries.map { it.name }).containsAtLeastElementsIn(stored)
    }

    @Test
    fun `names written by released versions still read back`() {
        assertStillKnown(ThemeMode.entries, "SYSTEM", "LIGHT", "DARK")
        assertStillKnown(AppColors.entries, "SCREENLATE", "SYSTEM")
        assertStillKnown(DockSide.entries, "LEFT", "RIGHT", "TOP", "BOTTOM")
        assertStillKnown(AimMode.entries, "ABOVE_FINGER", "BUBBLE_CENTER")
        assertStillKnown(TextSource.entries, "SCREEN", "APP_TEXT", "APP_TEXT_ONLY")
        assertStillKnown(SmallTextMode.entries, "OFF", "ON_DEMAND", "ALWAYS")
        assertStillKnown(OcrEngines.entries, "BOTH", "CLOUD", "DEVICE")
        assertStillKnown(OverwriteMode.entries, "COALESCE", "COALESCE_NEW", "OVERWRITE", "SKIP", "APPEND", "PREPEND")
        assertStillKnown(DuplicateScope.entries, "COLLECTION", "DECK", "DECK_ROOT")
        assertStillKnown(DuplicateBehavior.entries, "PREVENT", "OVERWRITE", "NEW")
        assertStillKnown(
            AudioSourceType.entries,
            "JAPANESE_POD_101", "LANGUAGE_POD_101", "JISHO", "LINGUA_LIBRE", "WIKTIONARY", "TEXT_TO_SPEECH", "URL", "CUSTOM_JSON",
        )
        // The copy mode is stored by its page id.
        assertThat(DefinitionCopyMode.entries.map { it.id }).containsAtLeast("all", "meanings")
    }
}
