package com.tpmanley.audiobookbutton

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

/** Whether Spotify is playing on this phone, as seen through Android's media sessions. */
object PlaybackStatus {
    const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val PREFS = "playback"

    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun isPlayingLocally(context: Context): Boolean =
        hasAccess(context) &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("local", false)

    /** Stores the new state; returns true if it changed. */
    fun setPlayingLocally(context: Context, playing: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("local", false) == playing) return false
        prefs.edit().putBoolean("local", playing).apply()
        return true
    }

    fun spotifyController(context: Context): MediaController? = try {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        msm.getActiveSessions(ComponentName(context, MediaWatcherService::class.java))
            .firstOrNull { it.packageName == SPOTIFY_PACKAGE }
    } catch (e: SecurityException) {
        null // Notification access not granted.
    }

    /**
     * Playing *on this phone*. Spotify's session says "playing" even when the sound is going to
     * a Connect device (speaker, computer), so also require audio actually coming out of the phone.
     */
    fun isLocalPlaying(context: Context, controller: MediaController?): Boolean {
        controller ?: return false
        val state = controller.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        return playing && audioPlayingHere(context)
    }

    fun audioPlayingHere(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).isMusicActive
}
