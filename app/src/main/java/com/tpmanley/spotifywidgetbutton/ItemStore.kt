package com.tpmanley.spotifywidgetbutton

import android.content.Context

/** The one Spotify item (audiobook, podcast, album, playlist, …) the widget plays. */
data class SpotifyItem(val uri: String, val title: String)

/** Persists the item the widget plays. */
object ItemStore {
    private const val PREFS = "item"
    private const val KEY_URI = "uri"
    private const val KEY_TITLE = "title"
    private const val KEY_LAST_RESULT = "last_result"

    fun load(context: Context): SpotifyItem? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return SpotifyItem(uri, prefs.getString(KEY_TITLE, null).orEmpty())
    }

    /** Step-by-step log of the most recent widget tap, shown on the edit screen for troubleshooting. */
    fun startTrace(context: Context) {
        val stamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_RESULT, "Tap at $stamp").commit()
    }

    fun trace(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val log = prefs.getString(KEY_LAST_RESULT, "").orEmpty()
        prefs.edit().putString(KEY_LAST_RESULT, "$log\n$time $message".trim()).commit()
    }

    fun lastResult(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_RESULT, null)

    fun save(context: Context, item: SpotifyItem) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URI, item.uri)
            .putString(KEY_TITLE, item.title)
            .apply()
    }
}
