package me.viktorbarzin.portalframe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * Full-screen photo-frame activity for the "open it as an app" mode.
 *
 * Immersive mode hides the system bars for a clean display, so the app provides
 * its own exit affordances:
 *   - double-tap anywhere
 *   - long-press anywhere
 *   - the Back button (where the device exposes one)
 *
 * Touch is handled in [dispatchTouchEvent] so the exit gestures keep working even
 * after [FrameView] rebuilds its WebView (e.g. after a renderer crash). The idle
 * experience is a separate component ([FrameDreamService]).
 */
class FrameActivity : Activity() {

    private lateinit var frame: FrameView
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on while the frame is open. The Portal's own power
        // policy + camera presence do NOT reliably hold it — it went dark on a
        // ~3-min cycle even with screen_off_timeout maxed out — so hold the
        // wakelock at the window. (Re-added in v0.1.5; removed in v0.1.4 was a
        // mistake for this device.)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        frame = FrameView(this)
        setContentView(frame)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                finish()
            }
        })

        Toast.makeText(this, "Double-tap or long-press to exit", Toast.LENGTH_LONG).show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // singleTask warm-relaunch reuses this instance — reload so re-opening
        // always shows a fresh frame rather than a stale/blank page.
        frame.reload()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        frame.onResumeView()
    }

    override fun onPause() {
        frame.onPauseView()
        super.onPause()
    }

    override fun onDestroy() {
        frame.destroyView()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // targetSdk 29
    override fun onBackPressed() {
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    @Suppress("DEPRECATION") // Target SDK 29: legacy immersive flags are the supported path.
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}
