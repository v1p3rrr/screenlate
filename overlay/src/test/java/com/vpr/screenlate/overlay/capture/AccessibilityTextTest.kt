package com.vpr.screenlate.overlay.capture

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class AccessibilityTextTest {

    private fun row(count: Int) = (0 until count).map { Box(it * 30f, 100f, it * 30f + 30f, 140f) }

    @Test
    fun `characters with boxes of their own are usable`() {
        assertThat(sharesBoxes(row(12))).isFalse()
        assertThat(sharesBoxes(row(1))).isFalse()
    }

    @Test
    fun `the node's bounds repeated for every character are not`() {
        val node = Box(40f, 1000f, 1220f, 1360f)
        assertThat(sharesBoxes(List(30) { node })).isTrue()
        assertThat(sharesBoxes(List(2) { node })).isTrue()
    }

    @Test
    fun `a few characters sharing a box do not reject the text`() {
        // A combining mark may report its base character's box.
        val boxes = row(10).toMutableList().apply { add(3, this[3]) }
        assertThat(sharesBoxes(boxes)).isFalse()
    }

    @Test
    fun `a field whose first character sits at its corner is misplaced`() {
        val field = Box(48f, 372f, 1232f, 540f)
        // The glyph starts inside the padding, at about (96, 426); the box reports the field's corner instead.
        assertThat(startsAtCorner(field, Box(48f, 372f, 64.75f, 444f))).isTrue()
        assertThat(startsAtCorner(field, Box(96f, 426f, 112.75f, 498f))).isFalse()
        assertThat(startsAtCorner(field, Box(48f, 426f, 64.75f, 498f))).isFalse()
    }

    /** The lines' words: a line holds one. */
    private fun appTextWords(characters: List<Pair<String, Box?>>, separator: String) =
        appTextLines(characters, separator).map { it.words.single() }

    /** [text] with a box per character along one row, null for characters in [hidden] (indices). */
    private fun characters(text: String, top: Float = 100f, hidden: Set<Int> = emptySet()): List<Pair<String, Box?>> =
        text.mapIndexed { index, c ->
            c.toString() to Box(index * 30f, top, index * 30f + 30f, top + 40f).takeIf { index !in hidden }
        }

    @Test
    fun `a line wrapped at a space or a line break keeps the words apart`() {
        // "Hello world" wrapped after the space, which has no location; then a line break without one.
        val text = characters("Hello ", hidden = setOf(5)) + characters("world", top = 150f) +
            characters("\n", hidden = setOf(0)) + characters("again", top = 200f)

        val lines = appTextWords(text, " ")

        assertThat(lines.map { it.text }).containsExactly("Hello", "world", "again").inOrder()
        assertThat(lines.map { it.separator }).containsExactly(" ", " ", "").inOrder()
        assertThat(lines.joinToString("") { it.text + it.separator }).isEqualTo("Hello world again")
    }

    @Test
    fun `japanese lines join without a separator as before`() {
        val text = characters("今日は") + characters("\n", hidden = setOf(0)) + characters("晴れ", top = 150f)

        val lines = appTextWords(text, "")

        assertThat(lines.joinToString("") { it.text + it.separator }).isEqualTo("今日は晴れ")
    }

    @Test
    fun `a wrap without a space and a hidden letter join the parts`() {
        // A long word wrapped by the layout, and an emoji without a location.
        val wrapped = characters("Donaudampf") + characters("schiff", top = 150f)
        assertThat(appTextWords(wrapped, " ").map { it.separator }).containsExactly("", "").inOrder()
        val emoji = characters("ab") + listOf<Pair<String, Box?>>("😀" to null) + characters("cd", top = 150f)
        assertThat(appTextWords(emoji, " ").map { it.separator }).containsExactly("", "").inOrder()
        // Spaces without a location before any text give no separator.
        val leading = listOf<Pair<String, Box?>>(" " to null) + characters("ab") + characters("cd", top = 150f)
        assertThat(appTextWords(leading, " ").map { it.separator }).containsExactly("", "").inOrder()
    }

    @Test
    fun `a combining mark without a box stays with its letter`() {
        // A text view gives a stress or tone mark typed apart an empty box.
        val stressed = characters("ко́шка", hidden = setOf(2))
        val russian = appTextWords(stressed, " ")
        assertThat(russian.map { it.text }).containsExactly("ко́шка")
        assertThat(russian[0].characterBoxes!![2]).isEqualTo(russian[0].characterBoxes!![1])
        val vietnamese = appTextWords(characters("hoà bình", hidden = setOf(3)), " ")
        assertThat(vietnamese.joinToString("") { it.text + it.separator }).isEqualTo("hoà bình")
        // A mark after a character without a box, or at the start, has nothing to stay with.
        val afterHidden = characters("ab́cd", hidden = setOf(1, 2))
        assertThat(appTextWords(afterHidden, " ").map { it.text }).containsExactly("a", "cd").inOrder()
        assertThat(appTextWords(characters("́ab", hidden = setOf(0)), " ").map { it.text }).containsExactly("ab")
    }

    @Test
    fun `boxes that marks take from their letters are not shared boxes`() {
        // "Được" decomposed: Đ u, horn, o, horn, dot below, c; the marks have no box of their own.
        val word = "Đ" + "u" + "\u031B" + "o" + "\u031B" + "\u0323" + "c"
        val characters = characters(word, hidden = setOf(2, 4, 5))
        val lines = appTextWords(characters, " ")

        assertThat(lines.map { it.text }).containsExactly(word)
        assertThat(sharesBoxes(lines.flatMap { it.characterBoxes!! })).isTrue()
        assertThat(sharesBoxes(characters.mapNotNull { it.second })).isFalse()
    }

    @Test
    fun `each line keeps its character boxes`() {
        val lines = appTextWords(characters("ab cd", hidden = setOf(2)), " ")
        assertThat(lines.map { it.characterBoxes!!.size }).containsExactly(2, 2).inOrder()
        assertThat(lines[1].box).isEqualTo(Box(90f, 100f, 150f, 140f))
    }

    /** [text] laid out from right to left, a box per character from [right] leftwards. */
    private fun rightToLeft(text: String, right: Float, top: Float = 100f): List<Pair<String, Box?>> =
        text.mapIndexed { index, c -> c.toString() to Box(right - index * 30f - 30f, top, right - index * 30f, top + 40f) }

    /** [text] laid out from left to right from [left]. */
    private fun leftToRight(text: String, left: Float, top: Float = 100f): List<Pair<String, Box?>> =
        text.mapIndexed { index, c -> c.toString() to Box(left + index * 30f, top, left + index * 30f + 30f, top + 40f) }

    @Test
    fun `an arabic paragraph runs from right to left, its latin run and number inside one line`() {
        // "اشتريت iPhone 15 الآن": the Latin run and its number left to right, between the Arabic words.
        val text = rightToLeft("اشتريت ", right = 900f) + leftToRight("iPhone 15", left = 420f) + rightToLeft(" الآن", right = 420f)

        val lines = appTextLines(text, " ")

        assertThat(lines.map { it.text }).containsExactly("اشتريت iPhone 15 الآن")
        assertThat(lines.single().rightToLeft).isTrue()
        // A wrap starts again at the right, below.
        val wrapped = rightToLeft("مرحبا ", right = 900f) + rightToLeft("بكم", right = 900f, top = 150f)
        assertThat(appTextLines(wrapped, " ").map { it.text }).containsExactly("مرحبا", "بكم").inOrder()
        // An Arabic letter that starts back to the right ends the line even on the same row.
        val columns = rightToLeft("مرحبا", right = 900f) + rightToLeft("بكم", right = 900f)
        assertThat(appTextLines(columns, " ").map { it.text }).containsExactly("مرحبا", "بكم").inOrder()
    }

    @Test
    fun `an arabic word in an english line stays in it`() {
        val text = leftToRight("the ", left = 0f) + rightToLeft("كتاب", right = 240f) + leftToRight(" means book", left = 240f)

        val lines = appTextLines(text, " ")

        assertThat(lines.map { it.text }).containsExactly("the كتاب means book")
        assertThat(lines.single().rightToLeft).isFalse()
        // A Latin letter back at the left still ends an English line.
        val columns = leftToRight("ab", left = 0f) + leftToRight("cd", left = 0f)
        assertThat(appTextLines(columns, " ").map { it.text }).containsExactly("ab", "cd").inOrder()
    }

    @Test
    fun `each paragraph takes the direction of its first letter`() {
        val text = rightToLeft("شكرا", right = 900f) + listOf<Pair<String, Box?>>("\n" to null) +
            leftToRight("Thanks", left = 0f, top = 150f) + listOf<Pair<String, Box?>>("\n" to null) +
            rightToLeft("15 كتابا", right = 900f, top = 200f)

        assertThat(appTextLines(text, " ").map { it.rightToLeft }).containsExactly(true, false, true).inOrder()
    }
}
