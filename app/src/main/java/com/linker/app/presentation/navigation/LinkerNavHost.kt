package com.linker.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.screens.accountcenter.AccountCenterScreen
import com.linker.app.presentation.screens.followlist.FollowListType
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
import com.linker.app.presentation.screens.settings.OfflineMessagingSettingsScreen
import com.linker.app.presentation.screens.splash.SplashScreen
import com.linker.app.presentation.screens.story.StoryGridScreen
import com.linker.app.presentation.screens.story.StoryScreen
import com.linker.app.presentation.screens.userprofile.UserProfileScreen
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@Composable
fun LinkerNavHost(
    modifier: Modifier = Modifier,
    currentUserId: String?,
    initialChatId: String? = null,
    onChatDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    var showContentPicker by remember { mutableStateOf(false) }

    val onNavigateBottomNav: (BottomNavItem) -> Unit = { item ->
        if (item == BottomNavItem.Add) {
            showContentPicker = true
        } else {
            val route = when (item) {
                BottomNavItem.Explore -> Route.Home
                BottomNavItem.Search  -> Route.Search
                BottomNavItem.Chat    -> Route.Chat
                BottomNavItem.Profile -> Route.Profile
                else -> Route.Home
            }
            navController.navigate(route) {
                popUpTo(Route.Home) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
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
            HomeScreen(
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) }
            )
        }

        composable<Route.Search> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToUserProfile = { userId ->
                    if (userId == currentUserId) {
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
                    if (userId == currentUserId) {
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
                    if (userId == currentUserId) {
                        navController.navigate(Route.Profile) { launchSingleTop = true }
                    } else {
                        navController.navigate(Route.UserProfile(userId))
                    }
                }
            )
        }

        composable<Route.StoryGrid> {
            StoryGridScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenStoryViewer = { userId ->
                    navController.navigate(Route.StoryViewer(userId))
                }
            )
        }

        composable<Route.StoryViewer> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.StoryViewer>()
            // Note: allUserStories would be passed from a shared ViewModel in a real impl.
            // For now we pass empty list; StoryGridViewModel is the source of truth.
            StoryScreen(
                userId = route.userId,
                allUserStories = emptyList(),
                onNavigateBack = { navController.popBackStack() },
                onUserTap = { uid ->
                    if (uid != currentUserId) navController.navigate(Route.UserProfile(uid))
                }
            )
        }

        composable<Route.LinkDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.LinkDetail>()
            com.linker.app.presentation.screens.link.LinkDetailScreen(
                linkId = route.linkId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Route.LinkEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.LinkEditor>()
            com.linker.app.presentation.screens.link.LinkEditorScreen(
                linkId = null,
                initialDescription = null, // Can be passed via safe args if needed
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable<Route.NoteEditor> {
            com.linker.app.presentation.screens.note.NoteEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSpotifySearch = { navController.navigate(Route.SpotifySearch) },
                onNavigateToLocationPicker = { navController.navigate(Route.LocationPicker) },
                navController = navController
            )
        }

        composable<Route.SpotifySearch> {
            com.linker.app.presentation.screens.note.SpotifySearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onTrackSelected = { track ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_track_id", track.id)
                    navController.popBackStack()
                }
            )
        }

        composable<Route.LocationPicker> {
            com.linker.app.presentation.screens.note.LocationPickerScreen(
                onNavigateBack = { navController.popBackStack() },
                onLocationSelected = { location ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_location_lat", location.lat)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_location_lon", location.lon)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_location_name", location.name)
                    navController.popBackStack()
                }
            )
        }

        composable<Route.Profile> {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateBottomNav = onNavigateBottomNav,
                onNavigateToStory = { navController.navigate(Route.StoryViewer("my_id")) },
                // followers/following sayısı 0 ise FollowList'e gitme
                onNavigateToFollowers = { uid ->
                    navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS))
                },
                onNavigateToFollowing = { uid ->
                    navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING))
                }
            )
        }

        composable<Route.UserProfile> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.UserProfile>()
            if (route.userId == currentUserId) {
                navController.navigate(Route.Profile) {
                    popUpTo(Route.UserProfile(route.userId)) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                UserProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { userId -> navController.navigate(Route.ChatDetail(userId)) },
                    onNavigateToFollowers = { uid ->
                        navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS))
                    },
                    onNavigateToFollowing = { uid ->
                        navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING))
                    }
                )
            }
        }

        composable<Route.FollowList> {
            FollowListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    if (userId == currentUserId) {
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
                    val uid = currentUserId ?: return@SettingsScreen
                    navController.navigate(Route.FollowList(uid, FollowListType.PENDING_REQUESTS))
                },
                onNavigateToOfflineMessaging = { navController.navigate(Route.OfflineMessagingSettings) }
            )
        }

        composable<Route.OfflineMessagingSettings> {
            OfflineMessagingSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
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
                        popUpTo(navController.graph.id) { inclusive = true }
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

    if (showContentPicker) {
        ContentPickerBottomSheet(
            onDismiss = { showContentPicker = false },
            onLinkSelected = {
                showContentPicker = false
                navController.navigate(Route.LinkEditor)
            },
            onNoteSelected = {
                showContentPicker = false
                navController.navigate(Route.NoteEditor)
            },
            onStorySelected = {
                showContentPicker = false
                // Handle story camera launch
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentPickerBottomSheet(
    onDismiss: () -> Unit,
    onLinkSelected: () -> Unit,
    onNoteSelected: () -> Unit,
    onStorySelected: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ne Paylaşmak İstersin?", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContentPickerOption(icon = Icons.Default.Link, title = "Link", onClick = onLinkSelected)
                ContentPickerOption(icon = Icons.Default.NoteAdd, title = "Not", onClick = onNoteSelected)
                ContentPickerOption(icon = Icons.Default.PhotoCamera, title = "Hikaye", onClick = onStorySelected)
            }
        }
    }
}

@Composable
private fun ContentPickerOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, color = TextPrimary, fontSize = 14.sp)
    }
}
