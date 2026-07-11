package com.linker.app.domain.repository

import com.linker.app.core.util.Result
import com.linker.app.domain.model.*
import com.linker.app.presentation.screens.note.SpotifyTrack

interface SpotifyRepository {
    suspend fun searchTracks(query: String, limit: Int? = null, offset: Int? = null): Result<List<SpotifyTrack>>
    suspend fun search(query: String, type: SpotifySearchType = SpotifySearchType.ALL, limit: Int? = null, offset: Int? = null): Result<List<SpotifySearchResultItem>>
    suspend fun getArtistProfile(artistId: String): Result<SpotifyArtistProfile>
    suspend fun getAlbumProfile(albumId: String): Result<SpotifyAlbumProfile>
    suspend fun getPlaylistTracks(playlistId: String, limit: Int? = null, offset: Int? = null): Result<List<SpotifyTrack>>
    suspend fun scrapePlaylistTracks(playlistId: String, limit: Int? = null, offset: Int? = null): Result<List<SpotifyTrack>>
    suspend fun scrapeTrackPreviewUrl(trackId: String): String?
    suspend fun getRecommendations(): Result<List<SpotifyTrack>>
    suspend fun getRecommendationsByGenre(genre: String, limit: Int = 15): Result<List<SpotifyTrack>>
    fun getLocalRecentTracks(): List<SpotifyTrack>
    fun saveLocalRecentTrack(track: SpotifyTrack)
}
