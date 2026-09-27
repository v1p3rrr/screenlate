package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class JapaneseInflectionsTest {

    /** Rule names as hoshidicts declares them, so an engine update with new or renamed rules fails here. */
    private val engineNames: List<String> =
        Regex("""\.name = "([^"]+)"""")
            .findAll(File("src/main/cpp/hoshidicts/src/deinflector.cpp").readText())
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `every engine rule has strings and no stale ones are left`() {
        assertThat(engineNames).isNotEmpty()
        assertThat(JapaneseInflections.names).containsExactlyElementsIn(engineNames)
    }
}
