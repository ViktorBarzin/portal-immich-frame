package me.viktorbarzin.portalframe

/**
 * What the frame puts on screen when it cannot show photos.
 *
 * Deliberately free of Android types so it can be unit-tested on the JVM, matching
 * [FrameUrl]; the Android-coupled part (drawing it, reading the device address)
 * lives in [FrameView].
 *
 * The audience is whoever happens to walk past a wall-mounted screen — no logs, no
 * adb, and no reason to suspect the device changed networks. The two failures seen
 * in the field look identical from the sofa but have opposite causes: the Portal
 * roaming onto a Wi-Fi with no route to the frame (a timeout), and the frame server
 * refusing a source address outside the home LANs (an HTTP 403). So the text always
 * carries the target URL and this device's own address, which is what separates them.
 */
data class FrameFailure(
    val headline: String,
    val detail: String,
    val target: String,
) {
    companion object {

        /** Status codes below 400 are outcomes, not failures — 200 and 304 both mean "shown". */
        private const val FIRST_ERROR_STATUS = 400

        /** The frame server answered, but with an error. */
        fun http(status: Int, url: String): FrameFailure = FrameFailure(
            headline = if (status == 403) {
                "Not allowed from this network"
            } else {
                "The frame server returned an error"
            },
            detail = if (status == 403) {
                // The allowlist is the only thing that answers 403 on these hosts, so
                // this is a statement of fact rather than a guess — see the infra repo,
                // docs/plans/2026-07-04-immich-frame-lan-only-design.md.
                "HTTP 403 — the frame is limited to the home network, and this " +
                    "device's address is not on it."
            } else {
                "HTTP $status from the frame server."
            },
            target = "Frame: $url",
        )

        /** [http] unless [status] is a success/redirect, which is not a failure at all. */
        fun httpOrNull(status: Int, url: String): FrameFailure? =
            if (status >= FIRST_ERROR_STATUS) http(status, url) else null

        /**
         * The frame server was never reached — no route, no DNS, refused, timed out.
         * [description] is the WebView's own text (e.g. `net::ERR_CONNECTION_TIMED_OUT`),
         * kept verbatim because it is the one machine-readable clue on screen.
         */
        fun network(description: String?, url: String): FrameFailure = FrameFailure(
            headline = "Can't reach the frame",
            detail = description?.takeIf { it.isNotBlank() }
                ?.let { "$it — nothing answered at this address." }
                ?: "Nothing answered at this address.",
            target = "Frame: $url",
        )

        /**
         * The page loaded and then stopped showing photos ([FrameHealth]).
         *
         * Worded apart from the other two on purpose. "Can't reach the frame" would
         * send whoever reads it hunting for a server outage, and the server is
         * usually fine — what changed is this device's network, after the page had
         * already been loaded from cache. [silentForMs] is the deciding fact: it
         * dates the failure, which a screen full of photos-that-aren't cannot.
         */
        fun stalled(url: String, silentForMs: Long): FrameFailure = FrameFailure(
            headline = "The frame stopped showing photos",
            detail = "No photo has loaded for ${minutes(silentForMs)}.",
            target = "Frame: $url",
        )

        /** Whole minutes, never "0 minutes" — a stall is always at least a minute old. */
        private fun minutes(ms: Long): String {
            val whole = (ms / 60_000).coerceAtLeast(1)
            return if (whole == 1L) "1 minute" else "$whole minutes"
        }

        /**
         * This device's own place on the network. The deciding fact when a Portal has
         * roamed: a frame sitting on some other subnet entirely has joined a nearby
         * network, not the home LAN, and no amount of server-side debugging shows it.
         */
        fun networkLine(ip: String?, gateway: String?): String {
            val parts = listOfNotNull(
                ip?.takeIf { it.isNotBlank() }?.let { "This device: $it" },
                gateway?.takeIf { it.isNotBlank() }?.let { "gateway $it" },
            )
            return if (parts.isEmpty()) "This device: no network connection" else parts.joinToString(", ")
        }

        /** Proof the frame is still trying, so a stuck screen reads differently from a retrying one. */
        fun retryLine(attempt: Int, seconds: Int): String =
            "Retrying every ${cadence(seconds)} — attempt $attempt"

        /** Minutes once they divide evenly: "every 180s" is arithmetic for the reader. */
        private fun cadence(seconds: Int): String =
            if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60}min" else "${seconds}s"
    }
}
