package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SerboCroatianScriptsTest {

    private fun cyrillic(text: String) = SerboCroatianScripts.toCyrillic(text)

    private fun latin(text: String) = SerboCroatianScripts.toLatin(text)

    @Test
    fun `latin letters and digraphs become one cyrillic letter each`() {
        assertThat(cyrillic("kuća")).isEqualTo("кућа")
        assertThat(cyrillic("ljubav")).isEqualTo("љубав")
        assertThat(cyrillic("njegov")).isEqualTo("његов")
        assertThat(cyrillic("džep")).isEqualTo("џеп")
        assertThat(cyrillic("Đurđevdan")).isEqualTo("Ђурђевдан")
        assertThat(cyrillic("čačak šuma žaba")).isEqualTo("чачак шума жаба")
    }

    @Test
    fun `capitals and the one-letter digraphs keep their case`() {
        assertThat(cyrillic("Ljubljana")).isEqualTo("Љубљана")
        assertThat(cyrillic("LJUBAV")).isEqualTo("ЉУБАВ")
        assertThat(cyrillic("Njemačka")).isEqualTo("Њемачка")
        assertThat(cyrillic("\u01c5ep")).isEqualTo("Џеп")
        assertThat(cyrillic("\u01c6ep")).isEqualTo("џеп")
        assertThat(cyrillic("\u01c9ubav")).isEqualTo("љубав")
    }

    @Test
    fun `marks, cyrillic letters and other characters stay`() {
        assertThat(cyrillic("ku\u030fća")).isEqualTo("ку\u030fћа")
        assertThat(cyrillic("\u043a\u0443\u045ba")).isEqualTo("кућа")
        assertThat(cyrillic("5. maja")).isEqualTo("5. маја")
    }

    @Test
    fun `text without a latin letter or with one outside the alphabet gives nothing`() {
        assertThat(cyrillic("кућа")).isNull()
        assertThat(cyrillic("1918.")).isNull()
        assertThat(cyrillic("wifi")).isNull()
        assertThat(cyrillic("Xbox")).isNull()
        assertThat(cyrillic("yoga")).isNull()
        assertThat(cyrillic("Mädchen")).isNull()
    }

    @Test
    fun `a digraph across a prefix comes out as one letter, as in any letter-by-letter conversion`() {
        assertThat(cyrillic("konjugacija")).isEqualTo("коњугација")
    }

    @Test
    fun `cyrillic letters become latin ones, the digraph letters two`() {
        assertThat(latin("кућа")).isEqualTo("kuća")
        assertThat(latin("љубав њега џеп")).isEqualTo("ljubav njega džep")
        assertThat(latin("Ђурђевдан")).isEqualTo("Đurđevdan")
        assertThat(latin("ку\u030fћа")).isEqualTo("ku\u030fća")
        assertThat(latin("5. маја")).isEqualTo("5. maja")
        assertThat(latin("kuћа")).isEqualTo("kuća")
    }

    @Test
    fun `a capital digraph letter is all capitals only before another capital`() {
        assertThat(latin("Љубљана")).isEqualTo("Ljubljana")
        assertThat(latin("ЉУБАВ")).isEqualTo("LJUBAV")
        assertThat(latin("Џ.")).isEqualTo("Dž.")
        assertThat(latin("Њ")).isEqualTo("Nj")
    }

    @Test
    fun `text without a cyrillic letter or with one outside the alphabet gives nothing back`() {
        assertThat(latin("kuća")).isNull()
        assertThat(latin("1918.")).isNull()
        assertThat(latin("мы")).isNull()
        assertThat(latin("її")).isNull()
    }
}
