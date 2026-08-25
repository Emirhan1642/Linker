package com.linker.app.presentation.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.screens.chat.ChatListScreen
import com.linker.app.presentation.screens.home.HomeScreen
import com.linker.app.presentation.screens.profile.ProfileScreen
import com.linker.app.presentation.screens.search.SearchScreen
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MainShellScreen(
    initialTab: Int = 0,
    onNavigateToStoryGrid: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToLinkDetail: (String) -> Unit,
    onNavigateToChatDetail: (String) -> Unit,
    onNavigateToNewChat: () -> Unit,
    onNavigateToNoteEditor: () -> Unit,
    onNavigateToNoteLocationMap: (Double, Double, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStory: () -> Unit,
    onNavigateToFollowers: (String) -> Unit,
    onNavigateToFollowing: (String) -> Unit,
    onNavigateToLinkEditor: () -> Unit,
    onNavigateToStoryEditor: () -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 3),
        pageCount = { 4 }
    )
    val scope = rememberCoroutineScope()
    var showContentPicker by remember { mutableStateOf(false) }

    val currentRouteName = when (pagerState.currentPage) {
        0 -> BottomNavItem.Explore.name
        1 -> BottomNavItem.Search.name
        2 -> BottomNavItem.Chat.name
        3 -> BottomNavItem.Profile.name
        else -> BottomNavItem.Explore.name
    }

    val onNavigateBottomNav: (BottomNavItem) -> Unit = { item ->
        when (item) {
            BottomNavItem.Explore -> scope.launch { pagerState.animateScrollToPage(0) }
            BottomNavItem.Search  -> scope.launch { pagerState.animateScrollToPage(1) }
            BottomNavItem.Add     -> showContentPicker = true
            BottomNavItem.Chat    -> scope.launch { pagerState.animateScrollToPage(2) }
            BottomNavItem.Profile -> scope.launch { pagerState.animateScrollToPage(3) }
        }
    }

    val searchViewModel: com.linker.app.presentation.screens.search.SearchViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    Scaffold(
        containerColor = Black,
        bottomBar = {
            LinkerBottomNavigationBar(
                currentRoute = currentRouteName,
                onNavigate = onNavigateBottomNav,
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        onNavigateBottomNav = onNavigateBottomNav,
                        onNavigateToStoryGrid = onNavigateToStoryGrid,
                        onNavigateToLinkDetail = onNavigateToLinkDetail,
                        onNavigateToProfile = onNavigateToUserProfile,
                        onNavigateToSearch = { query, tab ->
                            searchViewModel.searchWithQueryAndTab(query, tab)
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        showBottomBar = false
                    )
                    1 -> SearchScreen(
                        onNavigateBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onNavigateBottomNav = onNavigateBottomNav,
                        onNavigateToUserProfile = onNavigateToUserProfile,
                        onNavigateToLinkDetail = onNavigateToLinkDetail,
                        showBottomBar = false,
                        viewModel = searchViewModel
                    )
                    2 -> ChatListScreen(
                        onNavigateToChatDetail = onNavigateToChatDetail,
                        onNavigateBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onNavigateBottomNav = onNavigateBottomNav,
                        onNavigateToNewChat = onNavigateToNewChat,
                        onNavigateToNoteEditor = onNavigateToNoteEditor,
                        onNavigateToNoteLocationMap = onNavigateToNoteLocationMap,
                        showBottomBar = false
                    )
                    3 -> ProfileScreen(
                        onNavigateBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateBottomNav = onNavigateBottomNav,
                        onNavigateToStory = onNavigateToStory,
                        onNavigateToFollowers = onNavigateToFollowers,
                        onNavigateToFollowing = onNavigateToFollowing,
                        onNavigateToCreateLink = onNavigateToLinkEditor,
                        showBottomBar = false
                    )
                }
            }
        }
    }

    if (showContentPicker) {
        ContentPickerBottomSheet(
            onDismiss = { showContentPicker = false },
            onLinkSelected = {
                showContentPicker = false
                onNavigateToLinkEditor()
            },
            onStorySelected = {
                showContentPicker = false
                onNavigateToStoryEditor()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentPickerBottomSheet(
    onDismiss: () -> Unit,
    onLinkSelected: () -> Unit,
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
            Text(
                text = stringResource(R.string.content_picker_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContentPickerOption(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.content_picker_link),
                    onClick = onLinkSelected
                )
                ContentPickerOption(
                    icon = Icons.Default.PhotoCamera,
                    title = stringResource(R.string.content_picker_story),
                    onClick = onStorySelected
                )
            }
        }
    }
}

@Composable
private fun ContentPickerOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bouncyClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, color = TextPrimary, fontSize = 14.sp)
    }
}
