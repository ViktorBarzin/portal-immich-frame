package me.viktorbarzin.portalframe

/**
 * Whether the frame is currently showing photos or a failure, across one load.
 *
 * Android-free so it can be unit-tested on the JVM, matching [FrameUrl] and
 * [FrameFailure]; [FrameView] owns the WebView callbacks that drive it.
 *
 * The one rule worth stating out loud: **a load is only ever reset by us**, at the
 * point we call `loadUrl`. It is tempting to reset in `onPageStarted` instead, but
 * WebView reports an HTTP error BEFORE announcing the error document, so that reset
 * erases the failure and the `onPageFinished` behind it reads as a clean load. See
 * [FrameLoadStateTest] for the sequence that caught this.
 */
class FrameLoadState {

    /** The failure to draw, or null while the current load is still clean. */
    var current: FrameFailure? = null
        private set

    private var streak = 0

    /** Called immediately before we ask the WebView to load — the only reset point. */
    fun startingLoad() {
        current = null
    }

    /** Record a failed load; returns the consecutive-failure count for the panel. */
    fun failed(failure: FrameFailure): Int {
        // A single navigation can report more than once (e.g. an error for the
        // document and another as it settles); the streak counts loads, not callbacks.
        if (current == null) streak++
        current = failure
        return streak
    }

    /** True when the load that just finished carried no error — the caller may hide the panel. */
    fun finishedSuccessfully(): Boolean {
        if (current != null) return false
        streak = 0
        return true
    }
}
