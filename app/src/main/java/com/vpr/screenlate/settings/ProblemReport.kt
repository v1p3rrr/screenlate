package com.vpr.screenlate.settings

import android.os.Build
import java.net.URLEncoder

/** GitHub's "Problem" form (`.github/ISSUE_TEMPLATE/problem.yml`) with the version and device fields filled in. */
internal object ProblemReport {
    fun url(appVersion: String): String = url(
        appVersion = appVersion,
        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
    )

    /** The keys are the form's field ids. */
    fun url(appVersion: String, android: String, device: String): String =
        "$SOURCE_URL/issues/new?" + listOf(
            "template" to "problem.yml",
            "version" to appVersion,
            "android" to android,
            "device" to device,
        ).joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
