package me.viktorbarzin.portalframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Turns the package installer's "I need a human" reply into an actual prompt, and
 * remembers the answer.
 *
 * Committing a session does NOT show the confirmation dialog. The installer answers
 * asynchronously with [PackageInstaller.STATUS_PENDING_USER_ACTION] and an Intent
 * that the app has to start itself; commit and then do nothing, and the update sits
 * there invisibly forever — the download succeeds, the log says "offering", and
 * nothing appears on the glass (seen on the emulator, 2026-08-15).
 */
class FrameInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, -1)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") // getParcelableExtra(String, Class) is API 33+; targetSdk 29.
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                // The frame is a full-screen kiosk activity, and this arrives from a
                // broadcast with no task of its own — without NEW_TASK the prompt has
                // nowhere to be shown.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }

            PackageInstaller.STATUS_SUCCESS ->
                // Rarely seen: the process is usually replaced before this arrives.
                // The frame is brought back by [FrameRelaunchReceiver] instead.
                Log.i(TAG, "update installed")

            else -> {
                Log.i(
                    TAG,
                    "install did not complete (status=$status): " +
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                )
                // Includes someone tapping Cancel, which is a legitimate answer. Back
                // off that version so the next check does not put the same dialog over
                // the photos again.
                if (versionCode > 0) {
                    FrameUpdater.schedule.declined(versionCode, System.currentTimeMillis())
                }
            }
        }
    }

    companion object {
        /** Private to this app; the receiver is not exported. */
        const val ACTION = "me.viktorbarzin.portalframe.INSTALL_STATUS"

        /** Which build this session is installing, so a decline can be attributed to it. */
        const val EXTRA_VERSION_CODE = "versionCode"

        private const val TAG = "FrameUpdater"
    }
}
