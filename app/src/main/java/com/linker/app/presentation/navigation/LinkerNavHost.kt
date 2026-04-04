package com.linker.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
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
import com.linker.app.presentation.screens.chat.NewChatScreen
import com.linker.app.presentation.screens.followlist.FollowListScreen
import com.linker.app.presentation.screens.home.HomeScreen
import com.linker.app.presentation.screens.profile.ProfileScreen
import com.linker.app.presentation.screens.search.SearchScreen
import com.linker.app.presentation.screens.settings.SettingsScreen
import com.linker.app.presentation.screens.splash.SplashScreen
import com.linker.app.presentation.screens.story.StoryScreen
import com.linker.app.presentation.screens.userprofile.UserProfileScreen

@Composable
fun LinkerNavHost(
    modifier: Modifier = Modifier,
    initialChatId: String? = null,
    onChatDeepLinkHandled: () -> Unit = {}
) {
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

        composable<Route.Splash> {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Route.Auth) { popUpTo(Route.Splash) { inclusive = true } }
                },
                onNavigateToHome = {
                    navController.navigate(Route.Home) { popUpTo(Route.Splash) { inclusive = true } }
                }
            )
        }

        composable<Route.Auth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home) { popUpTo(Route.Auth) { inclusive = true } }
                },
                isAddingAccount = false
            )
        }

        composable<Route.AddAccountAuth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home) { popUpTo(Route.Home) { inclusive = true } }
                },
                onNavigateToAccountCenter = {
                    navController.navigate(Route.AccountCenter) { popUpTo(Route.AccountCenter) { inclusive = true } }
                },
                isAddingAccount = true
            )
        }

        composable<Route.Home> {
            HomeScreen(onNavigateBottomNav = onNavigateBottomNav)
        }

        composable<Route.Search> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToUserProfile = { userId ->
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId == myUid) {
                        navController.navigate(Route.Profile) {
                            popUpTo(Route.Home) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    } else {
                        navController.navigate(Route.UserProfile(userId))
                    }
                }
            )
        }

        composable<Route.Create> { }

        composable<Route.Chat> {
            ChatListScreen(
                onNavigateToChatDetail = { navController.navigate(Route.ChatDetail(it)) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToNewChat = { navController.navigate(Route.NewChat) }
            )
        }

        composable<Route.NewChat> {
            NewChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { chatId ->
                    navController.navigate(Route.ChatDetail(chatId)) {
                        popUpTo(Route.Chat) { saveState = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Route.ChatDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatDetail>()
            ChatMessageScreen(
                chatId = route.chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = { navController.navigate(Route.ChatInfo(route.chatId)) },
                onNavigateToUserProfile = { userId ->
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId == myUid) {
                        navController.navigate(Route.Profile) { launchSingleTop = true }
                    } else {
                        navController.navigate(Route.UserProfile(userId))
                    }
                }
            )
        }

        composable<Route.ChatInfo> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatInfo>()
            ChatInfoScreen(
                chatId = route.chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId == myUid) {
                        navController.navigate(Route.Profile) { launchSingleTop = true }
                    } else {
                        navController.navigate(Route.UserProfile(userId))
                    }
                }
            )
        }

        composable<Route.StoryViewer> {
            StoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.Profile> {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToStory = { navController.navigate(Route.StoryViewer("my_id")) },
                // followers/following sayısı 0 ise FollowList'e gitme
                onNavigateToFollowers = { uid ->
                    navController.navigate(Route.FollowList(uid, "FOLLOWERS"))
                },
                onNavigateToFollowing = { uid ->
                    navController.navigate(Route.FollowList(uid, "FOLLOWING"))
                }
            )
        }

        composable<Route.UserProfile> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.UserProfile>()
            val myUid = FirebaseAuth.getInstance().currentUser?.uid
            if (route.userId == myUid) {
                navController.navigate(Route.Profile) {
                    popUpTo(Route.UserProfile(route.userId)) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                UserProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { userId -> navController.navigate(Route.ChatDetail(userId)) },
                    onNavigateToFollowers = { uid ->
                        navController.navigate(Route.FollowList(uid, "FOLLOWERS"))
                    },
                    onNavigateToFollowing = { uid ->
                        navController.navigate(Route.FollowList(uid, "FOLLOWING"))
                    }
                )
            }
        }

        composable<Route.FollowList> {
            FollowListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId == myUid) {
                        navController.navigate(Route.Profile) { launchSingleTop = true }
                    } else {
                        navController.navigate(Route.UserProfile(userId))
                    }
                }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAccountCenter = { navController.navigate(Route.AccountCenter) },
                onNavigateToPendingRequests = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@SettingsScreen
                    navController.navigate(Route.FollowList(uid, "PENDING_REQUESTS"))
                }
            )
        }

        composable<Route.AccountCenter> {
            AccountCenterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate(Route.AddAccountAuth) },
                onSwitchComplete = {
                    // Home'a git, tüm backstack'i temizle — ProfileViewModel yeni
                    // AccountCenter hesabını AuthStateListener sayesinde otomatik yükleyecek
                    navController.navigate(Route.Home) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }

    LaunchedEffect(initialChatId) {
        if (!initialChatId.isNullOrBlank()) {
            navController.navigate(Route.ChatDetail(initialChatId))
            onChatDeepLinkHandled()
        }
    }
}
