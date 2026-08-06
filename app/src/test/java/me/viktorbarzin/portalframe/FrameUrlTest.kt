package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame URL is the one thing a Portal can be re-pointed at in the field, by
 * someone holding an adb cable and not much else. So [FrameUrl.sanitize] is the
 * gate: anything it accepts gets loaded into a JavaScript-enabled kiosk WebView
 * and persisted across reboots, and anything it rejects must leave the running
 * frame untouched.
 */
class FrameUrlTest {

    @Test
    fun `accepts an https url`() {
        assertEquals(
            "https://highlights-immich.viktorbarzin.me",
            FrameUrl.sanitize("https://highlights-immich.viktorbarzin.me")
        )
    }

    @Test
    fun `accepts a plain http url — the frames are LAN-only, TLS is not guaranteed`() {
        assertEquals("http://10.0.20.203", FrameUrl.sanitize("http://10.0.20.203"))
    }

    @Test
    fun `accepts a url with a port and a path`() {
        assertEquals(
            "http://192.168.1.50:8080/frame",
            FrameUrl.sanitize("http://192.168.1.50:8080/frame")
        )
    }

    @Test
    fun `trims surrounding whitespace — adb extras arrive padded`() {
        assertEquals(
            "https://frame.example",
            FrameUrl.sanitize("  https://frame.example\n")
        )
    }

    @Test
    fun `normalises the scheme to lower case`() {
        assertEquals("https://frame.example", FrameUrl.sanitize("HTTPS://frame.example"))
    }

    @Test
    fun `rejects null and blank`() {
        assertNull(FrameUrl.sanitize(null))
        assertNull(FrameUrl.sanitize(""))
        assertNull(FrameUrl.sanitize("   "))
    }

    @Test
    fun `rejects javascript — it would execute in the kiosk WebView`() {
        assertNull(FrameUrl.sanitize("javascript:alert(1)"))
        assertNull(FrameUrl.sanitize("JavaScript:alert(1)"))
    }

    @Test
    fun `rejects file and content urls — they would expose local storage`() {
        assertNull(FrameUrl.sanitize("file:///sdcard/secrets.txt"))
        assertNull(FrameUrl.sanitize("content://media/external/images"))
    }

    @Test
    fun `rejects other schemes`() {
        assertNull(FrameUrl.sanitize("ftp://frame.example"))
        assertNull(FrameUrl.sanitize("data:text/html,<h1>x"))
        assertNull(FrameUrl.sanitize("about:blank"))
    }

    @Test
    fun `rejects a scheme with no host`() {
        assertNull(FrameUrl.sanitize("https://"))
        assertNull(FrameUrl.sanitize("http:///path"))
    }

    @Test
    fun `rejects a bare hostname — the scheme must be explicit`() {
        assertNull(FrameUrl.sanitize("highlights-immich.viktorbarzin.me"))
    }

    @Test
    fun `rejects embedded whitespace, which would smuggle a second token`() {
        assertNull(FrameUrl.sanitize("https://frame.example /x"))
    }

    @Test
    fun `recognises the reset sentinel, case-insensitively`() {
        assertTrue(FrameUrl.isReset("default"))
        assertTrue(FrameUrl.isReset("DEFAULT"))
        assertTrue(FrameUrl.isReset("  default  "))
    }

    @Test
    fun `does not mistake a real url or nothing at all for the reset sentinel`() {
        assertFalse(FrameUrl.isReset("https://frame.example"))
        assertFalse(FrameUrl.isReset(null))
        assertFalse(FrameUrl.isReset(""))
    }

    @Test
    fun `the reset sentinel is not a valid url`() {
        assertNull(FrameUrl.sanitize(FrameUrl.RESET))
    }
}
