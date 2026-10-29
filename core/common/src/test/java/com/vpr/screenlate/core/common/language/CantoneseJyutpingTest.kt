package com.vpr.screenlate.core.common.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CantoneseJyutpingTest {

    @Test
    fun `the key is the letters without tones, spaces or case`() {
        assertThat(CantoneseJyutping.key("nei5 hou2")).isEqualTo("neihou")
        assertThat(CantoneseJyutping.key("Nei5Hou2")).isEqualTo("neihou")
        assertThat(CantoneseJyutping.key("nei hou")).isEqualTo("neihou")
        assertThat(CantoneseJyutping.key("m4 goi1, ng5")).isEqualTo("mgoing")
    }

    @Test
    fun `text that is no jyutping has no key`() {
        assertThat(CantoneseJyutping.key("你好")).isNull()
        assertThat(CantoneseJyutping.key("nei55")).isNull()
        assertThat(CantoneseJyutping.key("5nei")).isNull()
        assertThat(CantoneseJyutping.key("nǐ hǎo")).isNull()
        assertThat(CantoneseJyutping.key("")).isNull()
    }

    @Test
    fun `typed tones that match count for a reading, others against it`() {
        assertThat(CantoneseJyutping.match("nei5hou2", "nei5 hou2")).isEqualTo(2)
        assertThat(CantoneseJyutping.match("nei5hou", "nei5 hou2")).isEqualTo(1)
        assertThat(CantoneseJyutping.match("neihou", "nei5 hou2")).isEqualTo(0)
        assertThat(CantoneseJyutping.match("nei3hou2", "nei5 hou2")).isEqualTo(0)
        assertThat(CantoneseJyutping.match("nei3hou3", "nei5 hou2")).isEqualTo(-2)
        // Readings of other letters are not compared.
        assertThat(CantoneseJyutping.match("nei5", "nei5 hou2")).isEqualTo(0)
    }

    @Test
    fun `jyutping becomes yale as wiktionary titles its recordings`() {
        assertThat(CantoneseJyutping.yale("ngo5 dei6")).isEqualTo("ngo5dei6")
        assertThat(CantoneseJyutping.yale("zou2 san4")).isEqualTo("jou2san4")
        assertThat(CantoneseJyutping.yale("cin4")).isEqualTo("chin4")
        assertThat(CantoneseJyutping.yale("jat1")).isEqualTo("yat1")
        assertThat(CantoneseJyutping.yale("jyu4")).isEqualTo("yu4")
        assertThat(CantoneseJyutping.yale("hoeng1 gong2")).isEqualTo("heung1gong2")
        assertThat(CantoneseJyutping.yale("seoi2")).isEqualTo("seui2")
        assertThat(CantoneseJyutping.yale("saa1 baat3")).isEqualTo("sa1baat3")
        assertThat(CantoneseJyutping.yale("m4 goi1")).isEqualTo("m4goi1")
        assertThat(CantoneseJyutping.yale("Gwong2 Dung1 Waa2")).isEqualTo("gwong2dung1wa2")
    }

    @Test
    fun `a reading without every tone has no yale`() {
        assertThat(CantoneseJyutping.yale("nei hou")).isNull()
        assertThat(CantoneseJyutping.yale("nei5hou2")).isNull()
        assertThat(CantoneseJyutping.yale("")).isNull()
        assertThat(CantoneseJyutping.yale("nǐ hǎo")).isNull()
    }
}
