package me.viktorbarzin.portalframe

/**
 * Whether a page that loaded is still behaving like a photo frame.
 *
 * Android-free so it can be unit-tested on the JVM, matching [FrameUrl],
 * [FrameFailure] and [UpdateSchedule]; [FrameView] supplies the clock and the
 * requests.
 *
 * **Why a loaded page needs watching at all.** Every failure the frame could
 * previously explain was a failure of a *navigation* — nothing answered, or a
 * server answered with an error — and those arrive as WebView callbacks. The
 * failure that kept happening isn't one of those. The Portal joins a Wi-Fi with no
 * route home; the shell comes out of the WebView's cache, so the navigation
 * succeeds; the page boots, its first photo request dies, and it goes quiet. On the
 * London Portal on 2026-08-18 that state had lasted at least a day: the page was up
 * (its own UI was drawn), and DevTools recorded zero network events in 75 s.
 *
 * So the signal is silence. A working frame asks for a photo every `Interval`
 * seconds — 30 on the London frame, 45 on Sofia's — and a broken one asks for
 * nothing at all, which makes a few minutes of quiet a reliable verdict rather than
 * a guess.
 *
 * The first stall is answered with a reload and nothing on screen: most stalls heal,
 * and a panel that appears on a wall for a moment is worse than the moment. A second
 * stall means the reload didn't help, and that is worth saying out loud.
 */
class FrameHealth(private val silenceMs: Long) {

    /** What [check] concluded. */
    sealed class Verdict {
        /** Photos are arriving, the frame is off screen, or it is not due yet. */
        object Healthy : Verdict()

        /**
         * No photo for [silentForMs]; the caller should reload. [attempt] counts
         * consecutive stalls, and [explain] is true once one reload has already
         * failed to bring photos back.
         */
        data class Stalled(
            val attempt: Int,
            val explain: Boolean,
            val silentForMs: Long,
        ) : Verdict()
    }

    private var watching = false

    /** The last photo request, or when watching began — what "silent since" means. */
    private var lastPhotoMs: Long = 0

    /** Earliest moment the next stall may be declared, so reloads keep to a cadence. */
    private var dueMs: Long = 0

    private var attempts = 0

    /** Begin watching a page: a fresh load, or the frame coming back on screen. */
    fun watch(nowMs: Long) {
        watching = true
        rebase(nowMs)
    }

    /** Stop watching. A paused WebView issues no requests, so its silence means nothing. */
    fun unwatch() {
        watching = false
    }

    /** The page asked for a photo — it is doing its job. */
    fun photoRequested(nowMs: Long) {
        rebase(nowMs)
    }

    /** Decide whether the frame has gone quiet. Call on a timer while it is on screen. */
    fun check(nowMs: Long): Verdict {
        if (!watching) return Verdict.Healthy
        // A backwards clock jump — NTP correcting a Portal that booted without a
        // clock worth trusting — would otherwise mean a negative silent time on the
        // panel, and a stall declared the moment time caught up. Start again instead.
        if (nowMs < lastPhotoMs) {
            rebase(nowMs)
            return Verdict.Healthy
        }
        if (nowMs < dueMs) return Verdict.Healthy

        attempts++
        dueMs = nowMs + silenceMs
        return Verdict.Stalled(
            attempt = attempts,
            explain = attempts >= EXPLAIN_AFTER,
            silentForMs = nowMs - lastPhotoMs,
        )
    }

    private fun rebase(nowMs: Long) {
        lastPhotoMs = nowMs
        dueMs = nowMs + silenceMs
        attempts = 0
    }

    private companion object {
        /** Stalls before the panel goes up — one reload is given the chance to fix it. */
        const val EXPLAIN_AFTER = 2
    }
}
