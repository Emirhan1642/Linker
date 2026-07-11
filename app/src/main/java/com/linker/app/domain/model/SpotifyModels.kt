package com.linker.app.domain.model

import com.linker.app.presentation.screens.note.SpotifyTrack

data class SpotifyArtistDomain(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val followerCount: Int
)

data class SpotifyAlbumDomain(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val releaseYear: String?
)

data class SpotifyArtistProfile(
    val artist: SpotifyArtistDomain,
    val topTracks: List<SpotifyTrack>,
    val popularReleases: List<SpotifyAlbumDomain>,
    val albums: List<SpotifyAlbumDomain>,
    val singles: List<SpotifyAlbumDomain>,
    val compilations: List<SpotifyAlbumDomain>,
    val thisIsPlaylistId: String? // Store just the playlist ID so we can navigate to it or play it, or we could fetch tracks.
)

data class SpotifyAlbumProfile(
    val album: SpotifyAlbumDomain,
    val artists: List<SpotifyArtistDomain>,
    val tracks: List<SpotifyTrack>
)

enum class SpotifySearchType {
    ALL, TRACKS, ARTISTS, ALBUMS
}

sealed class SpotifySearchResultItem {
    data class Track(val track: SpotifyTrack) : SpotifySearchResultItem()
    data class Artist(val artist: SpotifyArtistDomain) : SpotifySearchResultItem()
    data class Album(val album: SpotifyAlbumDomain) : SpotifySearchResultItem()
}
