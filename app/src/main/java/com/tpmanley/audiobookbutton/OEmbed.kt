package com.tpmanley.audiobookbutton

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Looks up a title via Spotify's public oEmbed endpoint (no auth required). */
object OEmbed {
    suspend fun fetchTitle(spotifyUri: String): String? = withContext(Dispatchers.IO) {
        val webUrl = SpotifyLink.toWebUrl(spotifyUri) ?: return@withContext null
        val endpoint = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(webUrl, "UTF-8")
        try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            try {
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("title").ifBlank { null }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(Config.TAG, "oEmbed lookup failed", e)
            null
        }
    }
}
