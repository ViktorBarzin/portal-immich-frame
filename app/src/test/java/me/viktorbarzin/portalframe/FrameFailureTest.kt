package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a failed frame load says on a wall-mounted screen.
 *
 * The frame is looked at, not debugged: whoever walks past it has no logs, no adb
 * and often no idea the device moved networks. Twice now a black screen has been
 * read as "the server is down" when the Portal had simply joined the wrong Wi-Fi,
 * so these strings exist to put the deciding fact — which network this device is
 * actually on — in front of that person.
 */
class FrameFailureTest {

    private val url = "https://highlights-immich.viktorbarzin.me"

    @Test
    fun `a 403 says the frame is limited to the home network`() {
        val f = FrameFailure.http(403, url)
        assertEquals("Not allowed from this network", f.headline)
        assertTrue(f.detail, f.detail.contains("home network"))
    }

    @Test
    fun `a 403 names the address the server saw, because that is the deciding fact`() {
        val f = FrameFailure.http(403, url)
        assertTrue(f.detail, f.detail.contains("HTTP 403"))
    }

    @Test
    fun `other http errors report their status rather than guessing a cause`() {
        assertEquals("The frame server returned an error", FrameFailure.http(500, url).headline)
        assertTrue(FrameFailure.http(500, url).detail.contains("HTTP 500"))
        assertTrue(FrameFailure.http(404, url).detail.contains("HTTP 404"))
    }

    @Test
    fun `a network failure is named as unreachable, not as an error from the server`() {
        val f = FrameFailure.network("net::ERR_CONNECTION_TIMED_OUT", url)
        assertEquals("Can't reach the frame", f.headline)
        assertTrue(f.detail, f.detail.contains("net::ERR_CONNECTION_TIMED_OUT"))
    }

    @Test
    fun `a network failure with no description still produces usable text`() {
        val f = FrameFailure.network(null, url)
        assertEquals("Can't reach the frame", f.headline)
        assertTrue(f.detail, f.detail.isNotBlank())
    }

    @Test
    fun `the target url is always shown — a mis-pointed device looks identical otherwise`() {
        assertTrue(FrameFailure.http(403, url).target.contains(url))
        assertTrue(FrameFailure.network("net::ERR_FAILED", url).target.contains(url))
    }

    @Test
    fun `the network line reports this device's address and gateway`() {
        val line = FrameFailure.networkLine(ip = "192.168.20.195", gateway = "192.168.20.1")
        assertTrue(line, line.contains("192.168.20.195"))
        assertTrue(line, line.contains("192.168.20.1"))
    }

    @Test
    fun `the network line degrades to whatever is known rather than vanishing`() {
        assertTrue(FrameFailure.networkLine(ip = "192.168.8.42", gateway = null).contains("192.168.8.42"))
        assertTrue(FrameFailure.networkLine(ip = null, gateway = "192.168.8.1").contains("192.168.8.1"))
    }

    @Test
    fun `with no address at all the network line says so instead of printing null`() {
        val line = FrameFailure.networkLine(ip = null, gateway = null)
        assertTrue(line, line.isNotBlank())
        assertTrue(line, !line.contains("null"))
    }

    @Test
    fun `the retry line counts attempts so a frozen screen is distinguishable from a retrying one`() {
        assertTrue(FrameFailure.retryLine(attempt = 1, seconds = 5).contains("5"))
        assertTrue(FrameFailure.retryLine(attempt = 12, seconds = 5).contains("12"))
    }

    @Test
    fun `http failures below 400 are not failures at all`() {
        assertNull(FrameFailure.httpOrNull(200, url))
        assertNull(FrameFailure.httpOrNull(304, url))
        assertNull(FrameFailure.httpOrNull(399, url))
    }

    @Test
    fun `http failures at 400 and above are reported`() {
        assertEquals(FrameFailure.http(403, url), FrameFailure.httpOrNull(403, url))
        assertEquals(FrameFailure.http(500, url), FrameFailure.httpOrNull(500, url))
    }
}
