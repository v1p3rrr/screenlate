package com.vpr.screenlate.overlay.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.resume

/**
 * The lookup results page (`assets/popup/popup.html`) in a WebView, shared by the overlay popup and the search
 * screen. Hosts add [container] to their layout; the WebView inside is replaced if its renderer dies.
 *
 * Pages and dictionary media are served from `https://appassets.androidplatform.net/`: assets under `/assets/`,
 * media from `/media?d=<dictionary>&p=<path>` through [Callbacks.media].
 *
 * @param embedded the page is part of an app screen: no card frame and no close button.
 */
class LookupPage(
    context: Context,
    private val callbacks: Callbacks,
    embedded: Boolean = false,
) {
    interface Callbacks {
        /** The close button, or the renderer died and the page was reset. */
        fun onClose()

        /** A link inside a glossary asks to look up [query], preferring terms read as [primaryReading]. */
        fun onLookup(query: String, primaryReading: String?)

        fun onOpenUrl(url: String)

        /** ➕ on entry [index]; [noteData] is the JSON from the page's NoteData.build. */
        fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean)

        fun onPlayAudio(expression: String, reading: String)

        /** A kanji in an entry's headword was tapped. */
        fun onKanji(character: String)

        /** Bytes of a dictionary media file. Called on a WebView background thread; may block. */
        fun media(dictionary: String, path: String): ByteArray?
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageReady = false
    private val pendingScripts = mutableListOf<String>()

    /** Scripts that configure the page and must be replayed after a renderer restart. */
    private val persistent = linkedMapOf<String, String>()

    init {
        // Debug builds expose the page to DevTools (chrome://inspect, scripts/popup-eval.mjs).
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        if (embedded) setPersistent("configure", "Popup.configure({embedded: true})")
    }

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    val container = FrameLayout(context)
    private var webView = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = PageClient()
        addJavascriptInterface(Bridge(), "ScreenlateBridge")
        loadUrl(PAGE_URL)
        container.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Shows [state] (JSON from [PageState]), replacing the current view and its back stack. */
    fun render(state: String) = run("Popup.render($state)")

    /** Re-renders the current view in place (theme, OCR status). */
    fun update(state: String) = run("Popup.update($state)")

    /** Shows [state] on top of the current view; the back button returns to it. */
    fun push(state: String) = run("Popup.push($state)")

    /** Sets the scoped `styles.css` of the loaded dictionaries (a JSON array of {dictionary, css}). */
    fun setStyles(styles: JsonElement) = setPersistent("styles", "Popup.setStyles($styles)")

    /** Shows or hides the ➕ and 🔊 buttons of entries. */
    fun setActions(anki: Boolean, audio: Boolean) = setPersistent("actions", "Popup.setActions({anki: $anki, audio: $audio})")

    /** Sets ➕ button states by entry index (see `Popup.setNoteStates`). */
    fun setNoteStates(states: Map<Int, String>) {
        if (states.isEmpty()) return
        val json = states.entries.joinToString(",", "{", "}") { (index, state) -> "\"$index\":\"$state\"" }
        run("Popup.setNoteStates($json)")
    }

    /** Evaluates [script] in the page and returns its JSON-encoded result, or null if the page is not ready. */
    suspend fun evaluate(script: String): String? {
        if (!pageReady) return null
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
        }
    }

    fun destroy() {
        container.removeAllViews()
        webView.destroy()
    }

    private fun setPersistent(key: String, script: String) {
        if (persistent[key] == script) return
        persistent[key] = script
        run(script)
    }

    private fun run(script: String) {
        if (pageReady) {
            webView.evaluateJavascript(script, null)
        } else {
            // Only the latest view matters; configuration is replayed from `persistent`.
            pendingScripts.clear()
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
    private inner class PageClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host == HOST && url.path == MEDIA_PATH) {
                return mediaResponse(url.getQueryParameter("d").orEmpty(), url.getQueryParameter("p").orEmpty())
            }
            return assetLoader.shouldInterceptRequest(url)
        }

        // A crashed or killed renderer must not take the app or the accessibility service down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Lookup page renderer gone (crashed: ${detail.didCrash()}), recreating")
            container.removeView(view)
            view.destroy()
            pageReady = false
            pendingScripts.clear()
            webView = createWebView(view.context)
            callbacks.onClose()
            return true
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = post {
            pageReady = true
            (persistent.values + pendingScripts).forEach { webView.evaluateJavascript(it, null) }
            pendingScripts.clear()
        }

        @JavascriptInterface
        fun onClose() = post { callbacks.onClose() }

        @JavascriptInterface
        fun onLookup(query: String, primaryReading: String) =
            post { callbacks.onLookup(query, primaryReading.ifEmpty { null }) }

        @JavascriptInterface
        fun onOpenUrl(url: String) = post { callbacks.onOpenUrl(url) }

        @JavascriptInterface
        fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean) =
            post { callbacks.onAddNote(index, noteData, withScreenshot) }

        @JavascriptInterface
        fun onPlayAudio(expression: String, reading: String) = post { callbacks.onPlayAudio(expression, reading) }

        @JavascriptInterface
        fun onKanji(character: String) = post { callbacks.onKanji(character) }

        private fun post(action: () -> Unit) {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val TAG = "LookupPage"
        const val HOST = "appassets.androidplatform.net"
        const val PAGE_URL = "https://$HOST/assets/popup/popup.html"
        const val MEDIA_PATH = "/media"
    }
}
