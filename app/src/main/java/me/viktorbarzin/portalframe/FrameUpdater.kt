package me.viktorbarzin.portalframe

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the published build on startup and offers it to the package installer.
 *
 * **What this can and cannot do on a Portal.** An ordinary Android app cannot
 * install silently: that needs a system-signed app or device-owner provisioning,
 * and device-owner requires a factory reset with no accounts on the device, which
 * the official Portal path rules out. So the last step is always Android's own
 * "Update app?" dialog and one tap on the touchscreen. Everything before it —
 * noticing, downloading, verifying — happens on its own, which is the part that
 * used to mean a USB cable and a trip to Sofia.
 *
 * Two things must be true on a device before this works, both one-time and both
 * settable over adb with no Portal UI (see the reprovision runbook):
 *   adb shell appops set me.viktorbarzin.portalframe REQUEST_INSTALL_PACKAGES allow
 * and the installed build must be signed with the same key as the published one,
 * which is why the keystore is held in Vault rather than only in a docker volume.
 *
 * Failure is always silent and total: any error leaves the frame showing photos.
 * An update that doesn't happen is a nuisance; a frame that stops showing photos
 * because an update went wrong is a wall-mounted black rectangle in another country.
 */
class FrameUpdater(
    private val context: Context,
    private val manifestUrl: String,
    private val installedVersionCode: Int,
) {

    /** Check, download and offer an update without blocking the caller. */
    fun checkInBackground() {
        if (manifestUrl.isBlank()) return
        Thread({ runCatching(::check) }, "frame-updater").apply { isDaemon = true }.start()
    }

    private fun check() {
        val manifest = FrameUpdate.parse(fetch(manifestUrl).decodeToString()) ?: run {
            Log.i(TAG, "no usable update manifest at $manifestUrl")
            return
        }
        if (!FrameUpdate.shouldInstall(installedVersionCode, manifest)) {
            Log.i(TAG, "up to date (installed $installedVersionCode, published ${manifest.versionCode})")
            return
        }

        val apk = fetch(manifest.url)
        if (!FrameUpdate.matches(apk, manifest.sha256)) {
            // Refusing here is the whole point of publishing a checksum: a truncated
            // download or a substituted file must never reach the installer.
            Log.w(TAG, "checksum mismatch for ${manifest.versionName} — refusing to install")
            return
        }
        Log.i(TAG, "offering ${manifest.versionName} (${apk.size} bytes)")
        offer(apk, manifest)
    }

    /** GET [url] whole. Bounded timeouts: a hung update must never outlive a photo cycle. */
    private fun fetch(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return ByteArray(0)
            val out = ByteArrayOutputStream()
            conn.inputStream.use { it.copyTo(out) }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    /** Hand the verified APK to the platform installer, which asks the human. */
    private fun offer(apk: ByteArray, manifest: FrameUpdate.Available) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("frame.apk", 0, apk.size.toLong()).use { out ->
                out.write(apk)
                session.fsync(out)
            }
            // A BROADCAST, not an activity: commit() replies asynchronously with
            // STATUS_PENDING_USER_ACTION and an Intent that must be started to show
            // the prompt. [FrameInstallReceiver] does that; pointing this at an
            // activity instead just relaunches the frame and the update stalls
            // invisibly.
            val intent = Intent(FrameInstallReceiver.ACTION).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private companion object {
        const val TAG = "FrameUpdater"
        const val TIMEOUT_MS = 15_000
    }
}
