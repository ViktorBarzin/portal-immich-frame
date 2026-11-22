package me.viktorbarzin.portalframe.projectivy

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Projectivy's plugin settings button opens this. There is nothing to set. */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 24f
                text = "Random photos from ${BuildConfig.FRAME_URL}\nNew batch every hour."
            },
        )
    }
}
