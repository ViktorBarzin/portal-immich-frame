package me.viktorbarzin.portalframe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
 *
 * It also **says why** when it can't show photos. A failed load used to leave a
 * black screen — indistinguishable from "asleep", from "the server is down", and
 * from "this Portal joined the neighbour's Wi-Fi", which is what it actually was
 * twice. [FrameStatusView] puts the deciding facts on the glass instead.
 */
@SuppressLint("ViewConstructor")
class FrameView(context: Context) : FrameLayout(context) {

    private var webView: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    // Shown over the WebView when a load fails, hidden again once one succeeds.
    // Kept outside build()/destroyWebView() so a renderer death doesn't take the
    // explanation off screen with it.
    private val status = FrameStatusView(context)
    private val state = FrameLoadState()

    // Resolved per load, not cached, so re-pointing the device takes effect on the
    // next reload without restarting the app.
    private val urls = FrameUrlStore(context)

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
        wv.keepScreenOn = true
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
                // Nothing answered — no route, no DNS, refused, timed out. This is the
                // Portal-roamed-to-the-wrong-Wi-Fi case: it never reaches a server, so
                // no server-side error page can explain it. We have to.
                if (request?.isForMainFrame == true) {
                    fail(FrameFailure.network(error?.description?.toString(), urls.current()))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest?,
                response: WebResourceResponse?
            ) {
                // A server answered, with an error. Only the main document counts: the
                // frame page pulls assets whose individual failures it handles itself
                // (ImmichFrame shows its own "immich-server is offline" screen), and
                // covering that with ours for one failed asset would be a regression.
                if (request?.isForMainFrame != true) return
                val code = response?.statusCode ?: return
                FrameFailure.httpOrNull(code, urls.current())?.let(::fail)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // A load that carried no error is a working frame — stand down.
                if (state.finishedSuccessfully()) showStatus(false)
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
        // The panel must out-rank the WebView in z-order. On a rebuild the fresh
        // WebView is added on top of it, hence the explicit bringToFront().
        if (status.parent == null) {
            addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            showStatus(false)
        } else {
            status.bringToFront()
        }
        load(wv)
    }

    /**
     * The single place a load is started. Resetting [state] here rather than in
     * `onPageStarted` is what keeps a recorded failure alive: WebView reports an
     * HTTP error before it announces the error document, so a callback-driven reset
     * erases the failure a moment after it is drawn.
     */
    private fun load(view: WebView) {
        state.startingLoad()
        view.loadUrl(urls.current())
    }

    /** Put [failure] on screen and keep reloading, so the frame returns on its own. */
    private fun fail(failure: FrameFailure) {
        val attempt = state.failed(failure)
        status.show(failure, attempt, (RETRY_MS / 1000).toInt())
        showStatus(true)
        main.postDelayed({ webView?.let(::load) }, RETRY_MS)
    }

    private fun showStatus(visible: Boolean) {
        status.visibility = if (visible) VISIBLE else GONE
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
        if (wv == null) build() else load(wv)
    }

    fun onResumeView() = webView?.onResume()
    fun onPauseView() = webView?.onPause()
    fun destroyView() = destroyWebView()

    private companion object {
        const val RETRY_MS = 5_000L
        const val REBUILD_MS = 1_500L
    }
}
