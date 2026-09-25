package com.vpr.screenlate.overlay.popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.util.Log
import androidx.webkit.WebViewAssetLoader
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.overlay.ui.OverlayWindows
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The popup window: a pre-warmed WebView rendering `assets/popup/popup.html`.
 *
 * Pages are served from `https://appassets.androidplatform.net/` so dictionary media can later be added to the same
 * origin through [WebViewAssetLoader].
 */
class PopupController(
    context: Context,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val edgeMargin = EDGE_MARGIN_DP * context.resources.displayMetrics.density
    private val params = OverlayWindows.popupParams()
    private var attached = false
    private var pageReady = false
    private var pendingState: JSONObject? = null

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

    /** Shows [state] in a popup placed next to [word]. */
    fun show(state: JSONObject, word: Box, vertical: Boolean, bubble: Box?, screen: Box, maxWidth: Float) {
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
        render(state)
    }

    /** Re-renders the popup in place, if it is showing. */
    fun update(state: JSONObject) {
        if (attached) render(state)
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

    private fun render(state: JSONObject) {
        if (!pageReady) {
            pendingState = state
            return
        }
        webView.evaluateJavascript("Popup.render($state)", null)
    }

    // Lint does not see the override below in an inner class and reports MissingOnRenderProcessGone.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class PopupWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        // A crashed or killed renderer must not take the accessibility service down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Popup renderer gone (crashed: ${detail.didCrash()}), recreating")
            val wasShowing = attached
            hide()
            view.destroy()
            pageReady = false
            webView = createWebView(view.context)
            if (wasShowing) onClose()
            return true
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = mainHandler.post {
            pageReady = true
            pendingState?.let(::render)
            pendingState = null
        }.let { }

        @JavascriptInterface
        fun onClose() = mainHandler.post { onClose.invoke() }.let { }
    }

    private companion object {
        const val TAG = "PopupController"
        const val PAGE_URL = "https://appassets.androidplatform.net/assets/popup/popup.html"
        const val WIDTH_FRACTION = 0.85f
        const val HEIGHT_FRACTION = 0.35f
        const val EDGE_MARGIN_DP = 8f
    }
}
