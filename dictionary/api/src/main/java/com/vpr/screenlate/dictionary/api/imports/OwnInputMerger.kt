package com.vpr.screenlate.dictionary.api.imports

import androidx.work.Data
import androidx.work.InputMerger

/**
 * Gives an import task only its own input. WorkManager's default merger lays the outputs of the task before it in its
 * chain over that input, and the output of a stopped download carries its URL, archive, name and catalog entry for
 * Resume: the task queued after it would download that one. WorkManager passes the task's own input first.
 */
class OwnInputMerger : InputMerger() {
    override fun merge(inputs: List<Data>): Data = inputs.firstOrNull() ?: Data.EMPTY
}
