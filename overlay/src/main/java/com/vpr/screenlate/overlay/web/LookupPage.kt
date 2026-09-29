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
import com.vpr.screenlate.overlay.fonts.FontFiles
import com.vpr.screenlate.overlay.fonts.PageFonts
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/**
 * The lookup results page (`assets/popup/popup.html`) in a WebView, shared by the overlay popup and the search
 * screen. Hosts add [container] to their layout; the WebView inside is replaced if its renderer dies.
 *
 * The overlay's page gives up its renderer first when memory runs short while the popup is hidden, and loads again
 * on [prepare]. An embedded page is on screen whenever its app screen is, so its renderer keeps the default priority.
 *
 * Pages and dictionary media are served from `https://appassets.androidplatform.net/`: assets under `/assets/`,
 * media from `/media?d=<dictionary>&p=<path>` through [Callbacks.media], installed fonts from `/fonts/<file>`.
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

        /** A kanji in an entry's headword was tapped. */
        fun onKanji(character: String)

        /**
         * A copy button: an entry's headword, a selection, or a dictionary's definitions, which also come as [html]
         * for apps that paste formatted text.
         */
        fun onCopy(text: String, html: String? = null)

        /** Bytes of a dictionary media file. Called on a WebView background thread; may block. */
        fun media(dictionary: String, path: String): ByteArray?
    }

    /** The note and audio buttons of entries; entries are identified by their [index] in the current view. */
    interface NoteActions {
        /**
         * ➕ on an entry; [noteData] is the JSON from the page's NoteData.build.
         *
         * @param force add even if the note is a duplicate that the settings would prevent.
         */
        fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean, force: Boolean)

        /** 📖 on an entry: the note added for it, or its duplicate, should open in AnkiDroid. */
        fun onOpenNote(index: Int)

        fun onPlayAudio(index: Int, expression: String, reading: String)

        /** 🔊 was held: the page waits for [showAudioMenu]. */
        fun onAudioMenu(index: Int, expression: String, reading: String)

        /** A clip from the audio menu was chosen. */
        fun onPlayClip(index: Int, clipId: String)

        /** The button under an unavailable ➕ asks for the Anki settings, where the problem is explained. */
        fun onOpenApp()
    }

    /** Receives the note and audio buttons; without it they do nothing. */
    var noteActions: NoteActions? = null

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

    private val fontDirectory = File(context.filesDir, PageFonts.DIRECTORY)

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    private val selectionHost = SelectionHost(context)
    val container: FrameLayout = selectionHost

    /**
     * Called with true when a text selection starts in a window without the system's toolbar (an overlay), and with
     * false when it ends; the host makes its window focusable meanwhile, so the selection handles show.
     */
    fun setOnSelecting(listener: (Boolean) -> Unit) {
        selectionHost.onSelecting = listener
    }
    private val reclaimable = !embedded
    private var webView: WebView? = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = PageClient()
        if (reclaimable) setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
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

    /** Language, fonts and custom CSS of the page (JSON from [com.vpr.screenlate.overlay.fonts.PageAppearance]). */
    fun setAppearance(appearance: JsonElement) = setPersistent("appearance", "Popup.setAppearance($appearance)")

    /** Sets the scoped `styles.css` of the loaded dictionaries (a JSON array of {dictionary, css}). */
    fun setStyles(styles: JsonElement) = setPersistent("styles", "Popup.setStyles($styles)")

    /** Tag descriptions of the enabled dictionaries (a JSON array of {dictionary, notes}). */
    fun setTagNotes(notes: JsonElement) = setPersistent("tagNotes", "Popup.setTagNotes($notes)")

    /** Shows or hides the ➕ and 🔊 buttons of entries. */
    /** @param ankiProblem a short reason shown by a grey ➕ when Anki export is set up but broken. */
    fun setActions(anki: Boolean, audio: Boolean, ankiProblem: String? = null) = setPersistent(
        "actions",
        "Popup.setActions({anki: $anki, audio: $audio, ankiProblem: ${ankiProblem?.let { JsonPrimitive(it) } ?: "null"}})",
    )

    /** Markers used by the note fields and frequency dictionary modes (see `Popup.setNoteConfig`). */
    fun setNoteConfig(config: JsonElement) = setPersistent("noteConfig", "Popup.setNoteConfig($config)")

    /** Fills the menu opened by holding 🔊 (see `Popup.showAudioMenu`); [items] is a JSON array. */
    fun showAudioMenu(index: Int, items: JsonElement, loading: Boolean) =
        run("Popup.showAudioMenu($index, $items, $loading)")

    /** Sets ➕ button states by entry index (see `Popup.setNoteStates`). */
    fun setNoteStates(states: Map<Int, String>) {
        if (states.isEmpty()) return
        val json = states.entries.joinToString(",", "{", "}") { (index, state) -> "\"$index\":\"$state\"" }
        run("Popup.setNoteStates($json)")
    }

    /** Evaluates [script] in the page and returns its JSON-encoded result, or null if the page is not ready. */
    suspend fun evaluate(script: String): String? {
        val view = webView
        if (!pageReady || view == null) return null
        return suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
        }
    }

    /** Loads the page again if the system reclaimed its renderer; called when a scan starts, so it loads meanwhile. */
    fun prepare() {
        if (webView == null) webView = createWebView(container.context)
    }

    fun destroy() {
        container.removeAllViews()
        webView?.destroy()
        webView = null
    }

    /** Configuration waits for the page to be ready, which replays it; it does not bring back a reclaimed page. */
    private fun setPersistent(key: String, script: String) {
        if (persistent[key] == script) return
        persistent[key] = script
        if (pageReady) webView?.evaluateJavascript(script, null)
    }

    private fun run(script: String) {
        val view = webView
        if (pageReady && view != null) {
            view.evaluateJavascript(script, null)
        } else {
            // Only the latest view matters; configuration is replayed from `persistent`.
            pendingScripts.clear()
            pendingScripts += script
            prepare()
        }
    }

    private fun mediaResponse(dictionary: String, path: String): WebResourceResponse {
        val bytes = runCatching { callbacks.media(dictionary, path) }
            .onFailure { Log.w(TAG, "Media $path of $dictionary failed", it) }
            .getOrNull()
            ?: return WebResourceResponse("text/plain", null, 404, "Not Found", emptyMap(), null)
        return WebResourceResponse(mimeType(path), null, bytes.inputStream())
    }

    private fun fontResponse(name: String): WebResourceResponse {
        val format = FontFiles.formatOf(name)
        // The file may go (a font deleted or replaced) between the page asking for it and this read.
        val input = if (FontFiles.isFileName(name) && format != null) {
            runCatching { File(fontDirectory, name).inputStream() }.getOrNull()
        } else {
            null
        }
        if (format == null || input == null) return WebResourceResponse("text/plain", null, 404, "Not Found", emptyMap(), null)
        return WebResourceResponse(format.mimeType, null, input)
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
            val path = url.path.orEmpty()
            if (url.host == HOST && path.startsWith(FONTS_PATH)) return fontResponse(path.removePrefix(FONTS_PATH))
            return assetLoader.shouldInterceptRequest(url)
        }

        // A crashed or killed renderer must not take the app or the accessibility service down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val shown = view.isShown
            Log.w(TAG, "Lookup page renderer gone (crashed: ${detail.didCrash()}, shown: $shown)")
            container.removeView(view)
            view.destroy()
            pageReady = false
            pendingScripts.clear()
            webView = null
            // A renderer the system reclaimed from a hidden popup comes back with the next scan.
            if (shown || !reclaimable) {
                prepare()
                callbacks.onClose()
            }
            return true
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = post {
            pageReady = true
            (persistent.values + pendingScripts).forEach { webView?.evaluateJavascript(it, null) }
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
        fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean, force: Boolean) =
            post { noteActions?.onAddNote(index, noteData, withScreenshot, force) }

        @JavascriptInterface
        fun onOpenNote(index: Int) = post { noteActions?.onOpenNote(index) }

        @JavascriptInterface
        fun onPlayAudio(index: Int, expression: String, reading: String) =
            post { noteActions?.onPlayAudio(index, expression, reading) }

        @JavascriptInterface
        fun onAudioMenu(index: Int, expression: String, reading: String) =
            post { noteActions?.onAudioMenu(index, expression, reading) }

        @JavascriptInterface
        fun onPlayClip(index: Int, clipId: String) = post { noteActions?.onPlayClip(index, clipId) }

        @JavascriptInterface
        fun onOpenApp() = post { noteActions?.onOpenApp() }

        @JavascriptInterface
        fun onKanji(character: String) = post { callbacks.onKanji(character) }

        @JavascriptInterface
        fun onCopy(text: String) = post { callbacks.onCopy(text) }

        @JavascriptInterface
        fun onCopyDefinition(text: String, html: String) = post { callbacks.onCopy(text, html.ifEmpty { null }) }

        private fun post(action: () -> Unit) {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val TAG = "LookupPage"
        const val HOST = "appassets.androidplatform.net"
        const val PAGE_URL = "https://$HOST/assets/popup/popup.html"
        const val MEDIA_PATH = "/media"
        const val FONTS_PATH = "/${PageFonts.DIRECTORY}/"    }
}
