package com.linker.app.presentation.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.core.util.FormatUtil.formatStat
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStory: () -> Unit = {},
    onNavigateToFollowers: (String) -> Unit = {},
    onNavigateToFollowing: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.user?.userId) {
        selectedTab = 0
        showFullScreenAvatar = false
    }

    val currentLinks = uiState.relinkedPosts

    val handleAvatarClick = {
        if (uiState.storyState != StoryState.NONE) onNavigateToStory()
        else showFullScreenAvatar = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (showFullScreenAvatar) Modifier.blur(16.dp) else Modifier,
            containerColor = Black,
            bottomBar = {
                LinkerBottomNavigationBar(
                    currentRoute = "Profile",
                    onNavigate = onNavigateBottomNav,
                    modifier = Modifier.background(Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    ProfileHeader(
                        user = uiState.user,
                        storyState = uiState.storyState,
                        onNavigateBack = onNavigateBack,
                        onNavigateToSettings = onNavigateToSettings,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onAvatarClick = handleAvatarClick,
                        onAvatarLongClick = { showFullScreenAvatar = true },
                        onFollowersClick = {
                            val uid = viewModel.currentUid ?: return@ProfileHeader
                            onNavigateToFollowers(uid)
                        },
                        onFollowingClick = {
                            val uid = viewModel.currentUid ?: return@ProfileHeader
                            onNavigateToFollowing(uid)
                        }
                    )
                }

                if (selectedTab == 0) {
                    if (currentLinks.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.profile_no_links), color = TextSecondary)
                            }
                        }
                    } else {
                        items(currentLinks) { link -> ProfilePostItem(post = link) }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(100.dp)) }
                } else {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.profile_no_relink), color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Full Screen Avatar Overlay
        AnimatedVisibility(
            visible = showFullScreenAvatar,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showFullScreenAvatar = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinkerAvatar(
                        imageUrl = uiState.user?.profileImageUrl,
                        size = 300.dp, storyState = StoryState.NONE, onClick = {}
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(uiState.user?.displayName ?: "User", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("@${uiState.user?.username ?: "username"}", color = TextSecondary, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarAction(R.drawable.ic_export_circle_01_outline, stringResource(R.string.profile_action_share)) { showFullScreenAvatar = false }
                        AvatarAction(R.drawable.ic_close_circle_outline, stringResource(R.string.profile_action_block)) { showFullScreenAvatar = false }
                        AvatarAction(R.drawable.ic_enhance_user_ai_outline, stringResource(R.string.follow_status_follow)) { showFullScreenAvatar = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarAction(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(60.dp).background(DarkGray, CircleShape)) {
            Icon(painterResource(iconRes), label, tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ProfileHeader(
    user: User?,
    storyState: StoryState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onFollowersClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onNavigateBack) {
                Icon(painterResource(R.drawable.ic_arrow_left_01_outline), "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
            LinkerAvatar(imageUrl = user?.profileImageUrl, size = 240.dp, storyState = storyState, onClick = onAvatarClick, onLongClick = onAvatarLongClick)
            IconButton(onClick = onNavigateToSettings) {
                Icon(painterResource(R.drawable.ic_setting_2_outline), "Settings", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(user?.displayName ?: "User", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("@${user?.username ?: "username"}", color = TextSecondary, fontSize = 14.sp)
        if (!user?.bio.isNullOrBlank()) {
            Text(user?.bio ?: "", color = TextPrimary, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onFollowersClick)) {
                Text(formatStat(user?.metrics?.followersCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.followers), color = TextPrimary, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onFollowingClick)) {
                Text(formatStat(user?.metrics?.followingCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.following), color = TextPrimary, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatStat(user?.metrics?.likesCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.profile_likes), color = TextPrimary, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = TextSecondary),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f).height(50.dp).padding(end = 5.dp, start = 40.dp)) {
                Icon(painterResource(R.drawable.ic_user_edit_outline), null, tint = TextPrimary, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.profile_action_edit), fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB15879)),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f).height(50.dp).padding(start = 5.dp, end = 40.dp)) {
                Icon(painterResource(R.drawable.ic_export_circle_01_outline), null, tint = TextPrimary, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.profile_action_share), fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val isFeed = selectedTab == 0
            IconButton(onClick = { onTabSelected(0) }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_gallery_outline), stringResource(R.string.profile_tab_feed),
                        tint = if (isFeed) TextPrimary else TextSecondary, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(2.dp).width(48.dp).background(if (isFeed) TextPrimary else Color.Transparent))
                }
            }
            val isShorts = selectedTab == 1
            IconButton(onClick = { onTabSelected(1) }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_play_add_outline), stringResource(R.string.profile_tab_shorts),
                        tint = if (isShorts) TextPrimary else TextSecondary, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(2.dp).width(48.dp).background(if (isShorts) TextPrimary else Color.Transparent))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextPrimary, fontSize = 14.sp)
    }
}

@Composable
fun ProfilePostItem(post: Link) {
    val aspectRatio = post.primaryMedia.aspectRatio ?: 1f
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(10.dp)).background(DarkGray)) {
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 100f)))
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_profile_outline), null, modifier = Modifier.size(24.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_heart_bold), null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(formatStat(post.engagement.likesCount), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(post.description ?: "", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
