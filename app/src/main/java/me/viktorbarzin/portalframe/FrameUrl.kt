package me.viktorbarzin.portalframe

import java.net.URI
import java.net.URISyntaxException

/**
 * Validation for the frame URL — the only thing this app can be re-pointed at.
 *
 * Deliberately free of Android types so it can be unit-tested on the JVM: the
 * Android-coupled part (persistence) lives in [FrameUrlStore] and stays dumb.
 *
 * The bar is set by where the result ends up: a JavaScript-enabled kiosk WebView
 * on a wall-mounted device, persisted across reboots. So the scheme is allow-listed
 * to http/https rather than merely rejecting known-bad ones — `javascript:` would
 * execute, and `file:`/`content:` would read local storage.
 */
object FrameUrl {

    /** Value of the `frameUrl` extra that clears an override, restoring the build default. */
    const val RESET = "default"

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** True when [raw] asks to fall back to the built-in default rather than set a URL. */
    fun isReset(raw: String?): Boolean =
        raw?.trim().equals(RESET, ignoreCase = true)

    /**
     * The normalised form of [raw], or `null` if it is not something this frame may
     * load. Normalisation is limited to trimming and lower-casing the scheme; the
     * rest of the caller's string is preserved so an intentional path or port
     * survives untouched.
     */
    fun sanitize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        // Embedded whitespace would let a second token ride along in one extra.
        if (trimmed.any(Char::isWhitespace)) return null

        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return null
        }

        // A bare hostname parses as a relative URI with no scheme — reject it rather
        // than guessing https, so what's on the wall is always what was typed.
        val rawScheme = uri.scheme ?: return null
        val scheme = rawScheme.lowercase()
        if (scheme !in ALLOWED_SCHEMES) return null
        if (uri.host.isNullOrEmpty()) return null

        return scheme + trimmed.substring(rawScheme.length)
    }
}
