package com.vpr.screenlate.overlay.popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.ui.OverlayWindows
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

/**
 * The popup window: a pre-warmed WebView rendering `assets/popup/popup.html`.
 *
 * Pages and dictionary media are served from `https://appassets.androidplatform.net/`: assets under `/assets/`,
 * media from `/media?d=<dictionary>&p=<path>` through [Callbacks.media].
 */
class PopupController(
    context: Context,
    private val windowManager: WindowManager,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onClose()

        /** A link inside a glossary asks to look up [query], preferring terms read as [primaryReading]. */
        fun onLookup(query: String, primaryReading: String?)

        fun onOpenUrl(url: String)

        /** Bytes of a dictionary media file. Called on a WebView background thread; may block. */
        fun media(dictionary: String, path: String): ByteArray?
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val edgeMargin = EDGE_MARGIN_DP * context.resources.displayMetrics.density
    private val params = OverlayWindows.popupParams()
    private var attached = false
    private var pageReady = false
    private val pendingScripts = mutableListOf<String>()
    private var styles: String? = null

    var bounds: Box? = null
        private set

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    private var webView = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = PopupWebViewClient()
        addJavascriptInterface(Bridge(), "ScreenlateBridge")
        loadUrl(PAGE_URL)
    }

    val isShowing: Boolean get() = attached

    /** Shows [state] in a popup placed next to [word], replacing whatever it showed before. */
    fun show(state: JsonElement, word: Box, vertical: Boolean, bubble: Box?, screen: Box, maxWidth: Float) {
        val area = Box(screen.left + edgeMargin, screen.top + edgeMargin, screen.right - edgeMargin, screen.bottom - edgeMargin)
        val width = minOf(screen.width * WIDTH_FRACTION, maxWidth)
        val height = screen.height * HEIGHT_FRACTION
        val placed = PopupPlacement.place(word, vertical, bubble, width, height, area, margin = edgeMargin * 2)
        bounds = placed
        params.x = placed.left.roundToInt()
        params.y = placed.top.roundToInt()
        params.width = placed.width.roundToInt()
        params.height = placed.height.roundToInt()
        if (attached) {
            windowManager.updateViewLayout(webView, params)
        } else {
            windowManager.addView(webView, params)
            attached = true
        }
        run("Popup.render($state)")
    }

    /** Re-renders the current view in place, if the popup is showing. */
    fun update(state: JsonElement) {
        if (attached) run("Popup.update($state)")
    }

    /** Shows [state] on top of the current view; the popup's back button returns to it. */
    fun push(state: JsonElement) {
        if (attached) run("Popup.push($state)")
    }

    /** Sets the scoped `styles.css` of the loaded dictionaries (a JSON array of {dictionary, css}). */
    fun setStyles(styles: JsonElement) {
        val script = "Popup.setStyles($styles)"
        if (script == this.styles) return
        this.styles = script
        run(script)
    }

    fun hide() {
        if (!attached) return
        windowManager.removeView(webView)
        attached = false
        bounds = null
    }

    fun release() {
        hide()
        webView.destroy()
    }

    private fun run(script: String) {
        if (pageReady) {
            webView.evaluateJavascript(script, null)
        } else {
            // Only the latest view matters, but styles must survive.
            pendingScripts.removeAll { !it.startsWith("Popup.setStyles") }
            pendingScripts += script
        }
    }

    private fun mediaResponse(dictionary: String, path: String): WebResourceResponse {
        val bytes = runCatching { callbacks.media(dictionary, path) }
            .onFailure { Log.w(TAG, "Media $path of $dictionary failed", it) }
            .getOrNull()
            ?: return WebResourceResponse("text/plain", null, 404, "Not Found", emptyMap(), null)
        return WebResourceResponse(mimeType(path), null, bytes.inputStream())
    }

    private fun mimeType(path: String): String = when (val extension = path.substringAfterLast('.', "").lowercase()) {
        "svg" -> "image/svg+xml"
        "avif" -> "image/avif"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    // Lint does not see the override below in an inner class and reports MissingOnRenderProcessGone.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class PopupWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host == HOST && url.path == MEDIA_PATH) {
                return mediaResponse(url.getQueryParameter("d").orEmpty(), url.getQueryParameter("p").orEmpty())
            }
            return assetLoader.shouldInterceptRequest(url)
        }

        // A crashed or killed renderer must not take the accessibility service down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Popup renderer gone (crashed: ${detail.didCrash()}), recreating")
            val wasShowing = attached
            hide()
            view.destroy()
            pageReady = false
            pendingScripts.clear()
            styles?.let { pendingScripts += it }
            webView = createWebView(view.context)
            if (wasShowing) callbacks.onClose()
            return true
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = post {
            pageReady = true
            pendingScripts.forEach { webView.evaluateJavascript(it, null) }
            pendingScripts.clear()
        }

        @JavascriptInterface
        fun onClose() = post { callbacks.onClose() }

        @JavascriptInterface
        fun onLookup(query: String, primaryReading: String) =
            post { callbacks.onLookup(query, primaryReading.ifEmpty { null }) }

        @JavascriptInterface
        fun onOpenUrl(url: String) = post { callbacks.onOpenUrl(url) }

        private fun post(action: () -> Unit) {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val TAG = "PopupController"
        const val HOST = "appassets.androidplatform.net"
        const val PAGE_URL = "https://$HOST/assets/popup/popup.html"
        const val MEDIA_PATH = "/media"
        const val WIDTH_FRACTION = 0.85f
        const val HEIGHT_FRACTION = 0.35f
        const val EDGE_MARGIN_DP = 8f
    }
}
