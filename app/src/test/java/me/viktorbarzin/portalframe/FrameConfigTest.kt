package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading the one setting the app needs out of the frame server's config.
 *
 * The frame's photo cadence lives on the server, and until now the app only
 * guessed at it. It decides how long a healthy frame may legitimately go quiet,
 * so guessing wrong is what made a working frame look stalled.
 */
class FrameConfigTest {

    @Test
    fun `the interval is read from the frame's own config`() {
        // Trimmed from the real answer of /api/Config on Milka's frame.
        val json = """{"language":"bg","interval":45,"transitionDuration":1,"showClock":true}"""

        assertEquals(45, FrameConfig.parseIntervalSeconds(json))
    }

    @Test
    fun `a captive portal's login page is not a config`() {
        // Entirely ordinary for a Portal to receive, and it must not be read as one.
        assertNull(FrameConfig.parseIntervalSeconds("<!doctype html><html>Sign in</html>"))
    }

    @Test
    fun `a config with no interval leaves the decision to the caller`() {
        assertNull(FrameConfig.parseIntervalSeconds("""{"language":"bg"}"""))
    }

    @Test
    fun `a nonsensical interval is refused rather than believed`() {
        assertNull(FrameConfig.parseIntervalSeconds("""{"interval":0}"""))
        assertNull(FrameConfig.parseIntervalSeconds("""{"interval":-45}"""))
        assertNull(FrameConfig.parseIntervalSeconds("""{"interval":"soon"}"""))
    }
}
