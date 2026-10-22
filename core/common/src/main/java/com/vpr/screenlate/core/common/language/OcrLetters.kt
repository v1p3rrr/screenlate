package com.vpr.screenlate.core.common.language

/**
 * [word] with each character [letters] lists written as the letter it stands for: one character for one, so character
 * boxes stay valid.
 */
internal fun replaceLetters(word: String, letters: Map<Char, Char>): String =
    if (word.none { it in letters }) word else String(CharArray(word.length) { letters[word[it]] ?: word[it] })
