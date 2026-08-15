package me.viktorbarzin.portalframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Brings the frame back after it updates itself.
 *
 * Android stops an app to replace it and does not start it again. On a phone that
 * is correct; on a wall-mounted photo frame it means the update turns the display
 * off — the Portal lands on its own launcher and stays there until someone walks
 * up and opens the app (observed on the London Portal+, 2026-08-15). An update
 * that leaves the frame dark is worse than no update.
 *
 * `ACTION_MY_PACKAGE_REPLACED` is delivered to the NEW build, once, after it is
 * installed. Starting an activity from it is a background activity start, which
 * Android 10 restricts — the device needs the SYSTEM_ALERT_WINDOW app-op, granted
 * during provisioning (infra/scripts/provision-portal.sh). Without it the frame
 * stays down and this receiver logs the attempt rather than failing silently.
 */
class FrameRelaunchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = Intent(context, FrameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
            Log.i(TAG, "relaunched the frame after update")
        } catch (e: Exception) {
            // Most likely the background-activity-start restriction. Say so loudly:
            // the visible symptom is a Portal sitting on its launcher, which looks
            // like the update failed rather than like a missing app-op.
            Log.w(TAG, "could not relaunch after update — check the SYSTEM_ALERT_WINDOW app-op: $e")
        }
    }

    private companion object {
        const val TAG = "FrameUpdater"
    }
}
