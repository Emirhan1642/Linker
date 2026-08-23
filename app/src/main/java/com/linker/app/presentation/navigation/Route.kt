package com.linker.app.presentation.navigation

import com.linker.app.presentation.screens.followlist.FollowListType
import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable data object Splash : Route
    @Serializable data object Onboarding : Route
    @Serializable data object Auth : Route

    @Serializable data object Main : Route
    @Serializable data object Home : Route
    @Serializable data object Search : Route
    @Serializable data object Create : Route
    @Serializable data object Chat : Route
    @Serializable data object Profile : Route

    @Serializable data object StoryGrid : Route
    @Serializable data class UserProfile(val userId: String) : Route
    @Serializable data class ChatDetail(val chatId: String) : Route
    @Serializable data class ChatInfo(val chatId: String) : Route
    @Serializable data object NewChat : Route
    @Serializable data class StoryViewer(val userId: String) : Route
    @Serializable data object StoryEditor : Route

    /** Link post detail — displays the full post with comments. */
    @Serializable data class LinkDetail(val linkId: String) : Route

    /** Link editor — for creating or editing a new Link post. */
    @Serializable data object LinkEditor : Route

    /** Note editor screen — for creating a new Note (text/music/location/countdown). */
    @Serializable data object NoteEditor : Route

    /** Spotify Search Screen */
    @Serializable data object SpotifySearch : Route

    /** Artist Profile Screen */
    @Serializable data class ArtistProfile(val artistId: String) : Route

    /** Album Detail Screen */
    @Serializable data class AlbumDetail(val albumId: String) : Route

    /**
     * Track Clip Picker Screen — shown after selecting a track, before returning to NoteEditor.
     * Allows the user to listen and select a start/end clip range from the track.
     */
    @Serializable data class TrackClipPicker(
        val trackId: String,
        val trackName: String,
        val artistName: String,
        val albumArtUrl: String = "",
        val previewUrl: String = "",
        val durationMs: Long = 0L,
        val isExplicit: Boolean = false
    ) : Route

    /** Location Picker Screen (legacy search — kept for potential future use) */
    @Serializable data object LocationPicker : Route

    /**
     * Full-screen map that displays the note author's shared GPS location.
     * Navigated to from NoteEditorScreen (own preview) or from a NoteDetail card.
     *
     * @param latitude  GPS latitude.
     * @param longitude GPS longitude.
     * @param placeName Human-readable city / district string.
     */
    @Serializable data class NoteLocationMap(
        val latitude: Double,
        val longitude: Double,
        val placeName: String
    ) : Route

    @Serializable data object Settings : Route
    @Serializable data object OfflineMessagingSettings : Route
    @Serializable data object AccountCenter : Route
    @Serializable data object AddAccountAuth : Route

    /**
     * Takipçi / Takip edilen / İstekler listesi.
     * [userId]   : kimin listesi gösteriliyor
     * [listType] : FollowListType enum (FOLLOWERS | FOLLOWING | PENDING_REQUESTS | SENT_REQUESTS)
     */
    @Serializable data class FollowList(val userId: String, val listType: FollowListType) : Route
}
