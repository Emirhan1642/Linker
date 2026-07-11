package com.linker.app.domain.repository

import com.linker.app.core.util.Result

data class SyncedLyricLine(
    val timeMs: Long,
    val text: String
)

interface LyricsRepository {
    suspend fun getSyncedLyrics(trackName: String, artistName: String): Result<List<SyncedLyricLine>>
    fun getCachedLyrics(trackName: String, artistName: String): List<SyncedLyricLine>?
}
