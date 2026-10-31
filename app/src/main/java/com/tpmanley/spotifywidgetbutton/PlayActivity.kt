package com.tpmanley.spotifywidgetbutton

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.protocol.types.PlayerContext

/**
 * Invisible trampoline launched by the widget: connects to the Spotify app,
 * starts (or resumes) the saved Spotify item, then closes.
 */
class PlayActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var remote: SpotifyAppRemote? = null
    private var retried = false
    private var authTried = false
    private var authForCheck = false
    private var done = false
    private var item: SpotifyItem? = null
    private val timeout = Runnable { item?.let { if (!done) fail(it, getString(R.string.error_timeout)) } }
    private var connectAttempt = 0

    private fun step(message: String) {
        Log.i(Config.TAG, message)
        ItemStore.trace(this, message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ItemStore.startTrace(this)
        val item = ItemStore.load(this)
        this.item = item
        step("SpotifyItem: ${item?.uri}")
        if (item == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (!SpotifyAppRemote.isSpotifyInstalled(this)) {
            fail(item, getString(R.string.error_spotify_missing))
            return
        }
        checkOtherDevices(item)
    }

    // --- "Is someone already playing on another device?" (Web API) ---

    private fun checkOtherDevices(item: SpotifyItem) {
        val token = SpotifyToken.valid(this)
        if (token == null) {
            if (!authTried) requestAuthorization(forCheck = true) else startConnecting(item)
            return
        }
        step("Checking what Spotify is playing")
        Thread {
            val result = WebApi.currentPlayback(token)
            runOnUiThread { if (!done && !isFinishing) onPlaybackChecked(item, result) }
        }.start()
    }

    private fun onPlaybackChecked(item: SpotifyItem, result: PlaybackCheck) {
        step("Playback check: $result")
        when (result) {
            is PlaybackCheck.Playing ->
                if (WebApi.isThisPhone(this, result).also { step("Is this phone? $it (${WebApi.describeThisPhone(this)})") }) startConnecting(item)
                else askToTakeOver(item, result)
            PlaybackCheck.Unauthorized -> {
                SpotifyToken.clear(this)
                if (!authTried) requestAuthorization(forCheck = true) else startConnecting(item)
            }
            // Nothing playing, or the check failed: don't let the check block playback.
            else -> startConnecting(item)
        }
    }

    private fun askToTakeOver(item: SpotifyItem, playing: PlaybackCheck.Playing) {
        val what = playing.what?.let { "“$it”" } ?: getString(R.string.takeover_something)
        val title = item.title.ifBlank { getString(R.string.takeover_untitled) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.takeover_title, playing.deviceName))
            .setMessage(getString(R.string.takeover_message, what, playing.deviceName, title))
            .setPositiveButton(R.string.takeover_yes) { _, _ -> step("User chose to take over"); startConnecting(item) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled() }
            .setOnCancelListener { cancelled() }
            .show()
    }

    private fun cancelled() {
        if (done) return
        step("Cancelled; left the other device playing")
        done = true
        finish()
    }

    // --- Playing on this phone (App Remote) ---

    private fun startConnecting(item: SpotifyItem) {
        // Safety net so this activity never lingers.
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MS)
        connect(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        step("Widget tapped again while still working; ignoring")
    }

    private fun connect(item: SpotifyItem) {
        val attempt = ++connectAttempt
        step("Connecting to Spotify (attempt $attempt)")
        // No built-in auth view: with it, connect() can hang forever instead of reporting
        // that approval is needed. Approval is handled by requestAuthorization() instead.
        val params = ConnectionParams.Builder(Config.CLIENT_ID)
            .setRedirectUri(Config.REDIRECT_URI)
            .showAuthView(false)
            .build()

        var answered = false
        handler.postDelayed({
            if (!answered && !done && attempt == connectAttempt) {
                answered = true
                onConnectFailed(item, null)
            }
        }, CONNECT_TIMEOUT_MS)

        SpotifyAppRemote.connect(this, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                if (answered || done) {
                    // Arrived after we gave up on this attempt.
                    SpotifyAppRemote.disconnect(appRemote)
                    return
                }
                answered = true
                step("Connected")
                remote = appRemote
                switchToThisPhone(appRemote) { playOrResume(appRemote, item) }
            }

            override fun onFailure(error: Throwable) {
                Log.w(Config.TAG, "Spotify connection failed", error)
                if (answered || done) return
                answered = true
                onConnectFailed(item, error)
            }
        })
    }

    /** [error] is null when Spotify never answered. */
    private fun onConnectFailed(item: SpotifyItem, error: Throwable?) {
        step(if (error == null) "No answer from Spotify after ${CONNECT_TIMEOUT_MS / 1000}s" else "Connect failed: ${describe(error)}")
        when {
            error is UserNotAuthorizedException || error is AuthenticationFailedException ->
                if (!authTried) requestAuthorization(forCheck = false)
                else fail(item, getString(R.string.error_connect, describe(error)))
            error is NotLoggedInException -> fail(item, getString(R.string.error_not_logged_in))
            !retried -> {
                // Spotify may be fully closed; wake it up and try once more.
                retried = true
                step("Opening Spotify, will retry in 2.5s")
                packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
                handler.postDelayed({ if (!done) connect(item) }, 2_500)
            }
            // Still silent after waking Spotify: maybe it's waiting on approval.
            !authTried -> requestAuthorization(forCheck = false)
            else -> fail(item, getString(R.string.error_connect, error?.let(::describe) ?: getString(R.string.reason_no_response)))
        }
    }

    /**
     * App Remote's built-in approval prompt doesn't appear with current Spotify versions,
     * so get the one-time "app-remote-control" approval via the auth library instead.
     */
    private fun requestAuthorization(forCheck: Boolean) {
        authTried = true
        authForCheck = forCheck
        handler.removeCallbacks(timeout) // The user may take a while on the approval screen.
        step("Asking Spotify for approval")
        val request = AuthorizationRequest.Builder(
            Config.CLIENT_ID, AuthorizationResponse.Type.TOKEN, Config.REDIRECT_URI,
        ).setScopes(SCOPES).build()
        AuthorizationClient.openLoginActivity(this, AUTH_REQUEST, request)
    }

    @Deprecated("Needed for AuthorizationClient.openLoginActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST) return
        val item = item ?: return
        val response = AuthorizationClient.getResponse(resultCode, data)
        step("Approval result: ${response.type} ${response.error.orEmpty()}")
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                SpotifyToken.save(this, response.accessToken, response.expiresIn)
                if (authForCheck) checkOtherDevices(item) else startConnecting(item)
            }
            // A failed check shouldn't stop playback; App Remote will ask again if it must.
            AuthorizationResponse.Type.ERROR ->
                if (authForCheck) startConnecting(item)
                else fail(item, getString(R.string.error_auth, response.error.orEmpty()))
            else ->
                if (authForCheck) startConnecting(item)
                else fail(item, getString(R.string.error_auth, getString(R.string.reason_cancelled)))
        }
    }

    /**
     * App Remote commands go to Spotify's *active* device, which may be a speaker or computer.
     * Pull playback onto this phone first so the item plays here.
     */
    private fun switchToThisPhone(appRemote: SpotifyAppRemote, then: () -> Unit) {
        var continued = false
        fun next() { if (!continued && !done) { continued = true; then() } }
        step("Switching playback to this phone")
        appRemote.connectApi.connectSwitchToLocalDevice()
            .setResultCallback { step("Switched to this phone"); next() }
            .setErrorCallback { step("Switch to this phone failed: ${describe(it)}"); next() }
        handler.postDelayed({ if (!continued) step("No answer to switch after 3s, continuing"); next() }, 3_000)
    }

    private fun playOrResume(appRemote: SpotifyAppRemote, item: SpotifyItem) {
        val player = appRemote.playerApi
        var decided = false

        fun playUri(uri: String, fallback: String?) {
            step("play($uri)")
            var answered = false
            player.play(uri)
                .setResultCallback { answered = true; succeed("Played $uri") }
                .setErrorCallback { error ->
                    answered = true
                    step("play($uri) failed: ${describe(error)}")
                    if (done) return@setErrorCallback
                    if (fallback != null) {
                        playUri(fallback, null)
                    } else {
                        fail(item, getString(R.string.error_play, describe(error)))
                    }
                }
            // Spotify sometimes starts playback without ever answering; check what it's doing.
            handler.postDelayed({
                if (answered || done) return@postDelayed
                step("No answer to play($uri) after 5s, checking player state")
                player.playerState.setResultCallback { state ->
                    step("Player: paused=${state.isPaused} track=${state.track?.uri} (${state.track?.name})")
                    if (!state.isPaused) succeed("Playing (Spotify never confirmed)")
                    else if (fallback != null) playUri(fallback, null)
                    else fail(item, getString(R.string.error_play, getString(R.string.reason_no_response)))
                }.setErrorCallback { step("Player state failed: ${describe(it)}") }
            }, 5_000)
        }

        fun play() {
            if (decided || done) return
            decided = true
            playUri(item.uri, SpotifyLink.alternateUri(item.uri))
        }

        fun resumeOrKeepPlaying(isPaused: Boolean, what: String) {
            if (decided || done) return
            decided = true
            if (isPaused) {
                player.resume().setResultCallback { succeed("Resumed $what") }
                    .setErrorCallback { fail(item, getString(R.string.error_play, describe(it))) }
            } else {
                succeed("Already playing $what")
            }
        }

        // If the saved item is already loaded, resume rather than restart it.
        if (SpotifyLink.isSingleItem(item.uri)) {
            // Tracks, episodes and chapters: compare with the current track.
            player.playerState.setResultCallback { state ->
                if (SpotifyLink.sameItem(state.track?.uri, item.uri)) {
                    step("Current track is the saved item")
                    resumeOrKeepPlaying(state.isPaused, item.uri)
                } else {
                    play()
                }
            }.setErrorCallback { step("Player state failed: ${describe(it)}"); play() }
            handler.postDelayed({ if (!decided && !done) { step("No player state after 1.5s"); play() } }, 1_500)
        } else {
            // Albums, playlists, shows, audiobooks, artists: compare with the current context.
            // Ask for the context and the paused flag at the same time, then decide once we have
            // what we need.
            var paused: Boolean? = null
            var context: PlayerContext? = null
            fun decide() {
                val ctx = context ?: return
                if (!SpotifyLink.sameItem(ctx.uri, item.uri)) play()
                else paused?.let { step("Current context is the saved item"); resumeOrKeepPlaying(it, ctx.uri) }
            }
            player.playerState.setResultCallback { paused = it.isPaused; decide() }
            val sub = player.subscribeToPlayerContext()
            sub.setEventCallback { ctx: PlayerContext ->
                if (context != null) return@setEventCallback
                sub.cancel()
                step("Current context: ${ctx.uri}")
                context = ctx
                decide()
            }.setErrorCallback { step("Context subscription failed: ${describe(it)}"); play() }
            handler.postDelayed({
                sub.cancel()
                val ctx = context
                when {
                    decided || done -> {}
                    // Context matches but the paused flag never came: resume() is harmless if playing.
                    ctx != null && SpotifyLink.sameItem(ctx.uri, item.uri) -> resumeOrKeepPlaying(true, ctx.uri)
                    else -> { step("No context after 1.5s"); play() }
                }
            }, 1_500)
        }
    }

    private fun describe(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message.orEmpty()}".trimEnd(':', ' ')

    private fun succeed(message: String) {
        if (done) return
        done = true
        step("OK - $message")
        finish()
    }

    private fun fail(item: SpotifyItem, message: String) {
        if (done) return
        done = true
        step("FAILED - $message")
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        // Fall back to opening the item page so it's one more tap to play.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            SpotifyLink.toWebUrl(item.uri)?.let {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        super.onDestroy()
    }

    private companion object {
        const val SPOTIFY_PACKAGE = PlaybackStatus.SPOTIFY_PACKAGE
        val SCOPES = arrayOf("app-remote-control", "user-read-playback-state")
        const val AUTH_REQUEST = 1337
        const val TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 6_000L
    }
}
