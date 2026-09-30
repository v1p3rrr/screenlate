package com.vpr.screenlate.overlay.web

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class PopupPageTest {

    private val policy = Regex("""<meta http-equiv="Content-Security-Policy" content="([^"]*)">""")
        .find(File("src/main/assets/popup/popup.html").readText())
        ?.groupValues?.get(1)
        ?.split(';')
        ?.map { it.trim().split(' ') }
        ?.associate { it.first() to it.drop(1) }
        .orEmpty()

    @Test
    fun `only stylesheets and fonts come from the internet`() {
        assertThat(policy["default-src"]).containsExactly("'self'")
        assertThat(policy["script-src"]).containsExactly("'self'")
        assertThat(policy["style-src"]).contains("https:")
        assertThat(policy["font-src"]).contains("https:")
        listOf("img-src", "media-src", "connect-src").forEach { directive ->
            assertThat(policy[directive]).isNotNull()
            assertThat(policy[directive].orEmpty().filter { it.startsWith("http") || it == "*" }).isEmpty()
        }
    }
}
