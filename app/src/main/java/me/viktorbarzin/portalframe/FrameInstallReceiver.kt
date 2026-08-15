package me.viktorbarzin.portalframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Turns the package installer's "I need a human" reply into an actual prompt.
 *
 * Committing a session does NOT show the confirmation dialog. The installer answers
 * asynchronously with [PackageInstaller.STATUS_PENDING_USER_ACTION] and an Intent
 * that the app has to start itself; commit and then do nothing, and the update sits
 * there invisibly forever — the download succeeds, the log says "offering", and
 * nothing appears on the glass (seen on the emulator, 2026-08-15).
 */
class FrameInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
                Log.i(TAG, "update installed")

            else ->
                // Includes the person declining the prompt, which is a legitimate
                // answer: the frame keeps running the build it already has.
                Log.i(
                    TAG,
                    "install did not complete (status=$status): " +
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                )
        }
    }

    companion object {
        /** Private to this app; the receiver is not exported. */
        const val ACTION = "me.viktorbarzin.portalframe.INSTALL_STATUS"
        private const val TAG = "FrameUpdater"
    }
}
