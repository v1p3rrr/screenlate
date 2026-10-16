package com.vpr.screenlate.experiments.ocrcompare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Shows a page in a WebView that DevTools can reach, below the system bars, for `scripts/ocr-compare/collect.mjs`:
 * `am start -n com.vpr.screenlate.experiments.ocrcompare.test/com.vpr.screenlate.experiments.ocrcompare.PageActivity
 * -d <url>`, then forward port 9222 to `localabstract:webview_devtools_remote_<pid>`.
 */
class PageActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this).apply { settings.javaScriptEnabled = true }
        val frame = FrameLayout(this).apply { addView(web) }
        frame.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(frame)
        intent.dataString?.let(web::loadUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(web::loadUrl)
    }
}
