package com.tpmanley.audiobookbutton

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Widget Stop button: pauses Spotify on this phone via its media session. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = PlaybackStatus.spotifyController(context)
        if (controller == null) {
            Log.w(Config.TAG, "Stop tapped but no Spotify session (or no notification access)")
        } else {
            controller.transportControls.pause()
        }
        // The media watcher will confirm, but hide the button right away.
        if (PlaybackStatus.setPlayingLocally(context, false)) AudiobookWidgetProvider.refreshAll(context)
    }
}
