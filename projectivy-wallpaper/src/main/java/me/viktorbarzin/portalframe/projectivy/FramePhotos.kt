package me.viktorbarzin.portalframe.projectivy

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One photo for the launcher background: where to fetch it and a caption. */
data class FramePhoto(val imageUrl: String, val title: String?)

/**
 * Reads ImmichFrame's `GET /api/Asset` reply: a JSON array of Immich assets,
 * a fresh random batch on every call. Each image becomes the URL of
 * ImmichFrame's own `/api/Asset/{id}/Image`, which serves a resized JPEG, so
 * Projectivy downloads a screen-sized file rather than the original.
 */
object FramePhotos {
    fun parse(serverUrl: String, json: String): List<FramePhoto> {
        val base = serverUrl.trimEnd('/')
        val assets = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return emptyList()
        }
        val photos = mutableListOf<FramePhoto>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("type") != "image") continue
            val id = asset.optString("id").takeIf { it.isNotBlank() } ?: continue
            photos += FramePhoto("$base/api/Asset/$id/Image", place(asset.optJSONObject("exifInfo")))
        }
        return photos
    }

    private fun place(exif: JSONObject?): String? {
        if (exif == null) return null
        val parts = listOf("city", "country")
            .map { if (exif.isNull(it)) "" else exif.optString(it) }
            .filter { it.isNotBlank() }
        return parts.joinToString(", ").ifBlank { null }
    }
}
