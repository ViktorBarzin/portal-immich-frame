package me.viktorbarzin.portalframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Brings the frame up after the device boots.
 *
 * A wall-mounted frame has nobody standing at it, so a power cut used to end with
 * the Portal sitting on its own launcher indefinitely — the display is on, the
 * device is on the network, and the photos are simply gone until someone travels
 * to it and taps the icon. That happened on the Valchedrym Portal on 2026-08-22,
 * where the same reboot also cleared the ADB toggle, so there was no remote way
 * back in either.
 *
 * Mirrors [FrameRelaunchReceiver]: starting an activity from a broadcast is a
 * background activity start, which Android 10 restricts, so the device needs the
 * SYSTEM_ALERT_WINDOW app-op (granted during provisioning). Failure is logged
 * loudly rather than swallowed — a frame that stays dark after a reboot otherwise
 * looks like a broken app instead of a missing app-op.
 *
 * `QUICKBOOT_POWERON` is included because some vendor builds emit it in place of
 * `BOOT_COMPLETED` when coming out of a fast-boot state.
 */
class FrameBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        val launch = Intent(context, FrameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
            Log.i(TAG, "started the frame after boot (${intent.action})")
        } catch (e: Exception) {
            Log.w(TAG, "could not start after boot — check the SYSTEM_ALERT_WINDOW app-op: $e")
        }
    }

    private companion object {
        const val TAG = "FrameBoot"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
