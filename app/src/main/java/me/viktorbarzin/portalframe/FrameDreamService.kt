package me.viktorbarzin.portalframe

import android.service.dreams.DreamService
import android.webkit.WebView

/**
 * Screensaver ("Dream") that renders the frame when the Portal is idle.
 * This is the primary idle-display mechanism; the HOME role on [FrameActivity]
 * is the fallback if the Portal does not trigger stock Android screensavers.
 */
class FrameDreamService : DreamService() {

    private var webView: WebView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        isScreenBright = true

        val wv = FrameWebViewFactory.create(this)
        webView = wv
        setContentView(wv)
        wv.loadUrl(BuildConfig.FRAME_URL)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        webView?.onResume()
    }

    override fun onDreamingStopped() {
        webView?.onPause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        webView?.destroy()
        webView = null
        super.onDetachedFromWindow()
    }
}
