package com.tpmanley.audiobookbutton

import android.content.ComponentName
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * Bound by Android once notification access is granted. We only use it to watch Spotify's
 * media session, so the widget can show/hide its Stop button the moment playback changes.
 * Notifications themselves are ignored.
 */
class MediaWatcherService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var spotify: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers?.firstOrNull { it.packageName == PlaybackStatus.SPOTIFY_PACKAGE })
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = publish()
        override fun onSessionDestroyed() = watch(null)
    }

    // Audio starting/stopping on the phone lags Spotify's session state, so recheck on
    // audio changes and shortly after session changes.
    private val audioCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = publish()
    }
    private val recheck = Runnable { publishNow() }

    override fun onListenerConnected() {
        getSystemService(AudioManager::class.java).registerAudioPlaybackCallback(audioCallback, handler)
        val msm = getSystemService(MediaSessionManager::class.java)
        sessionManager = msm
        val me = ComponentName(this, MediaWatcherService::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
        watch(msm.getActiveSessions(me).firstOrNull { it.packageName == PlaybackStatus.SPOTIFY_PACKAGE })
    }

    override fun onListenerDisconnected() {
        getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(audioCallback)
        handler.removeCallbacks(recheck)
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        watch(null)
    }

    private fun watch(controller: MediaController?) {
        if (controller?.sessionToken != spotify?.sessionToken) {
            spotify?.unregisterCallback(callback)
            spotify = controller
            controller?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        publishNow()
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, 1_500)
    }

    private fun publishNow() {
        if (PlaybackStatus.setPlayingLocally(this, PlaybackStatus.isLocalPlaying(this, spotify))) {
            AudiobookWidgetProvider.refreshAll(this)
        }
    }
}
