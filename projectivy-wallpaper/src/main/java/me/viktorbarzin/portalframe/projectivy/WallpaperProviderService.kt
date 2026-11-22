package me.viktorbarzin.portalframe.projectivy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.net.HttpURLConnection
import java.net.URL

/**
 * Projectivy binds this service and calls getWallpapers() off its UI thread
 * when its cache (itemsCacheDurationMillis in the manifest) runs out.
 * An empty list keeps the wallpaper already on screen, so any failure,
 * such as the frame server being unreachable, returns one.
 */
class WallpaperProviderService : Service() {

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            if (event !is Event.TimeElapsed) return emptyList()
            val photos = try {
                FramePhotos.parse(BuildConfig.FRAME_URL, fetch("${BuildConfig.FRAME_URL.trimEnd('/')}/api/Asset"))
            } catch (e: Exception) {
                Log.w(TAG, "could not fetch photos from ${BuildConfig.FRAME_URL}: $e")
                return emptyList()
            }
            Log.i(TAG, "giving Projectivy ${photos.size} photos")
            return photos.map {
                Wallpaper(it.imageUrl, WallpaperType.IMAGE, WallpaperDisplayMode.CROP, title = it.title)
            }
        }

        override fun getPreferences(): String = ""

        override fun setPreferences(params: String) = Unit
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "ImmichWallpaper"
    }
}
