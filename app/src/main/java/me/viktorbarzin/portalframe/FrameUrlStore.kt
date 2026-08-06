package me.viktorbarzin.portalframe

import android.content.Context

/**
 * Where the frame URL actually comes from at runtime: a persisted override if one
 * has been set on this device, otherwise the URL baked in at build time.
 *
 * This exists so a Portal can be pointed at a different ImmichFrame without a
 * rebuild — adding a device is a one-line adb command, not a recompile (ADR-0005).
 *
 * Kept deliberately thin: all the judgement lives in [FrameUrl], which is pure and
 * unit-tested. This class only reads and writes.
 */
class FrameUrlStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The URL the frame should load right now. Never null — falls back to the build default. */
    fun current(): String = prefs.getString(KEY_FRAME_URL, null) ?: BuildConfig.FRAME_URL

    /**
     * Apply a `frameUrl` extra, if one was supplied. Returns what happened so the
     * caller can tell whoever is standing at the device — silently ignoring a
     * typo'd URL would look identical to the frame being broken.
     */
    fun apply(raw: String?): Outcome {
        if (raw == null) return Outcome.Unchanged

        if (FrameUrl.isReset(raw)) {
            // commit(), not apply(): this is a one-shot config action that the person
            // running it may follow with an immediate power-cycle, so the write must
            // have landed before we report success.
            prefs.edit().remove(KEY_FRAME_URL).commit()
            return Outcome.Reset(BuildConfig.FRAME_URL)
        }

        val url = FrameUrl.sanitize(raw) ?: return Outcome.Rejected(raw)
        prefs.edit().putString(KEY_FRAME_URL, url).commit()
        return Outcome.Set(url)
    }

    /** The result of an [apply] call. */
    sealed interface Outcome {
        /** No `frameUrl` extra was supplied; whatever was configured still stands. */
        data object Unchanged : Outcome

        /** An override was stored. */
        data class Set(val url: String) : Outcome

        /** The override was cleared; the frame is back on the build default. */
        data class Reset(val url: String) : Outcome

        /** [raw] is not a URL this frame may load; the previous setting is untouched. */
        data class Rejected(val raw: String) : Outcome
    }

    private companion object {
        const val PREFS_NAME = "frame"
        const val KEY_FRAME_URL = "frame_url"
    }
}
