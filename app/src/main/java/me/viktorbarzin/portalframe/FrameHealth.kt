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
class FrameHealth(private var silenceMs: Long) {

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

    /**
     * Adopt a threshold derived from the frame's real cadence, once it is known.
     *
     * The config answer arrives after the page has already started being watched,
     * so the frame begins on the conservative fixed threshold and is retuned a
     * moment later. Re-baselines rather than keeping the old clock: the interval
     * that was being measured against no longer applies.
     */
    fun retune(newSilenceMs: Long, nowMs: Long) {
        silenceMs = newSilenceMs
        rebase(nowMs)
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

    companion object {
        /** Stalls before the panel goes up — one reload is given the chance to fix it. */
        private const val EXPLAIN_AFTER = 2

        /**
         * Threshold used when the frame's cadence is unknown. The original fixed
         * value, kept for exactly that case: a frame whose config cannot be read is
         * still worth watching, just conservatively.
         */
        private const val UNKNOWN_INTERVAL_MS = 3L * 60 * 1000

        /**
         * Advances a healthy frame can make without asking for anything.
         *
         * ImmichFrame prefetches the asset on screen plus `PRELOAD_ASSETS = 5`
         * ahead, so once fewer than five remain in its backlog every one of them is
         * already in memory — and the frame goes silent for the rest of the batch
         * while still changing pictures perfectly.
         */
        private const val PREFETCH_ADVANCES = 6

        /** Slack over [PREFETCH_ADVANCES], so a slow batch refill is not a stall. */
        private const val MARGIN_ADVANCES = 2

        /**
         * However quiet a frame is entitled to be, a wall that has actually gone
         * dark should not wait longer than this to say so.
         */
        private const val CEILING_MS = 15L * 60 * 1000

        /**
         * How long a frame on this [intervalSeconds] cadence may ask for nothing
         * before something is wrong.
         *
         * Derived rather than fixed because the old fixed three minutes sat *below*
         * the quiet a healthy frame produces on its own: at `Interval: 45` the
         * prefetch tail is 4m30s, so every batch ended in a stall verdict and a
         * reload — 22 of them in six hours on Milka's frame, 2026-08-23, while it
         * was showing photos the whole time.
         *
         * Pass null (or a value the server could not have meant) for the
         * conservative fallback.
         */
        fun silenceFor(intervalSeconds: Int?): Long {
            val interval = intervalSeconds?.takeIf { it > 0 } ?: return UNKNOWN_INTERVAL_MS
            val quietPlusSlack = (PREFETCH_ADVANCES + MARGIN_ADVANCES) * interval * 1000L
            return quietPlusSlack.coerceIn(UNKNOWN_INTERVAL_MS, CEILING_MS)
        }
    }
}
