package com.linker.app.presentation.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.linker.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.domain.model.User
import com.linker.app.domain.model.Link
import com.linker.app.presentation.components.StoryState

import androidx.compose.ui.draw.blur
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStory: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }
    
    // Mock data for staggered grid - until we implement the actual view model pagination 
    val currentLinks = uiState.relinkedPosts // this will be empty initially

    val handleAvatarClick = {
        if (uiState.storyState != StoryState.NONE) {
            onNavigateToStory()
        } else {
            showFullScreenAvatar = true
        }
    }

    val handleAvatarLongClick = {
        if (uiState.storyState != StoryState.NONE) {
            showFullScreenAvatar = true
        } else {
            showFullScreenAvatar = true
        }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                // Profile Header
                item(span = StaggeredGridItemSpan.FullLine) {
                    ProfileHeader(
                        user = uiState.user,
                        storyState = uiState.storyState,
                        onNavigateBack = onNavigateBack,
                        onNavigateToSettings = onNavigateToSettings,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onAvatarClick = handleAvatarClick,
                        onAvatarLongClick = handleAvatarLongClick
                    )
                }

                // Grid Items - fallback to mock if no relinked posts are actually found for now
                if (selectedTab == 0) {
                    if (currentLinks.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp), contentAlignment = Alignment.Center) {
                                Text("No Links yet", color = TextSecondary)
                            }
                        }
                    } else {
                        items(currentLinks) { link ->
                            ProfilePostItem(post = link)
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom nav
                    }
                } else {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No Relink yet", color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Full Screen Avatar Overlay
        AnimatedVisibility(
            visible = showFullScreenAvatar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showFullScreenAvatar = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Full size avatar representation
                    LinkerAvatar(
                        imageUrl = uiState.user?.profileImageUrl,
                        size = 300.dp,
                        storyState = StoryState.NONE, // No story ring in full screen preview
                        onClick = { /* Do nothing, let background consume or close? Let's keep empty */ }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        uiState.user?.displayName ?: "User",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "@" + (uiState.user?.username ?: "username"),
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Share, Block, Follow actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { showFullScreenAvatar = false },
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(DarkGray, CircleShape)
                            ) {
                                Icon(painterResource(id = R.drawable.ic_export_circle_01_outline), contentDescription = "Share", tint = Color.White)
                            }
                            Text(
                                "Share",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Block
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { showFullScreenAvatar = false },
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(DarkGray, CircleShape)
                            ) {
                                Icon(painterResource(id = R.drawable.ic_close_circle_outline), contentDescription = "Block", tint = Color.White)
                            }
                            Text(
                                "Block",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Follow
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { showFullScreenAvatar = false },
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(DarkGray, CircleShape)
                            ) {
                                Icon(painterResource(id = R.drawable.ic_enhance_user_ai_outline), contentDescription = "Follow", tint = Color.White)
                            }
                            Text(
                                "Follow",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
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
    onAvatarLongClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }

            // Avatar
            LinkerAvatar(
                imageUrl = user?.profileImageUrl,
                size = 240.dp,
                storyState = storyState,
                onClick = onAvatarClick,
                onLongClick = onAvatarLongClick
            )

            IconButton(onClick = onNavigateToSettings) {
                Icon(painterResource(id = R.drawable.ic_setting_2_outline), contentDescription = "Settings", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }

    Spacer(modifier = Modifier.height(8.dp))

    // Names
    Text(user?.displayName ?: "User", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("@${user?.username ?: "username"}", color = TextSecondary, fontSize = 14.sp)
    Text(user?.bio ?: "Helloooo!! I am a Linker user.", color = TextPrimary, fontSize = 14.sp) // bio could be empty

    Spacer(modifier = Modifier.height(14.dp))

    // Stats
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(formatStat(user?.followersCount ?: 0), "Followers")
        StatItem(formatStat(user?.followingCount ?: 0), "Following")
        StatItem(formatStat(user?.likesCount ?: 0), "Likes")
    }

    Spacer(modifier = Modifier.height(10.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Edit Button
            Button(
                onClick = { /* TODO */ },
                colors = ButtonDefaults.buttonColors(containerColor = TextSecondary),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .padding(end = 5.dp, start = 40.dp)
            ) {
                Icon(painterResource(id = R.drawable.ic_user_edit_outline), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Edit", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            // Share Button
            Button(
                onClick = { /* TODO */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB15879)), // Custom pinkish red
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .padding(start = 5.dp, end = 40.dp)
            ) {
                Icon(painterResource(id = R.drawable.ic_export_circle_01_outline), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Share", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val isFeed = selectedTab == 0
            IconButton(
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_gallery_outline),
                        contentDescription = "Feed",
                        tint = if (isFeed) TextPrimary else TextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(48.dp)
                            .background(if (isFeed) TextPrimary else Color.Transparent)
                    )
                }
            }

            val isShorts = selectedTab == 1
            IconButton(
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_add_outline),
                        contentDescription = "Shorts",
                        tint = if (isShorts) TextPrimary else TextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(48.dp)
                            .background(if (isShorts) TextPrimary else Color.Transparent)
                    )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(post.aspectRatio ?: 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkGray) // Placeholder for image
    ) {
        // Since we don't have actual images, we'll draw a gradient placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 100f
                    )
                )
        )
        
        // Top Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                // Should show author's profile image optionally
                Icon(painterResource(id = R.drawable.ic_profile_outline), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            
            // Likes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(id = R.drawable.ic_heart_bold), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(formatStat(post.likesCount), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Bottom text overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                post.description ?: "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatStat(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
