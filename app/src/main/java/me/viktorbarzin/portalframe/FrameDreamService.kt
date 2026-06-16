package me.viktorbarzin.portalframe

import android.service.dreams.DreamService

/**
 * Screensaver ("Dream") that renders the frame when the Portal is idle. Exits on
 * any touch automatically (standard screensaver behaviour). Uses the same
 * self-healing [FrameView] as the app, so a renderer crash during a long idle
 * stretch recovers on its own.
 */
class FrameDreamService : DreamService() {

    private var frame: FrameView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        isScreenBright = true

        val f = FrameView(this)
        frame = f
        setContentView(f)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        frame?.onResumeView()
    }

    override fun onDreamingStopped() {
        frame?.onPauseView()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        frame?.destroyView()
        frame = null
        super.onDetachedFromWindow()
    }
}
