package com.linker.app.domain.repository

import com.linker.app.core.util.Result
import com.linker.app.presentation.screens.note.SpotifyTrack

interface SpotifyRepository {
    suspend fun searchTracks(query: String): Result<List<SpotifyTrack>>
}
