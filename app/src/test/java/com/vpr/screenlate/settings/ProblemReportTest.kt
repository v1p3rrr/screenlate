package com.vpr.screenlate.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportTest {
    @Test
    fun `fills the form fields`() {
        assertEquals(
            "https://github.com/v1p3rrr/screenlate/issues/new?template=problem.yml" +
                "&version=0.1.2&android=15+%28API+35%29&device=HONOR+ABC-NX9%26",
            ProblemReport.url(appVersion = "0.1.2", android = "15 (API 35)", device = "HONOR ABC-NX9&"),
        )
    }

    @Test
    fun `uses field ids that exist in the form`() {
        val form = File("../.github/ISSUE_TEMPLATE/problem.yml").readText()
        listOf("version", "android", "device").forEach { id ->
            assertTrue("No field $id in problem.yml", Regex("""id: $id\b""").containsMatchIn(form))
        }
    }
}
