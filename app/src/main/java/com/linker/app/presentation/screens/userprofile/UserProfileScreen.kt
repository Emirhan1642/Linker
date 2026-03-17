package com.linker.app.presentation.screens.userprofile

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.domain.model.FollowState
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.model.followState
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.screens.profile.formatStat
import com.linker.app.presentation.theme.*

@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToFollowers: (String) -> Unit = {},
    onNavigateToFollowing: (String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFullScreenAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserProfileEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is UserProfileEffect.NavigateToChat -> {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (showFullScreenAvatar) Modifier.blur(16.dp) else Modifier,
            containerColor = Black,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        color = AccentGreen,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    uiState.error != null -> ErrorState(uiState.error!!, onNavigateBack)
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            UserProfileHeader(
                                user = uiState.user,
                                isActionLoading = uiState.isActionLoading,
                                selectedTab = uiState.selectedTab,
                                onNavigateBack = onNavigateBack,
                                onTabSelected = viewModel::onTabSelected,
                                onFollowAction = viewModel::onFollowAction,
                                onMessage = { uiState.user?.let { onNavigateToChat(it.userId) } },
                                onFollowersClick = { uiState.user?.let { onNavigateToFollowers(it.userId) } },
                                onFollowingClick = { uiState.user?.let { onNavigateToFollowing(it.userId) } },
                                onAvatarClick = { showFullScreenAvatar = true }
                            )
                        }

                        if (uiState.isContentLocked) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                PrivateAccountLock(user = uiState.user)
                            }
                        } else if (uiState.posts.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("No posts yet", color = TextSecondary) }
                            }
                        } else {
                            items(uiState.posts) { link -> UserPostItem(post = link) }
                        }

                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(modifier = Modifier.height(80.dp))
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
                        size = 300.dp,
                        storyState = StoryState.NONE,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        uiState.user?.displayName ?: "User",
                        color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "@${uiState.user?.username ?: ""}",
                        color = TextSecondary, fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun UserProfileHeader(
    user: User?,
    isActionLoading: Boolean,
    selectedTab: Int,
    onNavigateBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onFollowAction: () -> Unit,
    onMessage: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(painterResource(R.drawable.ic_arrow_left_01_outline), "Back",
                    tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
            LinkerAvatar(
                imageUrl = user?.profileImageUrl,
                size = 240.dp,
                storyState = StoryState.NONE,
                onClick = onAvatarClick
            )
            IconButton(onClick = { /* more */ }) {
                Icon(painterResource(R.drawable.ic_more_square_outline), "More",
                    tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(user?.displayName ?: "User", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("@${user?.username ?: ""}", color = TextSecondary, fontSize = 14.sp)
        if (!user?.bio.isNullOrBlank()) {
            Text(user?.bio ?: "", color = TextPrimary, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Stats — tıklanabilir
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onFollowersClick)
            ) {
                Text(formatStat(user?.followersCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Followers", color = TextPrimary, fontSize = 14.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onFollowingClick)
            ) {
                Text(formatStat(user?.followingCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Following", color = TextPrimary, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatStat(user?.likesCount ?: 0), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Likes", color = TextPrimary, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Follow + Message buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val followState = user?.followState() ?: FollowState.NOT_FOLLOWING
            val (btnColor, btnTextColor, btnLabel) = when (followState) {
                FollowState.FOLLOWING             -> Triple(TextSecondary, TextPrimary, "Following")
                FollowState.REQUEST_SENT          -> Triple(Color(0xFF4A5568), TextPrimary, "Requested")
                FollowState.NOT_FOLLOWING         -> Triple(AccentGreen, Black, "Follow")
                FollowState.NOT_FOLLOWING_PRIVATE -> Triple(AccentGreen, Black, "Request")
            }

            Button(
                onClick = onFollowAction,
                enabled = !isActionLoading,
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f).height(50.dp).padding(end = 5.dp, start = 40.dp)
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(color = btnTextColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(btnLabel, fontSize = 16.sp, color = btnTextColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onMessage,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f).height(50.dp).padding(start = 5.dp, end = 40.dp)
            ) {
                Text("Message", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs — private ve takip etmiyorsa gösterme
        if (user?.isPrivate == false || user?.isFollowing == true) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val isFeed = selectedTab == 0
                IconButton(onClick = { onTabSelected(0) }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_gallery_outline), "Posts",
                            tint = if (isFeed) TextPrimary else TextSecondary, modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.height(2.dp).width(48.dp)
                            .background(if (isFeed) TextPrimary else Color.Transparent))
                    }
                }
                val isRelinks = selectedTab == 1
                IconButton(onClick = { onTabSelected(1) }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_play_add_outline), "Relinks",
                            tint = if (isRelinks) TextPrimary else TextSecondary, modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.height(2.dp).width(48.dp)
                            .background(if (isRelinks) TextPrimary else Color.Transparent))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ── Private lock ──────────────────────────────────────────────────────────────

@Composable
private fun PrivateAccountLock(user: User?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_smart_lock_ai_outline), null,
                tint = TextSecondary, modifier = Modifier.size(36.dp))
        }
        Text("This account is private", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Follow @${user?.username ?: ""} to see their\nLinks and Relinks",
            color = TextSecondary, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp
        )
    }
}

// ── Post item ─────────────────────────────────────────────────────────────────

@Composable
private fun UserPostItem(post: Link) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(post.aspectRatio ?: 1f)
            .clip(RoundedCornerShape(10.dp)).background(DarkGray)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 100f)
        ))
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Gray),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_profile_outline), null, modifier = Modifier.size(24.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_heart_bold), null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(formatStat(post.likesCount), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(post.description ?: "", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(error: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error, color = ErrorRed)
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Go back", color = AccentGreen) }
    }
}
