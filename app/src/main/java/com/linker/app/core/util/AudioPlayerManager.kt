package com.linker.app.core.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerManager @Inject constructor() {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var currentUrl: String? = null
    private var targetEndTimeMs: Long? = null

    fun playPreview(url: String, startMs: Long? = null, endMs: Long? = null) {
        targetEndTimeMs = endMs
        if (currentUrl == url && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }

        if (currentUrl == url && mediaPlayer != null) {
            // Same track loaded. Just seek and resume to avoid recreating the MediaPlayer.
            if (startMs != null && startMs >= 0L) {
                mediaPlayer?.seekTo(startMs.toInt())
                _currentPositionMs.value = startMs
            }
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracking()
            return
        }

        // New URL or seeking to a specific start position
        stop()
        currentUrl = url
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration.toLong()
                    if (startMs != null && startMs > 0) mp.seekTo(startMs.toInt())
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracking()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    stopProgressTracking()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepareAsync() // Network stream
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun seekTo(positionMs: Long, endTimeMs: Long? = null) {
        if (endTimeMs != null) {
            targetEndTimeMs = endTimeMs
        }
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        currentUrl = null
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        _currentPositionMs.value = pos
                        
                        targetEndTimeMs?.let { endMs ->
                            if (pos >= endMs) {
                                pause()
                                targetEndTimeMs = null
                                _currentPositionMs.value = endMs
                            }
                        }
                    }
                }
                delay(100) // Update every 100ms for smooth lyric scrolling
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
