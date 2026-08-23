package me.viktorbarzin.portalframe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
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
 *
 * Two of those facts only became reachable once the frame stopped trusting a single
 * successful navigation. The page can load and *then* stop being a photo frame —
 * see [FrameHealth] — so a timer watches for silence alongside the load callbacks.
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

    // Watches a page that loaded but stopped asking for photos.
    private val health = FrameHealth(SILENCE_MS)

    // True while the stall panel is up. It outlives a reload on purpose: the panel
    // must stay until photos actually come back, not vanish the moment a page
    // finishes loading — a page loading fine is exactly what the stall looks like.
    private var stalled = false

    // Resolved per load, not cached, so re-pointing the device takes effect on the
    // next reload without restarting the app.
    private val urls = FrameUrlStore(context)

    init {
        setBackgroundColor(Color.BLACK)
        watchServiceWorkerRequests()
        build()
    }

    /**
     * Count photo requests the page makes *through its service worker*.
     *
     * ImmichFrame registers a PWA service worker, and once that worker takes
     * control of the page every fetch it makes is served through the worker —
     * which means none of them reach [WebViewClient.shouldInterceptRequest] or
     * [WebViewClient.onLoadResource]. Those two hooks watch the page, and a
     * controlled page talks to the network from somewhere else. WebView exposes
     * worker traffic on a separate, process-global controller, so without this the
     * frame sees perfect silence while photos are in fact arriving.
     *
     * That is not theoretical: on the Valchedrym Portal on 2026-08-23 the stall
     * panel sat on the glass with "no photo has loaded for 18 minutes" while the
     * reverse proxy logged the device pulling 11 full-size photos in five minutes —
     * the same rate as a known-good desktop browser on the same frame. It reads as
     * an intermittent fault because a freshly installed build works until the
     * worker activates and takes over, and from then on the frame is blind.
     *
     * The controller is per-process, so the most recently constructed FrameView
     * owns it — which is the one on screen, since the activity and the screensaver
     * are never up at once.
     */
    private fun watchServiceWorkerRequests() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    // Nothing is intercepted; this only reports liveness. Runs off
                    // the main thread, hence the post, exactly as the page-level hook
                    // below does.
                    if (isPhotoRequest(request.url?.path)) main.post(::photoSeen)
                    return null
                }
            }
        )
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
            // The frame page carries no Cache-Control, so with LOAD_DEFAULT Chromium
            // applies heuristic freshness — 10% of the file's age, days for a shell
            // built weeks ago. A Portal launched on a Wi-Fi with no route home then
            // renders that cached shell, the navigation SUCCEEDS, and every callback
            // below stays quiet while the screen is black. Fetching ~74 KB on each
            // load is the cheaper half of that trade.
            cacheMode = WebSettings.LOAD_NO_CACHE
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

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // Nothing is intercepted — this sees the page's own fetch/XHR.
                // Once a service worker controls the page its traffic moves out of
                // reach of this hook; watchServiceWorkerRequests() covers that.
                // Runs off the main thread, hence the post.
                if (isPhotoRequest(request?.url?.path)) main.post(::photoSeen)
                return null
            }

            override fun onLoadResource(view: WebView, url: String?) {
                // Belt and braces: a request served through the page's service worker
                // may not reach shouldInterceptRequest. Double-counting is harmless —
                // both paths only ever say "the frame is alive".
                if (isPhotoRequest(runCatching { Uri.parse(url).path }.getOrNull())) photoSeen()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // A load that carried no error is a working frame — stand down. Not
                // while stalled, though: a stalled frame loads perfectly well, and
                // hiding the panel here would flash it every reload instead of
                // leaving it up until photos actually return.
                if (state.finishedSuccessfully() && !stalled) showStatus(false)
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
        // A fresh WebView is a fresh page: start the stall clock with it, so the
        // Dream path and a renderer rebuild are watched exactly like app mode.
        startWatching()
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

    /**
     * The page asked the frame server for content, which is the whole definition of
     * a working frame here. Clears a stall: the panel is only ever taken down by
     * photos arriving, never by a page merely finishing a load.
     */
    private fun photoSeen() {
        health.photoRequested(System.currentTimeMillis())
        if (!stalled) return
        stalled = false
        if (state.current == null) showStatus(false)
    }

    /**
     * Ask [health] whether the frame has gone quiet, and act on the answer.
     *
     * Skipped entirely while a load failure is on screen: that panel names the exact
     * error and is already retrying every few seconds, so replacing it with "it
     * stopped showing photos" would trade a precise message for a vague one.
     */
    private fun checkHealth() {
        if (state.current != null) return
        val verdict = health.check(System.currentTimeMillis())
        if (verdict !is FrameHealth.Verdict.Stalled) return
        if (verdict.explain) {
            stalled = true
            status.show(
                FrameFailure.stalled(urls.current(), verdict.silentForMs),
                verdict.attempt,
                (SILENCE_MS / 1000).toInt(),
            )
            showStatus(true)
        }
        // Reloading IS the probe: it either brings photos back or fails for real,
        // and a real failure explains itself far better than this can.
        webView?.let(::load)
    }

    private val healthTick = object : Runnable {
        override fun run() {
            checkHealth()
            main.postDelayed(this, CHECK_MS)
        }
    }

    private fun startWatching() {
        health.watch(System.currentTimeMillis())
        main.removeCallbacks(healthTick)
        main.postDelayed(healthTick, CHECK_MS)
    }

    private fun stopWatching() {
        health.unwatch()
        main.removeCallbacks(healthTick)
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
        // A deliberate reload is a fresh start, so the stall streak starts over. The
        // watchdog's own reloads deliberately do NOT come through here — theirs must
        // accumulate, or a second stall could never be reached and never explained.
        startWatching()
        val wv = webView
        if (wv == null) build() else load(wv)
    }

    fun onResumeView() {
        webView?.onResume()
        startWatching()
    }

    fun onPauseView() {
        webView?.onPause()
        stopWatching()
    }

    fun destroyView() {
        stopWatching()
        destroyWebView()
    }

    /** Content from the frame server, as opposed to the page's own assets. */
    private fun isPhotoRequest(path: String?): Boolean = path?.startsWith(API_PREFIX) == true

    private companion object {
        const val RETRY_MS = 5_000L
        const val REBUILD_MS = 1_500L

        /**
         * How long a loaded page may ask for nothing before it is treated as stalled.
         * Six missed photo cycles on the London frame (`Interval: 30`), four on
         * Sofia's (45) — comfortably past a slow response, well short of a wall
         * staying black unexplained.
         */
        const val SILENCE_MS = 3L * 60 * 1000

        /** Tick well inside [SILENCE_MS] so a stall is noticed near when it starts. */
        const val CHECK_MS = 30_000L

        /**
         * Photo traffic, matched on the prefix rather than the exact endpoint: a
         * cycle is `/api/Asset`, `/api/Asset/…/AssetInfo` and `/api/Asset/…/AssetFaces`,
         * and matching the prefix means one endpoint being renamed upstream cannot
         * make a healthy frame look dead.
         */
        const val API_PREFIX = "/api/"
    }
}
