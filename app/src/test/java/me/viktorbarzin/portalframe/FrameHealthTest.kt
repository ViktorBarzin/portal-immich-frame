package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a loaded page has stopped being a photo frame.
 *
 * The failure this exists for: the Portal joined a Wi-Fi with no route home, the
 * shell came out of the WebView's cache so the navigation *succeeded*, and the page
 * then sat there having made no request at all — measured on the London Portal
 * 2026-08-18, zero network events in 75 s. Nothing in a navigation callback can see
 * that, so the frame has to notice its own silence.
 */
class FrameHealthTest {

    private val silence = 3L * 60 * 1000

    @Test
    fun `a frame that keeps asking for photos never stalls`() {
        val health = FrameHealth(silence)
        health.watch(0)
        var now = 0L
        repeat(20) {
            now += 30_000
            health.photoRequested(now)
            assertEquals(FrameHealth.Verdict.Healthy, health.check(now))
        }
    }

    @Test
    fun `silence for the threshold stalls, quietly the first time`() {
        val health = FrameHealth(silence)
        health.watch(0)
        assertEquals(FrameHealth.Verdict.Healthy, health.check(silence - 1))

        val verdict = health.check(silence)
        assertTrue("$verdict", verdict is FrameHealth.Verdict.Stalled)
        verdict as FrameHealth.Verdict.Stalled
        assertEquals(1, verdict.attempt)
        // A reload alone fixes most stalls, and nobody should watch an error panel
        // appear on the wall for something that healed itself.
        assertTrue("first stall must not draw a panel", !verdict.explain)
    }

    @Test
    fun `a second silence after that reload is worth explaining`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.check(silence)

        val verdict = health.check(silence * 2) as FrameHealth.Verdict.Stalled
        assertEquals(2, verdict.attempt)
        assertTrue("a reload that did not bring photos back must be visible", verdict.explain)
    }

    @Test
    fun `checks between stalls do nothing — the cadence is the threshold`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.check(silence)

        assertEquals(FrameHealth.Verdict.Healthy, health.check(silence + 1))
        assertEquals(FrameHealth.Verdict.Healthy, health.check(silence * 2 - 1))
    }

    @Test
    fun `one photo clears the streak, so the next stall is quiet again`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.check(silence)
        health.check(silence * 2)

        health.photoRequested(silence * 2 + 5_000)

        val verdict = health.check(silence * 3 + 5_000) as FrameHealth.Verdict.Stalled
        assertEquals(1, verdict.attempt)
        assertTrue(!verdict.explain)
    }

    @Test
    fun `the silent time is measured from the last photo, not the last reload`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.photoRequested(60_000)

        health.check(60_000 + silence)
        val verdict = health.check(60_000 + silence * 2) as FrameHealth.Verdict.Stalled

        // Two stalls in, the honest number is six minutes since a photo — not the
        // three since the reload, which is what the panel would say if the reload
        // reset the clock.
        assertEquals(silence * 2, verdict.silentForMs)
    }

    @Test
    fun `nothing stalls while the frame is off screen`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.unwatch()

        // A paused WebView makes no requests, so its silence says nothing at all.
        assertEquals(FrameHealth.Verdict.Healthy, health.check(silence * 10))
    }

    @Test
    fun `coming back on screen restarts the clock instead of stalling at once`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.unwatch()

        health.watch(silence * 10)

        assertEquals(FrameHealth.Verdict.Healthy, health.check(silence * 10 + 1))
        assertTrue(health.check(silence * 11) is FrameHealth.Verdict.Stalled)
    }

    @Test
    fun `a fresh watch also clears the streak`() {
        val health = FrameHealth(silence)
        health.watch(0)
        health.check(silence)
        health.check(silence * 2)

        health.watch(silence * 2)

        val verdict = health.check(silence * 3) as FrameHealth.Verdict.Stalled
        assertEquals(1, verdict.attempt)
    }

    @Test
    fun `a frame walking through its prefetched photos is not stalled`() {
        // THE REGRESSION THIS FILE EXISTS TO PIN. ImmichFrame fetches a batch of 25
        // assets, then prefetches the one on screen plus PRELOAD_ASSETS=5 ahead. So
        // once fewer than five are left in the backlog, everything remaining is
        // already in memory and the frame asks for NOTHING for the last six advances
        // of every batch. At `Interval: 45` that is a legitimate 4m30s of quiet.
        //
        // Measured on Milka's frame 2026-08-23: the old fixed three-minute threshold
        // sat below that, so a stall was declared on every batch — 22 reloads in six
        // hours, on a frame that was showing photos perfectly the whole time.
        val health = FrameHealth(FrameHealth.silenceFor(45))
        health.watch(0)

        val quietTail = 6 * 45_000L
        assertEquals(FrameHealth.Verdict.Healthy, health.check(quietTail))
    }

    @Test
    fun `the threshold clears the frame's own prefetch quiet, with room to spare`() {
        for (interval in listOf(30, 45, 60)) {
            val quietTail = 6L * interval * 1000
            assertTrue(
                "interval $interval: threshold must sit above the frame's own quiet",
                FrameHealth.silenceFor(interval) > quietTail,
            )
        }
    }

    @Test
    fun `an unknown interval falls back to the original threshold`() {
        // No config answer — a frame that cannot be asked is still worth watching,
        // just on the conservative old value rather than on nothing.
        assertEquals(3L * 60 * 1000, FrameHealth.silenceFor(null))
    }

    @Test
    fun `an interval the server could not have meant is treated as unknown`() {
        assertEquals(FrameHealth.silenceFor(null), FrameHealth.silenceFor(0))
        assertEquals(FrameHealth.silenceFor(null), FrameHealth.silenceFor(-45))
    }

    @Test
    fun `a very long interval cannot leave a dark wall unexplained for an hour`() {
        // Someone setting a ten-minute interval must not buy an eighty-minute wait
        // before the frame says anything.
        assertTrue(FrameHealth.silenceFor(600) <= 15L * 60 * 1000)
    }

    @Test
    fun `retuning takes effect and starts the clock again`() {
        val health = FrameHealth(3L * 60 * 1000)
        health.watch(0)

        health.retune(FrameHealth.silenceFor(45), 0)

        // The old threshold would have called this a stall; the tuned one must not.
        assertEquals(FrameHealth.Verdict.Healthy, health.check(6 * 45_000L))
        assertTrue(health.check(FrameHealth.silenceFor(45)) is FrameHealth.Verdict.Stalled)
    }

    @Test
    fun `a backwards clock jump re-baselines instead of stalling on a bogus gap`() {
        val health = FrameHealth(silence)
        health.watch(1_000_000)

        // NTP correcting a Portal's clock after boot. Reading the gap literally would
        // mean a negative silent time on the panel, or a stall the moment time moves.
        assertEquals(FrameHealth.Verdict.Healthy, health.check(1_000))
        assertEquals(FrameHealth.Verdict.Healthy, health.check(1_000 + silence - 1))
        assertTrue(health.check(1_000 + silence) is FrameHealth.Verdict.Stalled)
    }
}
