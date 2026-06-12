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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.core.util.FormatUtil.formatStat
import com.linker.app.domain.model.FollowState
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.model.followState
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserProfileEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message.asString(context))
                is UserProfileEffect.NavigateToChat -> {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (showFullScreenAvatar) Modifier.blur(16.dp) else Modifier,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    uiState.error != null -> {
                        val errorMessage = uiState.error?.asString()
                        if (errorMessage != null) {
                            ErrorState(errorMessage, onNavigateBack)
                        }
                    }
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
                                ) { Text(stringResource(R.string.user_profile_no_posts), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "@${uiState.user?.username ?: ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp
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
        UserProfileHeaderTopBar(user, onNavigateBack, onAvatarClick)
        Spacer(modifier = Modifier.height(8.dp))
        UserProfileInfo(user)
        Spacer(modifier = Modifier.height(14.dp))
        UserProfileStats(user, onFollowersClick, onFollowingClick)
        Spacer(modifier = Modifier.height(14.dp))
        UserProfileActions(user, isActionLoading, onFollowAction, onMessage)
        Spacer(modifier = Modifier.height(10.dp))
        UserProfileTabs(user, selectedTab, onTabSelected)
    }
}

@Composable
private fun UserProfileHeaderTopBar(
    user: User?,
    onNavigateBack: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(painterResource(R.drawable.ic_arrow_left_01_outline), stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
        }
        LinkerAvatar(
            imageUrl = user?.profileImageUrl,
            size = 240.dp,
            storyState = StoryState.NONE,
            onClick = onAvatarClick
        )
        IconButton(onClick = { /* more */ }) {
            Icon(painterResource(R.drawable.ic_more_square_outline), "More",
                tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun UserProfileInfo(user: User?) {
    Text(user?.displayName ?: "User", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("@${user?.username ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    if (!user?.bio.isNullOrBlank()) {
        Text(user?.bio ?: "", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UserProfileStats(
    user: User?,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onFollowersClick)
        ) {
            Text(formatStat(user?.metrics?.followersCount ?: 0), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.followers), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onFollowingClick)
        ) {
            Text(formatStat(user?.metrics?.followingCount ?: 0), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.following), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatStat(user?.metrics?.likesCount ?: 0), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.profile_likes), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        }
    }
}

@Composable
private fun UserProfileActions(
    user: User?,
    isActionLoading: Boolean,
    onFollowAction: () -> Unit,
    onMessage: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val followState = user?.followState() ?: FollowState.NOT_FOLLOWING
        val btnColor: Color
        val btnTextColor: Color
        val btnLabelId: Int

        when (followState) {
            FollowState.FOLLOWING -> {
                btnColor = MaterialTheme.colorScheme.surfaceVariant
                btnTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                btnLabelId = R.string.follow_status_following
            }
            FollowState.REQUEST_SENT -> {
                btnColor = Color(0xFF4A5568)
                btnTextColor = MaterialTheme.colorScheme.onBackground
                btnLabelId = R.string.user_profile_requested
            }
            FollowState.NOT_FOLLOWING -> {
                btnColor = MaterialTheme.colorScheme.primary
                btnTextColor = MaterialTheme.colorScheme.background
                btnLabelId = R.string.follow_status_follow
            }
            FollowState.NOT_FOLLOWING_PRIVATE -> {
                btnColor = MaterialTheme.colorScheme.primary
                btnTextColor = MaterialTheme.colorScheme.background
                btnLabelId = R.string.follow_status_follow
            }
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
                Text(stringResource(btnLabelId), fontSize = 16.sp, color = btnTextColor, fontWeight = FontWeight.SemiBold)
            }
        }

        Button(
            onClick = onMessage,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.weight(1f).height(50.dp).padding(start = 5.dp, end = 40.dp)
        ) {
            Text(stringResource(R.string.user_profile_message), fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UserProfileTabs(
    user: User?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    if (user?.privacy?.isPrivate == false || user?.relationship?.isFollowing == true) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val isFeed = selectedTab == 0
            IconButton(onClick = { onTabSelected(0) }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_gallery_outline), stringResource(R.string.user_profile_posts),
                        tint = if (isFeed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(2.dp).width(48.dp)
                        .background(if (isFeed) MaterialTheme.colorScheme.onBackground else Color.Transparent))
                }
            }
            val isRelinks = selectedTab == 1
            IconButton(onClick = { onTabSelected(1) }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_play_add_outline), stringResource(R.string.user_profile_relinks),
                        tint = if (isRelinks) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(2.dp).width(48.dp)
                        .background(if (isRelinks) MaterialTheme.colorScheme.onBackground else Color.Transparent))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
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
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_smart_lock_ai_outline), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Text(stringResource(R.string.user_profile_account_private), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.user_profile_follow_private_desc, user?.username ?: ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp
        )
    }
}

// ── Post item ─────────────────────────────────────────────────────────────────

@Composable
private fun UserPostItem(post: Link) {
    val aspectRatio = post.primaryMedia.aspectRatio ?: 1f
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
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
                Text(formatStat(post.engagement.likesCount), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back), color = MaterialTheme.colorScheme.primary) }
    }
}
