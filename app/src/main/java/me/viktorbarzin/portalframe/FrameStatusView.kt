package me.viktorbarzin.portalframe

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The panel shown instead of photos when a load fails.
 *
 * Black on purpose: it replaces a black screen, and a bright panel on a wall at
 * night is worse than the problem it reports. The content is [FrameFailure]'s —
 * this class only draws it and supplies the two Android-side facts it needs, the
 * device's own IPv4 address and default gateway.
 *
 * Reading the address needs no runtime permission (the interface list is public and
 * the gateway comes from ACCESS_NETWORK_STATE, which is granted at install). The
 * Wi-Fi SSID would be the friendlier label but is deliberately not shown: since
 * Android 8.1 it requires location permission, which is a poor trade for a kiosk.
 */
class FrameStatusView(context: Context) : LinearLayout(context) {

    private val headline = text(22f, Color.WHITE)
    private val detail = text(15f, MUTED)
    private val target = text(13f, MUTED)
    private val network = text(13f, MUTED)
    private val retry = text(12f, DIM)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        val pad = dp(24)
        setPadding(pad, pad, pad, pad)
        listOf(headline, detail, target, network, retry).forEach(::addView)
        (headline.layoutParams as LayoutParams).bottomMargin = dp(12)
        (detail.layoutParams as LayoutParams).bottomMargin = dp(20)
        (target.layoutParams as LayoutParams).bottomMargin = dp(4)
        (network.layoutParams as LayoutParams).bottomMargin = dp(16)
    }

    /** Render [failure]; [attempt] is the reload count so a stuck screen is distinguishable. */
    fun show(failure: FrameFailure, attempt: Int, retrySeconds: Int) {
        headline.text = failure.headline
        detail.text = failure.detail
        target.text = failure.target
        network.text = FrameFailure.networkLine(ipv4(), gateway())
        retry.text = FrameFailure.retryLine(attempt, retrySeconds)
    }

    /** This device's first non-loopback IPv4 address, or null when it has no network. */
    private fun ipv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()

    /**
     * The IPv4 default gateway on the active network, or null when there is none.
     *
     * IPv4 specifically: the question this line answers is "which LAN is this?", and
     * the answer is one home subnet versus another. Taking the first default route
     * instead picks the IPv6 one on a dual-stack network and prints `fe80::2`, which
     * is true and useless (seen on the emulator, 2026-08-15).
     */
    private fun gateway(): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val link = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        link.routes
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()

    private fun text(sizeSp: Float, color: Int) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MUTED = 0xFF9AA1AB.toInt()
        const val DIM = 0xFF5F6570.toInt()
    }
}
