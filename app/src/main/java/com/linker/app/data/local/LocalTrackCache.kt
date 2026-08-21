package com.linker.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.linker.app.presentation.screens.note.SpotifyTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTrackCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("recent_spotify_tracks", Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val KEY_RECENT_TRACKS = "recent_tracks"
    private val MAX_HISTORY = 30
    private val lock = Any()
    @Volatile
    private var memoryCache: List<SpotifyTrack>? = null

    fun getRecentTracks(): List<SpotifyTrack> {
        memoryCache?.let { return it }
        synchronized(lock) {
            memoryCache?.let { return it }
            val json = prefs.getString(KEY_RECENT_TRACKS, null)
            val list = if (json != null) {
                try {
                    val type = object : TypeToken<List<SpotifyTrack>>() {}.type
                    gson.fromJson<List<SpotifyTrack>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            memoryCache = list
            return list
        }
    }

    fun saveTrack(track: SpotifyTrack) {
        synchronized(lock) {
            val currentTracks = getRecentTracks().toMutableList()
            // Remove if it already exists to move it to the top
            currentTracks.removeAll { it.id == track.id }
            // Add to the top
            currentTracks.add(0, track)
            // Trim to max history
            if (currentTracks.size > MAX_HISTORY) {
                currentTracks.subList(MAX_HISTORY, currentTracks.size).clear()
            }
            
            memoryCache = currentTracks.toList()
            prefs.edit().putString(KEY_RECENT_TRACKS, gson.toJson(currentTracks)).apply()
        }
    }
}
