package me.viktorbarzin.portalframe

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Whether a published build should replace the running one, and the rules a
 * published build must satisfy before it is allowed near the package installer.
 *
 * Android-free apart from `org.json` (which ships with the platform and is
 * available to JVM unit tests), matching [FrameUrl] and [FrameFailure]; the
 * network and install side lives in [FrameUpdater].
 *
 * Every check here fails towards "keep showing photos". These frames hang on walls
 * in two countries and are re-provisioned by hand, so a bad update costs far more
 * than a missed one — a malformed manifest, an odd scheme, a mismatched checksum
 * or a non-newer version all mean "do nothing", never "try anyway".
 */
object FrameUpdate {

    /** A published build, as described by the update manifest. */
    data class Available(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String,
    )

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    /**
     * The manifest at the update URL, or `null` if it is anything other than a
     * well-formed description of a downloadable build. Returning null rather than
     * throwing matters: a captive portal answering with an HTML login page is a
     * completely ordinary thing for a Portal to receive.
     */
    fun parse(json: String): Available? {
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }

        val versionCode = obj.optInt("versionCode", -1).takeIf { it > 0 } ?: return null
        val versionName = obj.optString("versionName").takeIf { it.isNotBlank() } ?: return null
        // Same scheme allow-list as the frame URL, and for the same reason: this
        // names a file we are about to execute.
        val url = FrameUrl.sanitize(obj.optString("url")) ?: return null
        val sha256 = obj.optString("sha256").lowercase().takeIf { SHA256_HEX.matches(it) } ?: return null

        return Available(versionCode, versionName, url, sha256)
    }

    /** True only for a strictly newer build — a rolled-back manifest must never undo a fix. */
    fun shouldInstall(installed: Int, available: Available): Boolean =
        available.versionCode > installed

    /** Lower-case hex SHA-256 of [bytes]. */
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** True when [bytes] hashes to [expected]; case-insensitive, since tooling disagrees. */
    fun matches(bytes: ByteArray, expected: String): Boolean =
        sha256(bytes).equals(expected, ignoreCase = true)
}
