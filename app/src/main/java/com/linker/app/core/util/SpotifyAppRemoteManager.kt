package com.linker.app.core.util

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyAppRemoteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var targetEndTimeMs: Long? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun connect(context: Context = this.context, clientId: String, onConnected: () -> Unit, onError: (Throwable) -> Unit) {
        if (spotifyAppRemote?.isConnected == true) {
            onConnected()
            return
        }

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyAuthManager.REDIRECT_URI)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(this.context.applicationContext, connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    _isConnected.value = true
                    observePlayerState()
                    onConnected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SpotifyAppRemoteManager", "Connection failed", throwable)
                    _isConnected.value = false
                    onError(throwable)
                }
            })
    }

    private var lastSeekTime = 0L

    private var lastProgressUpdateMs = 0L

    private fun observePlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            _isPlaying.value = !playerState.isPaused
            _durationMs.value = playerState.track?.duration ?: 0L

            // Ignore delayed player state updates right after a seek to prevent race conditions 
            if (System.currentTimeMillis() - lastSeekTime > 1000) {
                _currentPositionMs.value = playerState.playbackPosition
                lastProgressUpdateMs = System.currentTimeMillis()
            }

            if (!playerState.isPaused) {
                startProgressTracking()
            } else {
                stopProgressTracking()
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        lastProgressUpdateMs = System.currentTimeMillis()
        progressJob = scope.launch {
            while (isActive) {
                delay(50)
                val now = System.currentTimeMillis()
                val delta = now - lastProgressUpdateMs
                lastProgressUpdateMs = now
                val newPos = _currentPositionMs.value + delta
                _currentPositionMs.value = newPos

                targetEndTimeMs?.let { endTime ->
                    if (newPos >= endTime) {
                        pause()
                        targetEndTimeMs = null
                        _currentPositionMs.value = endTime
                    }
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun getCurrentTrack(
        onResult: (trackId: String, trackName: String, artistName: String, albumArtUrl: String?, durationMs: Long) -> Unit,
        onError: () -> Unit
    ) {
        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            onError()
            return
        }
        remote.playerApi?.playerState?.setResultCallback { state ->
            val track = state.track
            if (track != null) {
                val trackId = track.uri.removePrefix("spotify:track:")
                val trackName = track.name ?: ""
                val artistName = track.artist?.name ?: ""
                val albumArtUrl = track.imageUri?.raw
                onResult(trackId, trackName, artistName, albumArtUrl, track.duration)
            } else {
                onError()
            }
        }?.setErrorCallback { onError() } ?: onError()
    }

    fun playTrack(trackId: String, startTimeMs: Long? = 0L, endTimeMs: Long? = null) {
        targetEndTimeMs = endTimeMs
        val trackUri = "spotify:track:$trackId"
        
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
            if (playerState.track?.uri == trackUri) {
                if (startTimeMs != null && startTimeMs >= 0L) {
                    spotifyAppRemote?.playerApi?.seekTo(startTimeMs)
                    _currentPositionMs.value = startTimeMs
                }
                resume()
            } else {
                spotifyAppRemote?.playerApi?.play(trackUri)
                    ?.setResultCallback {
                        if (startTimeMs != null && startTimeMs >= 0L) {
                            spotifyAppRemote?.playerApi?.seekTo(startTimeMs)
                            _currentPositionMs.value = startTimeMs
                        }
                    }
                    ?.setErrorCallback {
                        _isPlaying.value = false
                        stopProgressTracking()
                    }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        stopProgressTracking()
        spotifyAppRemote?.playerApi?.pause()
    }

    fun resume() {
        _isPlaying.value = true
        startProgressTracking()
        spotifyAppRemote?.playerApi?.resume()
    }

    fun seekTo(positionMs: Long, endTimeMs: Long? = null) {
        lastSeekTime = System.currentTimeMillis()
        if (endTimeMs != null) targetEndTimeMs = endTimeMs
        spotifyAppRemote?.playerApi?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    /**
     * Pauses playback first, then disconnects the App Remote.
     * Use this instead of disconnect() to avoid music continuing in background.
     */
    fun pauseAndDisconnect() {
        stopProgressTracking()
        spotifyAppRemote?.playerApi?.pause()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        _isConnected.value = false
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        targetEndTimeMs = null
        Log.d("SpotifyAppRemoteManager", "Paused and disconnected.")
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        _isConnected.value = false
    }
}
