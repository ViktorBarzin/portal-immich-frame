package me.viktorbarzin.portalframe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Self-healing host for the frame WebView, shared by [FrameActivity] (app mode)
 * and [FrameDreamService] (screensaver).
 *
 * An always-on kiosk WebView WILL lose its Chromium renderer eventually (OOM,
 * GPU hiccup, Portal's customized WebView). Without handling that the screen goes
 * permanently black. This container rebuilds the WebView when the renderer dies
 * and reloads on demand, so the frame recovers on its own.
 */
@SuppressLint("ViewConstructor")
class FrameView(context: Context) : FrameLayout(context) {

    private var webView: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.BLACK)
        build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun build() {
        val wv = WebView(context)
        wv.setBackgroundColor(Color.BLACK)
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.overScrollMode = OVER_SCROLL_NEVER
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Self-heal transient network/WAN failures of the main page.
                if (request?.isForMainFrame == true) {
                    main.postDelayed({ view.loadUrl(BuildConfig.FRAME_URL) }, RETRY_MS)
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // The renderer died. Discard the dead WebView and rebuild — and
                // return true so the framework does NOT kill our process.
                destroyWebView()
                main.postDelayed({ build() }, REBUILD_MS)
                return true
            }
        }
        webView = wv
        addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wv.loadUrl(BuildConfig.FRAME_URL)
    }

    private fun destroyWebView() {
        webView?.let {
            removeView(it)
            it.destroy()
        }
        webView = null
    }

    /** Reload the frame (e.g. when the app is re-opened). Rebuilds if needed. */
    fun reload() {
        val wv = webView
        if (wv == null) build() else wv.loadUrl(BuildConfig.FRAME_URL)
    }

    fun onResumeView() = webView?.onResume()
    fun onPauseView() = webView?.onPause()
    fun destroyView() = destroyWebView()

    private companion object {
        const val RETRY_MS = 5_000L
        const val REBUILD_MS = 1_500L
    }
}
