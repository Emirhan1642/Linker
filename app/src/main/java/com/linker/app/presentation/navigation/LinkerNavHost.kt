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
import com.linker.app.presentation.screens.note.TrackClipPickerScreen
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

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.linker.app.presentation.screens.main.MainShellScreen
import com.linker.app.presentation.screens.onboarding.OnboardingScreen

@Composable
fun LinkerNavHost(
    modifier: Modifier = Modifier,
    currentUserId: String?,
    initialChatId: String? = null,
    onChatDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        }
    ) {

        composable<Route.Splash>(
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(400)) }
        ) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Route.Onboarding) { popUpTo(Route.Splash) { inclusive = true } }
                },
                onNavigateToAuth = {
                    navController.navigate(Route.Auth) { popUpTo(Route.Splash) { inclusive = true } }
                },
                onNavigateToHome = {
                    navController.navigate(Route.Main) { popUpTo(Route.Splash) { inclusive = true } }
                }
            )
        }

        composable<Route.Onboarding>(
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(350)) }
        ) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Route.Auth) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable<Route.Auth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Main) { popUpTo(Route.Auth) { inclusive = true } }
                },
                isAddingAccount = false
            )
        }

        composable<Route.AddAccountAuth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Main) { popUpTo(Route.Main) { inclusive = true } }
                },
                onNavigateToAccountCenter = {
                    navController.navigate(Route.AccountCenter) { popUpTo(Route.AccountCenter) { inclusive = true } }
                },
                isAddingAccount = true
            )
        }

        composable<Route.Main>(
            enterTransition = { fadeIn(tween(350)) }
        ) {
            MainShellScreen(
                initialTab = 0,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) },
                onNavigateToUserProfile = { userId ->
                    if (userId != currentUserId) {
                        navController.navigate(Route.UserProfile(userId))
                    }
                },
                onNavigateToLinkDetail = { linkId -> navController.navigate(Route.LinkDetail(linkId)) },
                onNavigateToChatDetail = { chatId -> navController.navigate(Route.ChatDetail(chatId)) },
                onNavigateToNewChat = { navController.navigate(Route.NewChat) },
                onNavigateToNoteEditor = { navController.navigate(Route.NoteEditor) },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
                },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateToStory = { navController.navigate(Route.StoryGrid) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS)) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING)) },
                onNavigateToLinkEditor = { navController.navigate(Route.LinkEditor) },
                onNavigateToStoryEditor = { navController.navigate(Route.StoryEditor) }
            )
        }

        composable<Route.Home> {
            MainShellScreen(
                initialTab = 0,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) },
                onNavigateToUserProfile = { userId ->
                    if (userId != currentUserId) navController.navigate(Route.UserProfile(userId))
                },
                onNavigateToLinkDetail = { linkId -> navController.navigate(Route.LinkDetail(linkId)) },
                onNavigateToChatDetail = { chatId -> navController.navigate(Route.ChatDetail(chatId)) },
                onNavigateToNewChat = { navController.navigate(Route.NewChat) },
                onNavigateToNoteEditor = { navController.navigate(Route.NoteEditor) },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
                },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateToStory = { navController.navigate(Route.StoryGrid) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS)) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING)) },
                onNavigateToLinkEditor = { navController.navigate(Route.LinkEditor) },
                onNavigateToStoryEditor = { navController.navigate(Route.StoryEditor) }
            )
        }

        composable<Route.Search> {
            MainShellScreen(
                initialTab = 1,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) },
                onNavigateToUserProfile = { userId ->
                    if (userId != currentUserId) navController.navigate(Route.UserProfile(userId))
                },
                onNavigateToLinkDetail = { linkId -> navController.navigate(Route.LinkDetail(linkId)) },
                onNavigateToChatDetail = { chatId -> navController.navigate(Route.ChatDetail(chatId)) },
                onNavigateToNewChat = { navController.navigate(Route.NewChat) },
                onNavigateToNoteEditor = { navController.navigate(Route.NoteEditor) },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
                },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateToStory = { navController.navigate(Route.StoryGrid) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS)) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING)) },
                onNavigateToLinkEditor = { navController.navigate(Route.LinkEditor) },
                onNavigateToStoryEditor = { navController.navigate(Route.StoryEditor) }
            )
        }

        composable<Route.Create> { }

        composable<Route.Chat> {
            MainShellScreen(
                initialTab = 2,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) },
                onNavigateToUserProfile = { userId ->
                    if (userId != currentUserId) navController.navigate(Route.UserProfile(userId))
                },
                onNavigateToLinkDetail = { linkId -> navController.navigate(Route.LinkDetail(linkId)) },
                onNavigateToChatDetail = { chatId -> navController.navigate(Route.ChatDetail(chatId)) },
                onNavigateToNewChat = { navController.navigate(Route.NewChat) },
                onNavigateToNoteEditor = { navController.navigate(Route.NoteEditor) },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
                },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateToStory = { navController.navigate(Route.StoryGrid) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS)) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING)) },
                onNavigateToLinkEditor = { navController.navigate(Route.LinkEditor) },
                onNavigateToStoryEditor = { navController.navigate(Route.StoryEditor) }
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
                },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
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
                },
                onNavigateToStoryEditor = {
                    navController.navigate(Route.StoryEditor)
                }
            )
        }

        composable<Route.StoryViewer> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.StoryViewer>()
            StoryScreen(
                userId = route.userId,
                allUserStories = emptyList(),
                onNavigateBack = { navController.popBackStack() },
                onUserTap = { uid ->
                    if (uid != currentUserId) navController.navigate(Route.UserProfile(uid))
                }
            )
        }

        composable<Route.StoryEditor> {
            com.linker.app.presentation.screens.story.StoryEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onStoryPublished = { navController.popBackStack() }
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
                initialDescription = null,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable<Route.NoteEditor> {
            com.linker.app.presentation.screens.note.NoteEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSpotifySearch = { navController.navigate(Route.SpotifySearch) },
                onNavigateToLocationMap = { lat, lon, place ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, place))
                },
                navController = navController
            )
        }

        composable<Route.SpotifySearch> {
            com.linker.app.presentation.screens.note.SpotifySearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onTrackSelected = { track ->
                    // Navigate to clip picker instead of going directly to editor
                    navController.navigate(
                        Route.TrackClipPicker(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtUrl = track.albumArtUrl ?: "",
                            previewUrl = track.previewUrl ?: "",
                            durationMs = track.durationMs,
                            isExplicit = track.isExplicit
                        )
                    )
                },
                onArtistSelected = { artistId ->
                    navController.navigate(Route.ArtistProfile(artistId))
                },
                onAlbumSelected = { albumId ->
                    navController.navigate(Route.AlbumDetail(albumId))
                }
            )
        }

        composable<Route.ArtistProfile> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ArtistProfile>()
            com.linker.app.presentation.screens.note.ArtistProfileScreen(
                artistId = route.artistId,
                onNavigateBack = { navController.popBackStack() },
                onTrackSelected = { track ->
                    navController.navigate(
                        Route.TrackClipPicker(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtUrl = track.albumArtUrl ?: "",
                            previewUrl = track.previewUrl ?: "",
                            durationMs = track.durationMs,
                            isExplicit = track.isExplicit
                        )
                    )
                },
                onAlbumSelected = { albumId ->
                    navController.navigate(Route.AlbumDetail(albumId))
                }
            )
        }

        composable<Route.AlbumDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.AlbumDetail>()
            com.linker.app.presentation.screens.note.AlbumDetailScreen(
                albumId = route.albumId,
                onNavigateBack = { navController.popBackStack() },
                onTrackSelected = { track ->
                    navController.navigate(
                        Route.TrackClipPicker(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtUrl = track.albumArtUrl ?: "",
                            previewUrl = track.previewUrl ?: "",
                            durationMs = track.durationMs,
                            isExplicit = track.isExplicit
                        )
                    )
                }
            )
        }

        composable<Route.TrackClipPicker> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.TrackClipPicker>()
            TrackClipPickerScreen(
                trackId = route.trackId,
                trackName = route.trackName,
                artistName = route.artistName,
                albumArtUrl = route.albumArtUrl,
                previewUrl = route.previewUrl,
                trackDurationMs = route.durationMs,
                isExplicit = route.isExplicit,
                onNavigateBack = { navController.popBackStack() },
                onClipConfirmed = { startMs, endMs ->
                    // Write all track data to NoteEditor's saved state and pop back to it
                    navController.getBackStackEntry(Route.NoteEditor).savedStateHandle.apply {
                        set("selected_track_id", route.trackId)
                        set("selected_track_name", route.trackName)
                        set("selected_track_artist", route.artistName)
                        set("selected_track_art", route.albumArtUrl)
                        set("selected_track_preview", route.previewUrl.ifBlank { null })
                        set("selected_clip_start_ms", startMs)
                        set("selected_clip_end_ms", endMs)
                        set("selected_track_explicit", route.isExplicit)
                    }
                    navController.popBackStack(Route.NoteEditor, inclusive = false)
                }
            )
        }

        composable<Route.LocationPicker> {
            // Legacy location search screen — kept for future use.
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

        composable<Route.NoteLocationMap> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.NoteLocationMap>()
            com.linker.app.presentation.screens.note.NoteLocationMapScreen(
                latitude = route.latitude,
                longitude = route.longitude,
                placeName = route.placeName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Route.Profile> {
            MainShellScreen(
                initialTab = 3,
                onNavigateToStoryGrid = { navController.navigate(Route.StoryGrid) },
                onNavigateToUserProfile = { userId ->
                    if (userId != currentUserId) navController.navigate(Route.UserProfile(userId))
                },
                onNavigateToLinkDetail = { linkId -> navController.navigate(Route.LinkDetail(linkId)) },
                onNavigateToChatDetail = { chatId -> navController.navigate(Route.ChatDetail(chatId)) },
                onNavigateToNewChat = { navController.navigate(Route.NewChat) },
                onNavigateToNoteEditor = { navController.navigate(Route.NoteEditor) },
                onNavigateToNoteLocationMap = { lat, lon, placeName ->
                    navController.navigate(Route.NoteLocationMap(lat, lon, placeName))
                },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onNavigateToStory = { navController.navigate(Route.StoryGrid) },
                onNavigateToFollowers = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWERS)) },
                onNavigateToFollowing = { uid -> navController.navigate(Route.FollowList(uid, FollowListType.FOLLOWING)) },
                onNavigateToLinkEditor = { navController.navigate(Route.LinkEditor) },
                onNavigateToStoryEditor = { navController.navigate(Route.StoryEditor) }
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
                    navController.navigate(Route.Main) {
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
}
