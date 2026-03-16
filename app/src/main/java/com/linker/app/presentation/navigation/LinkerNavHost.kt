package com.linker.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.screens.auth.AuthScreen
import com.linker.app.presentation.screens.chat.ChatListScreen
import com.linker.app.presentation.screens.home.HomeScreen
import com.linker.app.presentation.screens.profile.ProfileScreen
import com.linker.app.presentation.screens.search.SearchScreen
import com.linker.app.presentation.screens.splash.SplashScreen
import com.linker.app.presentation.screens.chat.ChatInfoScreen
import com.linker.app.presentation.screens.chat.ChatMessageScreen
import com.linker.app.presentation.screens.story.StoryScreen

/**
 * Main Navigation Host
 * 
 * Manages navigation between all screens in the app.
 */
@Composable
fun LinkerNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
    // Helper to map bottom nav clicks to routes
    val onNavigateBottomNav: (BottomNavItem) -> Unit = { item ->
        val route = when (item) {
            BottomNavItem.Explore -> Route.Home
            BottomNavItem.Search -> Route.Search
            BottomNavItem.Add -> Route.Create
            BottomNavItem.Chat -> Route.Chat
            BottomNavItem.Profile -> Route.Profile
        }
        
        // Single top navigation to avoid building up backstack of top-level destinations
        navController.navigate(route) {
            popUpTo(Route.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier
    ) {
        // Splash Screen
        composable<Route.Splash> {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Route.Auth) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                }
            )
        }
        
        // Auth Screen
        composable<Route.Auth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                }
            )
        }
        
        // Home Screen (Main Feed / Explore)
        composable<Route.Home> {
            HomeScreen(onNavigateBottomNav = onNavigateBottomNav)
        }
        
        // Search Screen
        composable<Route.Search> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav
            )
        }
        
        // Create Screen (Placeholder)
        composable<Route.Create> {
            // TODO: Add Create Screen
        }
        
        // Chat List Screen
        composable<Route.Chat> {
            ChatListScreen(
                onNavigateToChatDetail = { chatId ->
                    navController.navigate(Route.ChatDetail(chatId))
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav
            )
        }

        // Chat Message (Detail) Screen
        composable<Route.ChatDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatDetail>()
            ChatMessageScreen(
                chatId = route.chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = {
                    navController.navigate(Route.ChatInfo(route.chatId))
                }
            )
        }

        // Chat Info Screen
        composable<Route.ChatInfo> {
            ChatInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Story Viewer Screen
        composable<Route.StoryViewer> {
            StoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Profile Screen
        composable<Route.Profile> {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToStory = { navController.navigate(Route.StoryViewer("my_id")) }
            )
        }
        
        // TODO: Detail screens and nested navigation
    }
}
