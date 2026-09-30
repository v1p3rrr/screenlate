package com.vpr.screenlate.overlay.web

import androidx.annotation.StringRes
import com.vpr.screenlate.dictionary.api.NoTermDictionary
import com.vpr.screenlate.overlay.R

/** What the popup and the search page say when a lookup finds nothing; [missing] tells why there is nothing to search. */
@StringRes
fun noResultsText(missing: NoTermDictionary?): Int = when (missing) {
    null -> R.string.overlay_no_results
    NoTermDictionary.INSTALLING -> R.string.overlay_no_dictionaries
    NoTermDictionary.NONE_ON -> R.string.overlay_no_dictionaries_on
}
