package me.viktorbarzin.portalframe

/**
 * Paces the update check, and remembers a decline.
 *
 * Android-free so it can be unit-tested on the JVM, matching the rest of the
 * non-UI logic; [FrameUpdater] supplies the clock and does the work.
 *
 * Two things it protects. A check that finds a new build interrupts the photos
 * with an install prompt, so checking must be bounded rather than continuous —
 * and if someone answers that prompt with Cancel, coming straight back with it
 * would make the frame worse than not updating at all.
 */
class UpdateSchedule(private val intervalMs: Long) {

    private var lastCheckMs: Long? = null
    private var declinedVersion: Int? = null
    private var declinedAtMs: Long = 0

    /** True when enough time has passed (or nothing has been checked yet). */
    fun shouldCheck(nowMs: Long): Boolean {
        val last = lastCheckMs ?: return true
        // A backwards clock jump — an NTP correction after boot, on a device with
        // no clock worth trusting — would otherwise read as "checked in the future"
        // and suppress every future check.
        if (nowMs < last) return true
        return nowMs - last >= intervalMs
    }

    fun checked(nowMs: Long) {
        lastCheckMs = nowMs
    }

    /** How long until the next check is due; never <= 0, so a repeating post cannot spin. */
    fun delayUntilNextMs(nowMs: Long): Long {
        val last = lastCheckMs ?: return intervalMs
        val elapsed = nowMs - last
        if (elapsed < 0) return intervalMs
        return (intervalMs - elapsed).coerceAtLeast(1L)
    }

    /** Record that someone answered the prompt for [versionCode] with Cancel. */
    fun declined(versionCode: Int, nowMs: Long) {
        declinedVersion = versionCode
        declinedAtMs = nowMs
    }

    /** True unless [versionCode] was declined recently. A newer build is always offered. */
    fun mayOffer(versionCode: Int, nowMs: Long): Boolean {
        if (declinedVersion != versionCode) return true
        val since = nowMs - declinedAtMs
        return since < 0 || since >= DECLINE_BACKOFF_MS
    }

    companion object {
        /** Long enough that a decline means "not today", short enough to not be a veto. */
        const val DECLINE_BACKOFF_MS = 24L * 60 * 60 * 1000
    }
}
