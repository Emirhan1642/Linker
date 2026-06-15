package com.linker.app.presentation.navigation

import com.linker.app.presentation.screens.followlist.FollowListType
import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable data object Splash : Route
    @Serializable data object Auth : Route

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

    /** Link post detail — displays the full post with comments. */
    @Serializable data class LinkDetail(val linkId: String) : Route

    /** Link editor — for creating or editing a new Link post. */
    @Serializable data object LinkEditor : Route

    /** Note editor screen — for creating a new Note (text/music/location/countdown). */
    @Serializable data object NoteEditor : Route

    /** Spotify Search Screen */
    @Serializable data object SpotifySearch : Route

    /** Location Picker Screen */
    @Serializable data object LocationPicker : Route

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
