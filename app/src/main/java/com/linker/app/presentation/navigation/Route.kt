package com.linker.app.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation Routes using Type-Safe Navigation
 */
sealed interface Route {
    
    // Auth Flow
    @Serializable
    data object Splash : Route
    
    @Serializable
    data object Auth : Route
    
    @Serializable
    data object ProfileSetup : Route
    
    // Main App Flow
    @Serializable
    data object Home : Route
    
    @Serializable
    data object Search : Route
    
    @Serializable
    data object Create : Route
    
    @Serializable
    data object Chat : Route
    
    @Serializable
    data object Profile : Route
    
    // Detail Screens
    @Serializable
    data class LinkDetail(val linkId: String) : Route
    
    @Serializable
    data class UserProfile(val userId: String) : Route
    
    @Serializable
    data class ChatDetail(val chatId: String) : Route
    
    @Serializable
    data class ChatInfo(val chatId: String) : Route
    
    @Serializable
    data class StoryViewer(val userId: String) : Route
    
    @Serializable
    data object Settings : Route
}
