package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the frame is allowed to look for a new build.
 *
 * Checking only at startup was almost the same as never checking: a frame is
 * opened once and left running for weeks on a wall, so in practice the only
 * trigger was a Portal reboot. This adds a repeat while the app runs.
 *
 * The rate limit is not politeness towards GitHub — it is about what a check can
 * cost the viewer. A check that finds something interrupts the photos with an
 * install prompt, so it must not be able to fire in a tight loop, and it must not
 * fire again immediately after a decline.
 */
class UpdateScheduleTest {

    private val interval = 6 * 60 * 60 * 1000L // 6h

    @Test
    fun `the first check happens straight away`() {
        val s = UpdateSchedule(intervalMs = interval)
        assertTrue(s.shouldCheck(nowMs = 0L))
    }

    @Test
    fun `a second check is refused until the interval has passed`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.checked(nowMs = 1_000L)
        assertFalse(s.shouldCheck(nowMs = 1_000L))
        assertFalse(s.shouldCheck(nowMs = 1_000L + interval - 1))
    }

    @Test
    fun `a check is allowed once the interval has passed`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.checked(nowMs = 1_000L)
        assertTrue(s.shouldCheck(nowMs = 1_000L + interval))
        assertTrue(s.shouldCheck(nowMs = 1_000L + interval * 3))
    }

    @Test
    fun `a clock that jumps backwards does not lock checking out forever`() {
        // The Portal has no battery-backed clock worth trusting; an NTP correction
        // after boot can move time backwards. Treating that as "checked in the
        // future" would suppress every future check.
        val s = UpdateSchedule(intervalMs = interval)
        s.checked(nowMs = 10 * interval)
        assertTrue(s.shouldCheck(nowMs = 5L))
    }

    @Test
    fun `the delay until the next check is the remaining interval`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.checked(nowMs = 0L)
        assertEquals(interval, s.delayUntilNextMs(nowMs = 0L))
        assertEquals(interval / 2, s.delayUntilNextMs(nowMs = interval / 2))
    }

    @Test
    fun `the delay is never negative or zero, so a repeating post cannot spin`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.checked(nowMs = 0L)
        assertTrue(s.delayUntilNextMs(nowMs = interval) > 0)
        assertTrue(s.delayUntilNextMs(nowMs = interval * 10) > 0)
    }

    @Test
    fun `a declined update is not re-offered on the very next check`() {
        // Someone tapping Cancel is answering "not now". Coming straight back with
        // the same prompt would make the frame unusable rather than persistent.
        val s = UpdateSchedule(intervalMs = interval)
        s.declined(versionCode = 11, nowMs = 0L)
        assertFalse(s.mayOffer(versionCode = 11, nowMs = interval))
    }

    @Test
    fun `a declined update is offered again after the back-off`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.declined(versionCode = 11, nowMs = 0L)
        assertTrue(s.mayOffer(versionCode = 11, nowMs = UpdateSchedule.DECLINE_BACKOFF_MS))
    }

    @Test
    fun `declining one version does not suppress a different, newer one`() {
        val s = UpdateSchedule(intervalMs = interval)
        s.declined(versionCode = 11, nowMs = 0L)
        assertTrue(s.mayOffer(versionCode = 12, nowMs = interval))
    }

    @Test
    fun `nothing is suppressed when nothing has been declined`() {
        val s = UpdateSchedule(intervalMs = interval)
        assertTrue(s.mayOffer(versionCode = 11, nowMs = 0L))
    }
}
