package com.linker.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.screens.accountcenter.AccountCenterScreen
import com.linker.app.presentation.screens.auth.AuthScreen
import com.linker.app.presentation.screens.chat.ChatInfoScreen
import com.linker.app.presentation.screens.chat.ChatListScreen
import com.linker.app.presentation.screens.chat.ChatMessageScreen
import com.linker.app.presentation.screens.followlist.FollowListScreen
import com.linker.app.presentation.screens.home.HomeScreen
import com.linker.app.presentation.screens.profile.ProfileScreen
import com.linker.app.presentation.screens.search.SearchScreen
import com.linker.app.presentation.screens.settings.SettingsScreen
import com.linker.app.presentation.screens.splash.SplashScreen
import com.linker.app.presentation.screens.story.StoryScreen
import com.linker.app.presentation.screens.userprofile.UserProfileScreen

@Composable
fun LinkerNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val onNavigateBottomNav: (BottomNavItem) -> Unit = { item ->
        val route = when (item) {
            BottomNavItem.Explore -> Route.Home
            BottomNavItem.Search  -> Route.Search
            BottomNavItem.Add     -> Route.Create
            BottomNavItem.Chat    -> Route.Chat
            BottomNavItem.Profile -> Route.Profile
        }
        navController.navigate(route) {
            popUpTo(Route.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = Route.Splash, modifier = modifier) {

        // ── Splash ──────────────────────────────────────────────────────────
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

        // ── Auth ────────────────────────────────────────────────────────────
        composable<Route.Auth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                },
                isAddingAccount = false
            )
        }

        // ── Add Account Auth ────────────────────────────────────────────────
        composable<Route.AddAccountAuth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Home) { inclusive = true }
                    }
                },
                onNavigateToAccountCenter = {
                    navController.navigate(Route.AccountCenter) {
                        popUpTo(Route.AccountCenter) { inclusive = true }
                    }
                },
                isAddingAccount = true
            )
        }

        // ── Home ────────────────────────────────────────────────────────────
        composable<Route.Home> {
            HomeScreen(onNavigateBottomNav = onNavigateBottomNav)
        }

        // ── Search ──────────────────────────────────────────────────────────
        composable<Route.Search> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Route.UserProfile(userId))
                }
            )
        }

        // ── Create ──────────────────────────────────────────────────────────
        composable<Route.Create> { }

        // ── Chat List ───────────────────────────────────────────────────────
        composable<Route.Chat> {
            ChatListScreen(
                onNavigateToChatDetail = { navController.navigate(Route.ChatDetail(it)) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav
            )
        }

        // ── Chat Detail ─────────────────────────────────────────────────────
        composable<Route.ChatDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatDetail>()
            ChatMessageScreen(
                chatId = route.chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = { navController.navigate(Route.ChatInfo(route.chatId)) }
            )
        }

        // ── Chat Info ───────────────────────────────────────────────────────
        composable<Route.ChatInfo> {
            ChatInfoScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Story Viewer ────────────────────────────────────────────────────
        composable<Route.StoryViewer> {
            StoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── My Profile ──────────────────────────────────────────────────────
        composable<Route.Profile> {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToStory = { navController.navigate(Route.StoryViewer("my_id")) }
            )
        }

        // ── User Profile (başka kullanıcı) ───────────────────────────────────
        composable<Route.UserProfile> {
            UserProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { navController.navigate(Route.ChatDetail(it)) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, "FOLLOWERS")) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, "FOLLOWING")) }
            )
        }

        // FollowList rotası:
        composable<Route.FollowList> {
            FollowListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { navController.navigate(Route.UserProfile(it)) }
            )
        }

        // Settings'e pending requests ekle:
        composable<Route.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAccountCenter = { navController.navigate(Route.AccountCenter) },
                onNavigateToPendingRequests = {
                    navController.navigate(Route.FollowList("me", "PENDING_REQUESTS"))
                }
            )
        }

        // ── Account Center ──────────────────────────────────────────────────
        composable<Route.AccountCenter> {
            AccountCenterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate(Route.AddAccountAuth) },
                onSwitchComplete = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Home) { inclusive = true }
                    }
                }
            )
        }
    }
}
