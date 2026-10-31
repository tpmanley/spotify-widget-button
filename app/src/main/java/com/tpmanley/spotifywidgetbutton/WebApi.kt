package com.tpmanley.spotifywidgetbutton

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Short-lived Web API token from the Spotify auth library (about 1 hour, no refresh). */
object SpotifyToken {
    private const val PREFS = "token"

    fun valid(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return null
        // Leave a minute of slack so it doesn't expire mid-request.
        return token.takeIf { System.currentTimeMillis() < prefs.getLong("expires_at", 0) - 60_000 }
    }

    fun save(context: Context, token: String, expiresInSeconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putLong("expires_at", System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

sealed interface PlaybackCheck {
    data object NothingPlaying : PlaybackCheck
    data class Playing(val deviceName: String, val deviceType: String, val what: String?) : PlaybackCheck
    data object Unauthorized : PlaybackCheck
    data class Failed(val reason: String) : PlaybackCheck
}

object WebApi {
    /** Blocking; call off the main thread. */
    fun currentPlayback(token: String): PlaybackCheck = try {
        val conn = URL("https://api.spotify.com/v1/me/player").openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            when (val code = conn.responseCode) {
                204 -> PlaybackCheck.NothingPlaying
                401 -> PlaybackCheck.Unauthorized
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                else -> PlaybackCheck.Failed("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(Config.TAG, "Playback check failed", e)
        PlaybackCheck.Failed("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }

    internal fun parse(body: String): PlaybackCheck {
        if (body.isBlank()) return PlaybackCheck.NothingPlaying
        val json = JSONObject(body)
        if (!json.optBoolean("is_playing")) return PlaybackCheck.NothingPlaying
        val device = json.optJSONObject("device")
        val item = json.optJSONObject("item")
        val what = item?.optString("name")?.ifBlank { null }
            ?: item?.optJSONObject("show")?.optString("name")?.ifBlank { null }
        return PlaybackCheck.Playing(
            deviceName = device?.optString("name").orEmpty().ifBlank { "another device" },
            deviceType = device?.optString("type").orEmpty(),
            what = what,
        )
    }

    private fun phoneNames(context: Context) = listOfNotNull(
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME),
        Build.MODEL,
    )

    fun describeThisPhone(context: Context): String =
        "audio playing here=${PlaybackStatus.audioPlayingHere(context)}, names=${phoneNames(context)}"

    /**
     * Best guess at whether the active Spotify Connect device is this phone: either sound is
     * coming out of this phone right now, or the device name matches the phone's.
     */
    fun isThisPhone(context: Context, playing: PlaybackCheck.Playing): Boolean =
        PlaybackStatus.audioPlayingHere(context) ||
            phoneNames(context).any { it.equals(playing.deviceName, ignoreCase = true) }
}
