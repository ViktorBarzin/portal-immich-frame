package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the failure panel may be taken off screen.
 *
 * This exists because of a bug that only showed up on a device: for an HTTP error
 * WebView calls `onReceivedHttpError` BEFORE `onPageStarted` for the error document,
 * so anything that resets state in `onPageStarted` erases the failure that was just
 * recorded, and the following `onPageFinished` reads "clean load" and hides the
 * panel. On the emulator the panel appeared and vanished 130ms later, leaving the
 * black screen and broken-image robot this whole change set out to replace
 * (observed 2026-08-15).
 *
 * So the reset point is a load WE start, never a callback WebView hands us.
 */
class FrameLoadStateTest {

    private val failure = FrameFailure.http(403, "https://frame.example")

    @Test
    fun `a load with no error is a success and the panel comes down`() {
        val s = FrameLoadState()
        s.startingLoad()
        assertTrue(s.finishedSuccessfully())
    }

    @Test
    fun `an http error survives the page-finished that follows it`() {
        val s = FrameLoadState()
        s.startingLoad()
        s.failed(failure)
        assertFalse(s.finishedSuccessfully())
    }

    @Test
    fun `an error reported before the load is even announced still counts`() {
        // The real observed order: error, then onPageStarted for the error document.
        // We no longer listen to onPageStarted, so this is simply "error then finish".
        val s = FrameLoadState()
        s.startingLoad()
        s.failed(failure)
        assertFalse(s.finishedSuccessfully())
        assertFalse(s.finishedSuccessfully())
    }

    @Test
    fun `the next load we start clears the previous failure`() {
        val s = FrameLoadState()
        s.startingLoad()
        s.failed(failure)
        s.startingLoad()
        assertTrue(s.finishedSuccessfully())
    }

    @Test
    fun `consecutive failures count up so the panel can show a real streak`() {
        val s = FrameLoadState()
        s.startingLoad(); assertEquals(1, s.failed(failure))
        s.startingLoad(); assertEquals(2, s.failed(failure))
        s.startingLoad(); assertEquals(3, s.failed(failure))
    }

    @Test
    fun `a success resets the streak, so a later blip starts from one`() {
        val s = FrameLoadState()
        s.startingLoad(); s.failed(failure)
        s.startingLoad(); s.failed(failure)
        s.startingLoad(); assertTrue(s.finishedSuccessfully())
        s.startingLoad(); assertEquals(1, s.failed(failure))
    }

    @Test
    fun `two errors in one load do not double-count the streak`() {
        // A main-frame error can be reported more than once for a single navigation;
        // the panel should say "attempt 1", not "attempt 2".
        val s = FrameLoadState()
        s.startingLoad()
        assertEquals(1, s.failed(failure))
        assertEquals(1, s.failed(failure))
    }

    @Test
    fun `the last failure is what the panel renders`() {
        val network = FrameFailure.network("net::ERR_CONNECTION_TIMED_OUT", "https://frame.example")
        val s = FrameLoadState()
        s.startingLoad()
        s.failed(failure)
        assertEquals(failure, s.current)
        s.startingLoad()
        s.failed(network)
        assertEquals(network, s.current)
    }
}
