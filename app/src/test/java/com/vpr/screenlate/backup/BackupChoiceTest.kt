package com.vpr.screenlate.backup

import androidx.compose.ui.state.ToggleableState
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupChoiceTest {
    private val files = listOf("a", "b", "c")

    @Test
    fun `a file not seen before takes the item's mark`() {
        val choice = FileChoice(on = true).toggle("a", files)
        assertThat(choice.chosen(files + "new")).containsExactly("b", "c", "new")
        assertThat(FileChoice(on = false).toggle("a", files).chosen(files + "new")).containsExactly("a")
    }

    @Test
    fun `the item shows all, none or some of its files`() {
        assertThat(FileChoice(on = true).state(files)).isEqualTo(ToggleableState.On)
        assertThat(FileChoice(on = false).state(files)).isEqualTo(ToggleableState.Off)
        assertThat(FileChoice(on = true).toggle("b", files).state(files)).isEqualTo(ToggleableState.Indeterminate)
    }

    @Test
    fun `marking every file otherwise turns the item over`() {
        var choice = FileChoice(on = true)
        files.forEach { choice = choice.toggle(it, files) }
        assertThat(choice).isEqualTo(FileChoice(on = false))
        assertThat(choice.chosen(files + "new")).isEmpty()
    }

    @Test
    fun `the item's box marks all, or clears all when all are on`() {
        val some = FileChoice(on = false).toggle("a", files)
        assertThat(some.toggleAll(files)).isEqualTo(FileChoice(on = true))
        assertThat(FileChoice(on = true).toggleAll(files)).isEqualTo(FileChoice(on = false))
    }

    @Test
    fun `files gone from the list are forgotten`() {
        val choice = FileChoice(on = true, flipped = setOf("gone")).toggle("a", files)
        assertThat(choice.flipped).containsExactly("a")
    }

    @Test
    fun `sections follow the boxes and the chosen files`() {
        val choice = BackupChoice(off = setOf(BackupSection.ANKI))
        val sections = choice.sections(BackupSection.entries, fontFiles = files, dictionaryFiles = files)
        assertThat(sections).doesNotContain(BackupSection.ANKI)
        assertThat(sections).contains(BackupSection.FONTS)
        assertThat(sections).doesNotContain(BackupSection.DICTIONARY_FILES)
        assertThat(choice.sections(BackupSection.entries, fontFiles = emptyList(), dictionaryFiles = files))
            .doesNotContain(BackupSection.FONTS)
        assertThat(BackupChoice.ALL.sections(listOf(BackupSection.POPUP, BackupSection.DICTIONARY_FILES), files, files))
            .containsExactly(BackupSection.POPUP, BackupSection.DICTIONARY_FILES).inOrder()
    }

    @Test
    fun `languages are offered when there are several or one has models`() {
        val japanese = BackupFile("ja", "Japanese", null)
        val english = BackupFile("en", "English", null)
        val choice = BackupChoice(languages = FileChoice(on = true, flipped = setOf("ja")))
        // A single language without models is always taken, whatever an earlier choice said.
        assertThat(choice.offersLanguages(listOf(japanese))).isFalse()
        assertThat(choice.chosenLanguages(listOf(japanese))).containsExactly("ja")
        assertThat(choice.offersLanguages(listOf(japanese.copy(bytes = 10)))).isTrue()
        assertThat(choice.chosenLanguages(listOf(japanese.copy(bytes = 10)))).isEmpty()
        assertThat(choice.chosenLanguages(listOf(japanese, english))).containsExactly("en")
    }

    @Test
    fun `files of a language left out are not offered`() {
        val languages = listOf(BackupFile("ja", "Japanese", 10), BackupFile("en", "English", null))
        val files = listOf(BackupFile("a", "A", 1, "ja"), BackupFile("b", "B", 1, "en"), BackupFile("c", "C", 1, null))
        val withoutJapanese = BackupChoice(languages = FileChoice(on = true).toggle("ja", listOf("ja", "en")))
        assertThat(withoutJapanese.visible(files, languages).map { it.key }).containsExactly("b", "c").inOrder()
        assertThat(withoutJapanese.takesModels(languages)).isFalse()
        assertThat(BackupChoice().visible(files, languages).map { it.key }).containsExactly("a", "b", "c").inOrder()
        assertThat(BackupChoice().takesModels(languages)).isTrue()
    }

    @Test
    fun `croatian or serbian takes the shared dictionaries`() {
        val languages = listOf(BackupFile("hr", "Croatian", 10), BackupFile("sr", "Serbian", 10))
        val files = listOf(BackupFile("a", "A", 1, "sh"), BackupFile("b", "B", 1, "ru"))
        val croatianOnly = BackupChoice(languages = FileChoice(on = true).toggle("sr", languages.map { it.key }))
        assertThat(croatianOnly.chosenLanguages(languages)).containsExactly("hr", "sh")
        assertThat(croatianOnly.visible(files, languages).map { it.key }).containsExactly("a")
        val none = BackupChoice(languages = FileChoice(on = false))
        assertThat(none.visible(files, languages)).isEmpty()
    }

    @Test
    fun `slovak takes the czech dictionaries it falls back to`() {
        val languages = listOf(BackupFile("sk", "Slovak", 10), BackupFile("ja", "Japanese", 10))
        val files = listOf(BackupFile("a", "A", 1, "sk"), BackupFile("b", "B", 1, "cs"), BackupFile("c", "C", 1, "ja"))
        val slovakOnly = BackupChoice(languages = FileChoice(on = true).toggle("ja", languages.map { it.key }))
        assertThat(slovakOnly.chosenLanguages(languages)).containsExactly("sk", "cs")
        assertThat(slovakOnly.visible(files, languages).map { it.key }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `the first choice has settings and fonts but no dictionaries`() {
        val first = BackupChoiceStore.read(mutablePreferencesOf())
        assertThat(first).isEqualTo(BackupChoice(fonts = FileChoice(on = true), dictionaries = FileChoice(on = false)))
        val stored = mutablePreferencesOf(
            stringSetPreferencesKey("backup_sections_off") to setOf("ANKI", "REMOVED_SECTION"),
            booleanPreferencesKey("backup_dictionaries_on") to true,
            stringSetPreferencesKey("backup_dictionaries_flipped") to setOf("JMdict"),
            stringSetPreferencesKey("backup_languages_flipped") to setOf("en"),
        )
        assertThat(BackupChoiceStore.read(stored)).isEqualTo(
            BackupChoice(
                off = setOf(BackupSection.ANKI),
                dictionaries = FileChoice(on = true, flipped = setOf("JMdict")),
                languages = FileChoice(on = true, flipped = setOf("en")),
            ),
        )
    }
}
