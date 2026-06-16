package me.viktorbarzin.portalframe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Builds the single WebView that renders the already-running ImmichFrame page.
 * Shared by [FrameActivity] (launcher/home) and [FrameDreamService] (screensaver)
 * so both modes behave identically.
 */
object FrameWebViewFactory {

    private const val RETRY_DELAY_MS = 5_000L

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView {
        val webView = WebView(context)
        webView.setBackgroundColor(Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.keepScreenOn = true
        // A photo frame is display-only: swallow long-press (text selection / context menu).
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Self-heal: if the main page fails (e.g. transient network/WAN blip),
                // reload it after a short delay so the frame recovers on its own.
                if (request?.isForMainFrame == true) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { view.loadUrl(BuildConfig.FRAME_URL) },
                        RETRY_DELAY_MS
                    )
                }
            }
        }
        return webView
    }
}
