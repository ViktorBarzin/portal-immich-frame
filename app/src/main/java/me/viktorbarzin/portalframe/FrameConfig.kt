package me.viktorbarzin.portalframe

import org.json.JSONObject

/**
 * The one setting the app needs from the frame server: how often photos change.
 *
 * Android-free apart from `org.json`, matching [FrameUrl], [FrameFailure] and
 * [FrameUpdate]; the network side lives in [FrameView].
 *
 * **Why the app cares about a server setting.** [FrameHealth] decides a frame is
 * broken when it stops asking for photos, so it has to know how long "quiet" is
 * allowed to be — and that follows directly from the photo cadence. Hardcoding a
 * guess is what made a healthy frame look stalled: `Interval` is per-frame config
 * (30 on the London frame, 45 on Sofia's and Milka's), and a threshold that suits
 * one does not suit the other.
 *
 * Every unreadable answer returns null rather than throwing, and the caller falls
 * back to the conservative fixed threshold. A frame whose config cannot be read is
 * still worth watching; it just gets watched the old way.
 */
object FrameConfig {

    /**
     * Seconds between photos, per `/api/Config`, or null if the answer was not a
     * config that named a sane interval.
     *
     * A captive portal answering with an HTML login page is completely ordinary for
     * a Portal, so "not JSON" is an expected outcome, not an error.
     */
    fun parseIntervalSeconds(json: String): Int? {
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }

        // optInt returns the fallback for a missing key AND for a value that is not
        // a number, which is exactly the treatment both deserve here.
        return obj.optInt("interval", -1).takeIf { it > 0 }
    }

    /** Where [parseIntervalSeconds] input comes from, given the frame's base URL. */
    fun endpointFor(frameUrl: String): String = frameUrl.trimEnd('/') + "/api/Config"
}
